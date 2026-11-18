# SCRUM-74 — F48 Implement Local Data Storage (F20)

## 1. Thông tin task

| Thuộc tính | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board trong ảnh | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Yêu cầu liên quan | FR F20, NFR04, NFR05 |

## 2. Mục tiêu

Lưu bền vững dữ liệu mà UAV Gateway đã thu thập, để dữ liệu vẫn còn khi API mất kết nối hoặc tiến trình gateway khởi động lại. Dữ liệu chỉ được xóa khỏi kho pending sau khi backend phản hồi hợp lệ.

## 3. Acceptance checklist

- [x] Lưu mỗi record xuống file JSONL trên gateway.
- [x] Đọc lại toàn bộ record pending sau khi restart.
- [x] Hỗ trợ FIFO, xem trước batch và xóa đúng số record đã được xác nhận.
- [x] Có giới hạn dung lượng và bộ đếm record bị loại khi đầy.
- [x] Ghi qua file tạm, `fsync` và thay file nguyên tử.
- [x] Retry có giới hạn khi OneDrive/antivirus tạm khóa file trên Windows.
- [x] Không tự xóa queue khi khởi động; chỉ xóa khi người dùng truyền `--reset-queue`.

## 4. Đã thực hiện

| File/module | Nội dung |
|---|---|
| `src/farm-sim/src/farm/gateway/LocalStore.java` | Kho JSONL thread-safe, `append`, `peek`, `removeFirst`, capacity, reload, atomic replace và retry file lock. |
| `src/farm-sim/src/farm/Main.java` | Dùng queue từ cấu hình và chỉ reset khi có cờ `--reset-queue`. |
| `src/farm-sim/config/farm-sim.properties` | Cấu hình `storage.queue-file`, `storage.dead-letter-file`, `storage.capacity`. |
| `src/farm-sim/src/farm/tests/TestRunner.java` | Test persistence qua restart và capacity. |

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
java -cp out farm.tests.TestRunner
```

Tìm dòng:

```text
[PASS] LocalStore persistence and capacity
```

Kiểm tra tích hợp mất mạng:

```powershell
.\run.ps1 --missions 2 --tick-ms 0 --offline-first --reset-queue
```

Quan sát mission đầu giữ record trong `data/gateway-buffer.jsonl`, sau khi server hoạt động queue về `0`.

## 6. Kết quả đã xác minh

- Java compile theo target 17: PASS.
- Acceptance runner: **7 PASS, 0 FAIL**.
- LocalStore reload giữ đúng dữ liệu; capacity/drop policy hoạt động.
- Smoke test trên OneDrive: 6/6 record được sync, queue `0`, không mất dữ liệu khi file bị khóa tạm.

## 7. Đóng góp cho nhóm

- Cung cấp lớp lưu trữ an toàn cho F50–F52.
- Đảm bảo phần ingestion của H không làm mất dữ liệu khi backend tạm ngừng.
- Cung cấp file queue rõ ràng để QA kiểm tra store-and-forward.

## 8. Phụ thuộc và handoff

- H cần giữ contract response batch và deduplication `(nodeId, seq)`.
- F50 sử dụng `peek/removeFirst`; F52 quyết định thời điểm retry.
- Khi chuyển sang gateway thật, nhóm triển khai cần quyết định capacity và chính sách backup phù hợp thiết bị.

## 9. Giới hạn trung thực

- Bản demo dùng JSONL; kiến trúc production có thể thay bằng SQLite nhưng phải giữ semantics FIFO/ack.
- `fsync` giúp giảm rủi ro mất dữ liệu nhưng không thay thế backup khi thiết bị hỏng vật lý.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file task này vào SCRUM-74.
- [ ] Đính kèm link code `LocalStore.java`, `Main.java` và config.
- [ ] Dán kết quả `7 PASS, 0 FAIL` và E2E queue `0`.
- [ ] Nhờ H hoặc QA review hành vi ack/persistence.
- [ ] Chuyển sang Done sau khi reviewer xác nhận.

## 11. Comment Jira để copy

```text
Implemented SCRUM-74 / F48 Local Data Storage.
- Durable JSONL queue with restart reload, FIFO peek/ack, capacity/drop counter.
- Temp file + fsync + atomic replace; bounded retry for transient Windows/OneDrive locks.
- Queue is preserved by default; --reset-queue is explicit only.
Verification: Java build PASS; TestRunner 7 PASS/0 FAIL; offline/online E2E ended with queue=0.
Main files: LocalStore.java, Main.java, config/farm-sim.properties.
Proposed status: Ready for Review.
Limitation: demo storage is JSONL; production may replace it with SQLite while preserving the same semantics.
```
