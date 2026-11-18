# SCRUM-76 — F50 Implement Offline Data Queue

## 1. Thông tin task

| Thuộc tính | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board trong ảnh | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Yêu cầu liên quan | FR F20, NFR04, NFR05 |

## 2. Mục tiêu

Xây dựng hàng đợi store-and-forward: record được xếp FIFO khi backend offline, gửi theo batch khi có thể kết nối, và chỉ rời queue sau khi response được kiểm tra. Record backend từ chối phải được giữ ở dead-letter thay vì mất âm thầm.

## 3. Acceptance checklist

- [x] Queue FIFO bền vững qua restart.
- [x] Lấy batch tối đa 20 record mà chưa xóa pending.
- [x] HTTP lỗi, timeout hoặc response sai giữ nguyên record.
- [x] Response 2xx được đối chiếu `batchId`, `accepted`, `duplicates`, `rejected`.
- [x] Record bị reject được ghi vào dead-letter kèm raw record và lý do.
- [x] Dead-letter phải ghi thành công trước khi pending được xóa.
- [x] Backend gửi lại record không tạo dữ liệu trùng nhờ khóa `(nodeId, seq)`.

## 4. Đã thực hiện

| File/module | Nội dung |
|---|---|
| `src/farm-sim/src/farm/gateway/LocalStore.java` | FIFO pending queue và dead-letter store. |
| `src/farm-sim/src/farm/gateway/Uploader.java` | Tạo batch, gửi HTTP, parse response, commit queue hoặc giữ pending. |
| `src/farm-sim/config/farm-sim.properties` | Queue/DLQ path, capacity, API URL và timeout. |
| `src/farm-sim/src/farm/server/MockIngestionServer.java` | Response `accepted/duplicates/rejected`, chống trùng theo node+seq. |

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
.\run.ps1 --missions 2 --tick-ms 0 --offline-first --reset-queue
```

Kiểm tra phần tổng kết:

```text
Da dong bo server    : 12
Con trong hang doi   : 0
Ket noi cuoi          : ONLINE
```

Kiểm tra file runtime:

```powershell
Get-Content .\data\gateway-buffer.jsonl
Get-Content .\data\gateway-dead-letter.jsonl -ErrorAction SilentlyContinue
```

## 6. Kết quả đã xác minh

- E2E offline-first: **12 record sync, queue 0, ONLINE**.
- Case backend reject: kết quả `PARTIAL`, pending queue về 0 và dead-letter giữ đúng raw record cùng hai lý do lỗi.
- Response sai hoặc dead-letter không ghi được: pending được giữ để retry/deduplicate.

## 7. Đóng góp cho nhóm

- Bảo vệ dữ liệu trước khi chuyển cho ingestion API của H.
- Cho phép UAV tiếp tục thu thập tại vùng Internet không ổn định.
- Cung cấp bằng chứng rõ ràng cho NFR04/NFR05 và kịch bản QA mất mạng.

## 8. Phụ thuộc và handoff

- H phải trả đúng `batchId`, `accepted`, `duplicates`, `rejected[index,errors]`.
- H phải deduplicate `(nodeId, seq)` vì gateway có thể gửi lại batch sau response không rõ kết quả.
- F51/F52 cung cấp trạng thái mạng và lịch retry.

## 9. Giới hạn trung thực

- Batch size hiện cố định 20 trong prototype.
- Dead-letter cần quy trình vận hành riêng để sửa dữ liệu và gửi lại trong production.
- Demo dùng mock ingestion; backend Spring Boot của H phải giữ cùng contract.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file task này vào SCRUM-76.
- [ ] Đính kèm link `Uploader.java` và `LocalStore.java`.
- [ ] Dán log E2E 12 record/queue 0/ONLINE.
- [ ] Gửi response contract cho H xác nhận.
- [ ] Chỉ chuyển Done sau review contract và dead-letter behavior.

## 11. Comment Jira để copy

```text
Implemented SCRUM-76 / F50 Offline Data Queue.
- Durable FIFO pending queue, batch peek/ack, no deletion before a validated response.
- 2xx rejected records are written to dead-letter with raw payload and errors before pending removal.
- Network/non-2xx/invalid response keeps pending data for safe retry and backend deduplication.
Verification: offline-first E2E synced 12 records, queue=0, state=ONLINE; reject scenario stored the rejected record in DLQ.
Dependency: H must keep the documented batch response and (nodeId,seq) dedup contract.
Proposed status: Ready for Review.
```
