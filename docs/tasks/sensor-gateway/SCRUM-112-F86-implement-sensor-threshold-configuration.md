# SCRUM-112 — F86 Implement Sensor Threshold Configuration (F09)

## 1. Thông tin task

| Thuộc tính | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board trong ảnh | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Yêu cầu liên quan | FR F09, NFR03, NFR07 |

## 2. Mục tiêu

Cho phép quản trị viên thay đổi ngưỡng min/max theo loại cảm biến và tùy chọn ghi đè theo từng node mà không sửa mã nguồn. Cấu hình timeout và pin yếu cũng phải lưu bền vững, validate và có audit trail.

## 3. Acceptance checklist

- [x] Có rule mặc định cho sáu metric được hỗ trợ.
- [x] Hỗ trợ global rule và per-node override.
- [x] Validate metric, `min < max`, node ID, timeout và phần trăm pin.
- [x] Lưu policy ngoài source code và đọc lại sau restart.
- [x] Ghi audit gồm thời gian, actor và patch.
- [x] Có API GET và PATCH/PUT.
- [x] Mutation yêu cầu `X-Admin-Key`; sai key trả 401.
- [x] Khi chưa cấu hình `FARM_ADMIN_KEY`, API ghi bị khóa an toàn.

## 4. Đã thực hiện

| File/module | Nội dung |
|---|---|
| `src/farm-sim/src/farm/alert/AlertPolicy.java` | Rule global/per-node, validation, persistence và audit. |
| `src/farm-sim/src/farm/server/AlertApi.java` | `GET/PATCH/PUT /api/v1/config/alert-rules`, kiểm tra admin key và actor. |
| `src/farm-sim/src/farm/server/DashboardApi.java` | Đọc cùng AlertPolicy khi tính status thay vì hardcode. |
| `src/farm-sim/src/farm/tests/TestRunner.java` | Test validation, persistence và node override. |
| `.env.example` | Mẫu `FARM_ADMIN_KEY`, không chứa secret thật. |

## 5. Quy trình kiểm tra

Khởi động server:

```powershell
$env:FARM_ADMIN_KEY = 'generate-a-test-key'
cd src\farm-sim
.\build.ps1
.\run.ps1 --missions 1 --tick-ms 0 --keep
```

Đọc và cập nhật policy:

```powershell
$base = 'http://localhost:8080/api/v1'
Invoke-RestMethod "$base/config/alert-rules"
$headers = @{ 'X-Admin-Key'=$env:FARM_ADMIN_KEY; 'X-Actor'='gateway-operator' }
$body = '{"metric":"temperature","min":18,"max":32,"nodeId":"NODE-001"}'
Invoke-RestMethod -Method Patch -Uri "$base/config/alert-rules" `
  -Headers $headers -ContentType application/json -Body $body
```

Sau đó restart server và GET lại để xác nhận persistence.

## 6. Kết quả đã xác minh

- Test policy validation/persistence/node override: PASS.
- REST API: GET PASS; thiếu/sai key trả 401; đúng key PATCH PASS.
- Audit file ghi actor/patch; policy mới được dashboard và AlertEngine sử dụng.
- Tổng acceptance runner: **7 PASS, 0 FAIL**.

## 7. Đóng góp cho nhóm

- Farm Administrator có thể điều chỉnh điều kiện cảnh báo theo thực tế từng node.
- F87–F89 dùng một nguồn policy thống nhất.
- NL có API để xây giao diện cấu hình; H có contract để tích hợp auth/RBAC thật.

## 8. Phụ thuộc và handoff

- H cần thay local `X-Admin-Key` bằng authentication/RBAC backend production.
- NL có thể gọi GET/PATCH API khi làm UI F09.
- F87, F88, F89 phụ thuộc trực tiếp vào policy này.

## 9. Giới hạn trung thực

- Mock API dùng một admin key cho môi trường tích hợp, chưa phải user/role JWT production.
- Chưa có UI cấu hình; task này cung cấp model, persistence và API.
- API hiện hỗ trợ patch/create override, chưa có endpoint riêng để xóa một override.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file task vào SCRUM-112.
- [ ] Đính kèm `AlertPolicy.java`, `AlertApi.java` và `.env.example`.
- [ ] Dán kết quả GET/401/PATCH và test persistence.
- [ ] Nhờ H review security contract, NL review payload UI.
- [ ] Chuyển Done sau review API và quyền truy cập.

## 11. Comment Jira để copy

```text
Implemented SCRUM-112 / F86 Sensor Threshold Configuration.
- Persistent global and per-node metric rules, configurable stale timeout and low-battery percentage.
- Validation, audit trail and shared policy used by DashboardApi/AlertEngine.
- GET/PATCH/PUT /api/v1/config/alert-rules; mutations require X-Admin-Key and record X-Actor.
Verification: policy validation/persistence/override PASS; REST GET PASS, bad key 401, authorized PATCH PASS; full TestRunner 7 PASS/0 FAIL.
Handoff: H reviews production RBAC; NL may consume the API for configuration UI.
Proposed status: Ready for Review.
```
