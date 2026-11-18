# SCRUM-113 — F87 Implement Data Timeout Detection (F09)

## 1. Thông tin task

| Thuộc tính | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board trong ảnh | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Yêu cầu liên quan | FR F09, NFR02 |

## 2. Mục tiêu

Phát hiện node không có dữ liệu mới lâu hơn thời gian cấu hình, tạo đúng một alert active và tự resolve khi node gửi dữ liệu trở lại.

## 3. Acceptance checklist

- [x] Dùng `staleAfterSeconds` từ AlertPolicy, không hardcode trong detector.
- [x] Theo dõi thời điểm đăng ký và measurement mới nhất của từng node.
- [x] Tạo `DATA_TIMEOUT` khi vượt thời gian cho phép.
- [x] Không tạo trùng alert khi điều kiện vẫn còn.
- [x] Tăng severity khi thời gian mất dữ liệu vượt hai lần ngưỡng.
- [x] Tự resolve khi packet mới đến.
- [x] Giữ history/audit của alert đã resolve.

## 4. Đã thực hiện

| File/module | Nội dung |
|---|---|
| `src/farm-sim/src/farm/alert/AlertEngine.java` | Registry/last measurement, `checkTimeouts`, create/dedupe/resolve. |
| `src/farm-sim/src/farm/alert/AlertPolicy.java` | Cấu hình timeout bền vững. |
| `src/farm-sim/src/farm/alert/AlertRepository.java` | Active fingerprint và lịch sử trạng thái. |
| `src/farm-sim/src/farm/server/AlertApi.java` | Chạy timeout check khi đọc alert API trong mock server. |
| `src/farm-sim/src/farm/tests/TestRunner.java` | Test timeout dedupe và resolve. |

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
java -cp out farm.tests.TestRunner
```

Tìm các dòng test AlertEngine và log `DATA_TIMEOUT`. Có thể giảm `staleAfterSeconds` bằng API F86 trong môi trường test, đợi quá ngưỡng rồi gọi:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/alerts
```

Sau khi gửi packet mới, alert phải chuyển `RESOLVED` và không tạo bản sao active.

## 6. Kết quả đã xác minh

- AlertEngine abnormal/low-battery/timeout dedupe: PASS.
- Timeout tạo một alert, lần check lặp không tăng số alert active.
- Packet mới resolve `DATA_TIMEOUT` và giữ event history.
- Tổng TestRunner: **7 PASS, 0 FAIL**.

## 7. Đóng góp cho nhóm

- Giúp quản trị viên biết node mất tín hiệu hoặc ngừng đo.
- Cung cấp alert cho NL thực hiện xử lý/đóng và web notification.
- Đáp ứng yêu cầu freshness/stale của NFR02.

## 8. Phụ thuộc và handoff

- F86 cung cấp timeout cấu hình.
- F90 lưu/dedupe alert.
- NL dùng `DATA_TIMEOUT`, severity và history khi làm F91/F92.

## 9. Giới hạn trung thực

- Trong mock server, timeout scan được kích hoạt khi Alert API được truy cập; production backend nên chạy scheduler định kỳ độc lập.
- Chất lượng phát hiện phụ thuộc đồng hồ và timestamp measurement hợp lệ từ F47.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file task vào SCRUM-113.
- [ ] Đính kèm `AlertEngine`, `AlertPolicy`, `AlertRepository`.
- [ ] Dán test timeout create/dedupe/resolve PASS.
- [ ] Nhờ NL xác nhận type/severity/history cần cho UI.
- [ ] Ghi limitation scheduler mock trước khi chuyển Done.

## 11. Comment Jira để copy

```text
Implemented SCRUM-113 / F87 Data Timeout Detection.
- Uses configured staleAfterSeconds and per-node last measurement/registration time.
- Creates one deduplicated DATA_TIMEOUT alert, escalates severity for longer outages and auto-resolves on new data.
- Alert history remains persisted for audit.
Verification: AlertEngine timeout create/dedupe/resolve test PASS; full TestRunner 7 PASS/0 FAIL.
Handoff: NL consumes DATA_TIMEOUT alerts; production backend should schedule checkTimeouts periodically (mock triggers it through the alert API).
Proposed status: Ready for Review.
```
