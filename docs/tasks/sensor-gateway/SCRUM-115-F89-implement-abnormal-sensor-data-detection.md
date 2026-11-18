# SCRUM-115 — F89 Implement Abnormal Sensor Data Detection

## 1. Thông tin task

| Thuộc tính | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board trong ảnh | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Yêu cầu liên quan | FR F09/F10, NFR04 |

## 2. Mục tiêu

Sau khi record qua validation F47, so sánh từng reading hợp lệ với rule global/per-node, tạo alert cho giá trị thấp/cao bất thường và tách lỗi đọc cảm biến khỏi numeric anomaly.

## 3. Acceptance checklist

- [x] Dùng rule cấu hình theo node/metric từ F86.
- [x] Phát hiện cả thấp hơn min và cao hơn max.
- [x] Không tạo anomaly khi value nằm đúng biên an toàn.
- [x] Tính severity theo khoảng cách vượt ngưỡng.
- [x] Ánh sáng thấp ban đêm không bị coi là bất thường.
- [x] Reading `ERROR` tạo `SENSOR_READ_ERROR`, không so như số.
- [x] Dedupe alert active và auto-resolve khi reading bình thường.

## 4. Đã thực hiện

| File/module | Nội dung |
|---|---|
| `src/farm-sim/src/farm/alert/AlertEngine.java` | Rule lookup, low/high detection, severity, daylight rule, read-error alert và resolve. |
| `src/farm-sim/src/farm/alert/AlertPolicy.java` | Rule cho sáu metric và per-node override. |
| `src/farm-sim/src/farm/server/SensorDataValidator.java` | Kiểm tra sensor type, unit, range, status và value. |
| `src/farm-sim/src/farm/server/DashboardApi.java` | Dùng cùng policy khi gắn metric/node status. |
| `src/farm-sim/src/farm/tests/TestRunner.java` | Test abnormal create/dedupe/resolve. |

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
java -cp out farm.tests.TestRunner
```

Chạy tích hợp để quan sát alert từ sensor simulated:

```powershell
.\run.ps1 --missions 1 --tick-ms 0 --keep --reset-queue
Invoke-RestMethod 'http://localhost:8080/api/v1/alerts?limit=100'
```

Kiểm tra alert có `type`, `severity`, `nodeId`, `metric`, `observed`, `thresholdMin`, `thresholdMax`, `createdAt`, `status`.

## 6. Kết quả đã xác minh

- Unit/acceptance test tạo anomaly, không nhân đôi và resolve đúng.
- E2E ingestion tạo alert persisted từ dữ liệu mô phỏng.
- Dashboard và engine dùng chung rule; tổng runner **7 PASS, 0 FAIL**.

## 7. Đóng góp cho nhóm

- Chuyển dữ liệu nông trại bất thường thành sự kiện mà NL có thể hiển thị/xử lý.
- Cung cấp bằng chứng integration cho validation của H.
- Giúp dashboard phân biệt metric bình thường và bất thường.

## 8. Phụ thuộc và handoff

- F47 của H phải validate type/unit/range trước khi detector chạy.
- F86 cung cấp rule; F90 lưu alert; F93 gửi email.
- NL tiêu thụ payload alert cho F91/F92.

## 9. Giới hạn trung thực

- Chỉ sáu loại sensor trong `SensorType` được hỗ trợ; thêm sensor mới cần thêm unit/range/rule.
- Quy tắc hiện là threshold, chưa phải AI/anomaly model và đúng với phạm vi dự án.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file task vào SCRUM-115.
- [ ] Đính kèm `AlertEngine`, `AlertPolicy`, validator.
- [ ] Dán test abnormal create/dedupe/resolve và mẫu JSON alert.
- [ ] Nhờ H review điểm gọi sau validation, NL review payload.
- [ ] Chuyển Done sau review.

## 11. Comment Jira để copy

```text
Implemented SCRUM-115 / F89 Abnormal Sensor Data Detection.
- Applies global/per-node rules after validation; detects low/high values and derives severity.
- Daytime-aware light rule prevents false low-light alerts at night.
- Reading ERROR creates SENSOR_READ_ERROR instead of a numeric anomaly.
- Active alerts are deduplicated and auto-resolved on normal data.
Verification: AlertEngine abnormal create/dedupe/resolve PASS; E2E ingestion created persisted alerts; full TestRunner 7 PASS/0 FAIL.
Dependency: H validation contract; NL consumes the resulting alert payload.
Proposed status: Ready for Review.
```
