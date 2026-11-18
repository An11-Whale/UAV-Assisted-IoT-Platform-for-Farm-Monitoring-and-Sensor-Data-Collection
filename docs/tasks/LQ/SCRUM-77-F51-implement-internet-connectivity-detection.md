# SCRUM-77 — F51 Implement Internet Connectivity Detection

## 1. Thông tin task

| Thuộc tính | Giá trị |
|---|---|
| Người phụ trách | LQ |
| Trạng thái trên board trong ảnh | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Yêu cầu liên quan | FR F20, NFR04, NFR05 |

## 2. Mục tiêu

Theo dõi kết nối dựa trên kết quả đồng bộ thật của gateway, phân biệt mất mạng với API có phản hồi nhưng đang lỗi, đồng thời cung cấp snapshot đủ chi tiết cho retry, log và giao diện vận hành.

## 3. Acceptance checklist

- [x] Có các trạng thái `UNKNOWN`, `ONLINE`, `DEGRADED`, `OFFLINE`.
- [x] Ghi thời điểm kiểm tra và thời điểm trạng thái thay đổi.
- [x] Đếm số lỗi liên tiếp.
- [x] Lưu HTTP status cuối và nguyên nhân lỗi an toàn.
- [x] Network exception chuyển `OFFLINE` mà không xóa queue.
- [x] HTTP non-2xx chuyển `DEGRADED` mà không xóa queue.
- [x] Upload thành công chuyển `ONLINE` và reset failure count.

## 4. Đã thực hiện

| File/module | Nội dung |
|---|---|
| `src/farm-sim/src/farm/gateway/ConnectivityMonitor.java` | State machine và immutable `Snapshot`. |
| `src/farm-sim/src/farm/gateway/Uploader.java` | Cập nhật monitor từ HTTP success, HTTP failure, network exception và lỗi local. |
| `src/farm-sim/src/farm/Main.java` | In trạng thái kết nối cuối trong summary. |

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
.\run.ps1 --missions 2 --tick-ms 0 --offline-first --reset-queue
```

Quan sát:

1. Mission 1 log không kết nối được API và giữ queue.
2. Mission 2 mock server khởi động.
3. Summary kết thúc với `Ket noi cuoi : ONLINE`.

Trong code tích hợp có thể đọc:

```java
ConnectivityMonitor.Snapshot state = uploader.connectivity();
```

## 6. Kết quả đã xác minh

- Offline-first chuyển từ network failure sang `ONLINE` sau khi server hoạt động.
- Queue vẫn nguyên khi `OFFLINE`/`DEGRADED`.
- E2E kết thúc 12 record sync và queue 0.

## 7. Đóng góp cho nhóm

- F52 dùng state/failure result để quyết định retry.
- Dashboard/operator có dữ liệu để hiển thị tình trạng gateway.
- QA phân biệt lỗi Internet, lỗi backend và lỗi xử lý local.

## 8. Phụ thuộc và handoff

- F50 phải giữ pending record khi không online.
- F52 dùng failure count và thời điểm retry.
- Nếu NL/H muốn hiển thị connectivity trên Web, cần expose snapshot qua API/telemetry backend.

## 9. Giới hạn trung thực

- Prototype đánh giá kết nối từ lần upload thật, không liên tục ping Internet khi queue rỗng.
- `ONLINE` có nghĩa ingestion API vừa chấp nhận batch, không khẳng định mọi dịch vụ Internet đều hoạt động.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file task vào SCRUM-77.
- [ ] Đính kèm `ConnectivityMonitor.java` và phần tích hợp trong `Uploader`.
- [ ] Dán log offline→online và summary `ONLINE`.
- [ ] Nhờ thành viên gateway/dashboard review ý nghĩa từng state.
- [ ] Chuyển Done sau khi nhóm thống nhất cách expose state.

## 11. Comment Jira để copy

```text
Implemented SCRUM-77 / F51 Connectivity Detection.
- Explicit UNKNOWN/ONLINE/DEGRADED/OFFLINE state with checkedAt, changedAt, failure count, HTTP status and detail.
- Network failure keeps queue and marks OFFLINE; HTTP non-2xx marks DEGRADED; accepted upload marks ONLINE.
Verification: offline-first E2E recovered to ONLINE, synchronized 12 records and left queue=0.
Main files: ConnectivityMonitor.java and Uploader.java.
Limitation: the prototype derives connectivity from real upload attempts; it does not continuously ping while the queue is empty.
Proposed status: Ready for Review.
```
