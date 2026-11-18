# SCRUM-114 — F88 Implement Low Battery Detection (F09)

## 1. Thông tin task

| Thuộc tính | Giá trị |
|---|---|
| Người phụ trách | LQ |
| Trạng thái trên board trong ảnh | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Yêu cầu liên quan | FR F09, NFR09 |

## 2. Mục tiêu

So sánh pin node với ngưỡng cấu hình, tạo cảnh báo bảo trì có value/threshold/severity, chống trùng khi pin vẫn thấp và tự resolve khi pin trở lại bình thường.

## 3. Acceptance checklist

- [x] Dùng `lowBatteryPct` từ AlertPolicy.
- [x] Validate battery packet trong khoảng 0–100.
- [x] Tạo `LOW_BATTERY` khi value nhỏ hơn ngưỡng.
- [x] Severity `CRITICAL` khi pin dưới một nửa ngưỡng; còn lại `WARNING`.
- [x] Không nhân đôi alert active.
- [x] Tự resolve khi battery bằng hoặc cao hơn ngưỡng.
- [x] Dashboard dùng cùng policy để tính `WARNING`.

## 4. Đã thực hiện

| File/module | Nội dung |
|---|---|
| `src/farm-sim/src/farm/alert/AlertEngine.java` | Low-battery comparison, severity, dedupe và resolve. |
| `src/farm-sim/src/farm/alert/AlertPolicy.java` | Ngưỡng pin cấu hình/persistence/audit. |
| `src/farm-sim/src/farm/server/SensorDataValidator.java` | Reject battery thiếu hoặc ngoài 0–100. |
| `src/farm-sim/src/farm/server/DashboardApi.java` | Status node đọc cùng `lowBatteryPct`. |
| `src/farm-sim/src/farm/tests/TestRunner.java` | Test low-battery create/dedupe/resolve. |

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
java -cp out farm.tests.TestRunner
```

Test runner đưa battery dưới ngưỡng hai lần để xác nhận chỉ có một alert, sau đó đưa battery bình thường để xác nhận `RESOLVED`.

Khi server đang chạy, xem kết quả:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/v1/alerts?status=OPEN&limit=100'
```

## 6. Kết quả đã xác minh

- Tạo `LOW_BATTERY` đúng ngưỡng và severity.
- Lần evaluate lặp không tạo alert mới.
- Battery bình thường auto-resolve alert.
- Test AlertEngine PASS; tổng runner **7 PASS, 0 FAIL**.

## 7. Đóng góp cho nhóm

- Cho đội vận hành thay pin trước khi sensor ngừng hoạt động.
- Cung cấp type/value/threshold cho NL hiển thị cảnh báo rõ ràng.
- Đồng bộ status giữa backend mock và dashboard.

## 8. Phụ thuộc và handoff

- F40 phải đóng gói battery.
- F47 của H phải validate battery khi dùng backend thật.
- F86 cung cấp ngưỡng; F90 lưu alert; NL xử lý trên Web.

## 9. Giới hạn trung thực

- Pin trong simulator giảm chậm; acceptance test inject giá trị để kiểm tra nhanh.
- Không dự đoán tuổi thọ pin; chỉ phát hiện theo threshold hiện tại.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file task vào SCRUM-114.
- [ ] Đính kèm `AlertEngine`, validator và policy.
- [ ] Dán kết quả low-battery create/dedupe/resolve.
- [ ] Nhờ H/NL review field battery và alert payload.
- [ ] Chuyển Done sau review.

## 11. Comment Jira để copy

```text
Implemented SCRUM-114 / F88 Low Battery Detection.
- Uses configurable lowBatteryPct; validates battery in 0..100.
- Creates deduplicated LOW_BATTERY alerts with observed value/threshold and WARNING or CRITICAL severity.
- Auto-resolves when battery returns to the configured safe range; DashboardApi uses the same policy.
Verification: AlertEngine low-battery create/dedupe/resolve PASS; full TestRunner 7 PASS/0 FAIL.
Limitation: simulator battery changes slowly, so the acceptance test injects boundary values.
Proposed status: Ready for Review.
```
