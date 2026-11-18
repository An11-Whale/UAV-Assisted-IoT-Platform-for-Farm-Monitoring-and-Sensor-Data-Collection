# SCRUM-116 — F90 Implement Alert Creation and Management (F08)

## 1. Thông tin task

| Thuộc tính | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board trong ảnh | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Yêu cầu liên quan | FR F08, F09, F10, NFR04 |

## 2. Mục tiêu

Tạo domain và API quản lý alert có type, severity, source/value/threshold, thời gian, trạng thái và audit history; không tạo nhiều alert active cho cùng một điều kiện; dữ liệu phải còn sau restart.

## 3. Acceptance checklist

- [x] Alert có ID, type, severity, node, metric, observed, thresholds, message, time và status.
- [x] Dedupe theo fingerprint `type|nodeId|metric` khi alert còn active.
- [x] Có các trạng thái `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `CLOSED`.
- [x] Mỗi transition lưu actor, note, action và timestamp.
- [x] Event store append-only đọc lại được sau restart.
- [x] Có API list, detail và status transition.
- [x] Mutation yêu cầu admin key; sai key trả 401.
- [x] Alert tự động có thể resolve nhưng vẫn giữ lịch sử.

## 4. Đã thực hiện

| File/module | Nội dung |
|---|---|
| `src/farm-sim/src/farm/alert/Alert.java` | Alert entity và history events. |
| `src/farm-sim/src/farm/alert/AlertRepository.java` | Append-only event store, persistence, active dedupe và transition. |
| `src/farm-sim/src/farm/alert/AlertEngine.java` | Tạo/resolve alert từ detector và gọi email. |
| `src/farm-sim/src/farm/server/AlertApi.java` | `GET /api/v1/alerts`, detail và `PATCH /{id}/status`. |
| `src/farm-sim/src/farm/server/MockIngestionServer.java` | Nối ingestion → dashboard → alert engine/API. |
| `src/farm-sim/src/farm/tests/TestRunner.java` | Test dedupe, lifecycle và reload. |

## 5. Quy trình kiểm tra

Khởi động:

```powershell
$env:FARM_ADMIN_KEY = 'generate-a-test-key'
cd src\farm-sim
.\build.ps1
.\run.ps1 --missions 1 --tick-ms 0 --keep --reset-queue
```

List và đổi trạng thái:

```powershell
$base = 'http://localhost:8080/api/v1'
$alerts = Invoke-RestMethod "$base/alerts?limit=100"
$headers = @{ 'X-Admin-Key'=$env:FARM_ADMIN_KEY; 'X-Actor'='gateway-operator' }
$id = $alerts[0].id
$body = '{"status":"ACKNOWLEDGED","note":"Đã kiểm tra hiện trường"}'
Invoke-RestMethod -Method Patch -Uri "$base/alerts/$id/status" `
  -Headers $headers -ContentType application/json -Body $body
```

Restart server rồi GET detail để kiểm tra alert và history vẫn còn.

## 6. Kết quả đã xác minh

- Repository dedupe/lifecycle/reload test: PASS.
- REST alert list: PASS.
- PATCH status với đúng key: PASS; sai key bị từ chối.
- E2E ingestion tạo alert persisted; API trả danh sách 15 alert trong smoke test có dữ liệu tích lũy.
- Tổng TestRunner: **7 PASS, 0 FAIL**.

## 7. Đóng góp cho nhóm

- Cung cấp nguồn alert chuẩn cho NL làm F91 handling/closing và F92 web notification.
- Cho H contract rõ ràng để đưa vào backend/database thật.
- Cho QA audit được quá trình create–acknowledge–resolve/close.

## 8. Phụ thuộc và handoff

- F87–F89 tạo điều kiện alert.
- F93 nhận sự kiện alert mới để enqueue email.
- NL cần dùng API status/history và xác nhận UI/authorization cho F91/F92.
- H cần ánh xạ event store mock sang entity/transaction PostgreSQL trong backend thật.

## 9. Giới hạn trung thực

- Mock dùng JSONL event store; production cần database, pagination và transaction phù hợp.
- Chưa có assignee, SLA hay comment thread riêng; note audit trong transition đã sẵn sàng cho prototype.
- API key local chưa thay thế RBAC/JWT production.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file task vào SCRUM-116.
- [ ] Đính kèm entity, repository, API và mẫu JSON response.
- [ ] Dán test dedupe/lifecycle/reload và API auth/status PASS.
- [ ] Gửi NL review state transition/history.
- [ ] Gửi H review persistence/API mapping.
- [ ] Chuyển Done sau khi hai phía xác nhận.

## 11. Comment Jira để copy

```text
Implemented SCRUM-116 / F90 Alert Creation and Management.
- Persistent append-only alert event store with active-condition deduplication and restart reload.
- Alert fields include type, severity, node/metric, observed value, thresholds, time, status and audit history.
- Supports OPEN, ACKNOWLEDGED, RESOLVED and CLOSED; transitions record actor/note/time.
- REST list/detail/status API; mutations require X-Admin-Key.
Verification: repository dedupe/lifecycle/reload PASS; list and authorized status PATCH PASS; bad key rejected; full TestRunner 7 PASS/0 FAIL.
Handoff: NL consumes lifecycle/history for F91/F92; H maps mock persistence to the production backend.
Proposed status: Ready for Review.
```
