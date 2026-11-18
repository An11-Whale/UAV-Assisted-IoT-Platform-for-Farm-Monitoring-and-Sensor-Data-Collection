# SCRUM-78 — F52 Implement Automatic Retry Mechanism

## 1. Thông tin task

| Thuộc tính | Giá trị |
|---|---|
| Người phụ trách | LQ |
| Trạng thái trên board trong ảnh | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Yêu cầu liên quan | FR F20, NFR04, NFR05 |

## 2. Mục tiêu

Tự thử đồng bộ lại dữ liệu pending sau lỗi mạng/API bằng exponential backoff có giới hạn và jitter, không `sleep` chặn vòng lặp thu thập và không tạo dữ liệu trùng ở backend.

## 3. Acceptance checklist

- [x] Lần lỗi đầu dùng initial delay cấu hình.
- [x] Delay tăng theo multiplier và không vượt max delay.
- [x] Có jitter để nhiều gateway không retry cùng lúc.
- [x] `syncIfDue()` bỏ qua an toàn khi chưa đến hạn, không block bằng sleep.
- [x] Thành công reset failure count và retry time.
- [x] Thất bại giữ nguyên pending queue.
- [x] Retry gửi lại an toàn nhờ deduplication `(nodeId, seq)` ở backend.

## 4. Đã thực hiện

| File/module | Nội dung |
|---|---|
| `src/farm-sim/src/farm/gateway/RetryPolicy.java` | Exponential backoff, max delay, multiplier và jitter. |
| `src/farm-sim/src/farm/gateway/Uploader.java` | `syncIfDue`, `nextAttemptAt`, failure scheduling và result status. |
| `src/farm-sim/src/farm/Main.java` | Gọi `syncIfDue()` định kỳ trong vòng lặp gateway. |
| `src/farm-sim/config/farm-sim.properties` | `retry.initial-delay-ms`, `max-delay-ms`, `multiplier`, `jitter-ratio`. |

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
.\run.ps1 --missions 2 --tick-ms 0 --offline-first --reset-queue
```

Quan sát log `RETRY` khi server chưa chạy, sau đó log `UPLOAD` thành công khi server hoạt động. Kiểm tra summary:

```text
Da dong bo server    : 12
Con trong hang doi   : 0
Ket noi cuoi          : ONLINE
```

## 6. Kết quả đã xác minh

- Offline-first: mission 1 giữ 6 record; mission 2 tổng cộng 12 record được sync.
- Queue cuối bằng 0, connectivity `ONLINE`.
- Backend reject được tách vào dead-letter; lỗi response giữ pending để retry.
- Policy validation và config load đã chạy trong acceptance tests.

## 7. Đóng góp cho nhóm

- Hoàn tất cơ chế store-and-forward của FR F20.
- Giảm thao tác thủ công khi Internet ở nông trại chập chờn.
- Bảo vệ backend H khỏi retry storm nhờ backoff+jitter.

## 8. Phụ thuộc và handoff

- F50 cung cấp durable queue.
- F51 cung cấp connectivity result.
- Backend của H bắt buộc deduplicate `(nodeId, seq)`.
- Production gateway cần gọi `syncIfDue()` từ service loop/scheduler luôn chạy.

## 9. Giới hạn trung thực

- Demo gọi retry trong vòng lặp mô phỏng; chưa đóng gói thành Windows/Linux system service.
- `uploadAll()` ở ranh giới mission là force-attempt để demo nhanh; cơ chế tự động chính vẫn là `syncIfDue()`.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file task vào SCRUM-78.
- [ ] Đính kèm `RetryPolicy`, `Uploader`, config và log `RETRY`.
- [ ] Dán E2E 12 record/queue 0/ONLINE.
- [ ] Nhờ H xác nhận dedup contract.
- [ ] Chuyển Done sau review retry timing và backend dedup.

## 11. Comment Jira để copy

```text
Implemented SCRUM-78 / F52 Automatic Retry.
- Bounded exponential backoff with configurable multiplier/max delay and jitter.
- Uploader.syncIfDue() is non-sleeping and called from the gateway loop; failure retains pending records and success resets retry state.
Verification: offline-first E2E synchronized 12 records after recovery, queue=0, connectivity=ONLINE.
Dependency: H ingestion must deduplicate (nodeId,seq) for ambiguous/repeated delivery.
Limitation: prototype loop is not yet packaged as an OS service.
Proposed status: Ready for Review.
```
