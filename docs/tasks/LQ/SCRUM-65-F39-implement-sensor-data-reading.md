# SCRUM-65 – F39 Implement Sensor Data Reading

## 1. Thông tin task

| Mục | Giá trị |
|---|---|
| Người phụ trách | LQ |
| Trạng thái trên board | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Loại bàn giao | Sensor reading abstraction và simulator implementation |

## 2. Mục tiêu

Chuẩn hóa cách đọc cảm biến để mọi loại sensor trả về cùng mô hình `Reading`, có giá trị, unit, raw ADC (nếu có) và trạng thái `OK/ERROR`.

## 3. Acceptance checklist

- [x] Có interface chung cho mọi sensor.
- [x] Một lần đo lấy 5 mẫu và tính trung bình.
- [x] Có noise/error simulation tái lập bằng seed.
- [x] Clamp giá trị trong khoảng hợp lệ.
- [x] Reading lỗi dùng `value:null`, không bị ingest như số hợp lệ.
- [x] Measurement timestamp được gắn vào packet.

## 4. Đã thực hiện

- [`Sensor.java`](../../../src/farm-sim/src/farm/sensor/Sensor.java): interface `name()` và `read(Instant)`.
- [`SimulatedSensor.java`](../../../src/farm-sim/src/farm/sensor/SimulatedSensor.java): 5 samples, average, noise, error rate và clamp.
- [`Reading.java`](../../../src/farm-sim/src/farm/sensor/Reading.java): type/value/raw/status và JSON mapping.
- Các implementation: `SoilMoistureSensor`, `TempHumiditySensor`, `LightSensor`, `PhSensor`, `WaterLevelSensor`.
- [`SensorNode.handleCollect`](../../../src/farm-sim/src/farm/node/SensorNode.java): gom readings và chuyển sang packet cùng timestamp/battery.

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
.\run.ps1 --missions 1 --tick-ms 0 --reset-queue
```

Quan sát log collect/upload; chạy với `--keep` rồi gọi `GET /api/readings?limit=100`. Kiểm tra `value`, `unit`, `ts`, `nodeId` và không có reading status lỗi bị lưu như numeric row.

## 6. Kết quả đã xác minh

- Java compile với `-Xlint:all` PASS.
- E2E thu 6 node và validate thành công 36 numeric rows.
- Error reading được giữ trong packet với status và bị loại khỏi numeric measurement rows.

## 7. Đóng góp cho nhóm

Tạo đầu vào thống nhất cho F40 packaging, H/F47 validation, historical data và alert detection; thêm sensor mới chỉ cần implement cùng interface.

## 8. Phụ thuộc và handoff

- F40 nhận `List<Reading>` để tạo packet.
- H phải xử lý `status=ERROR` khác với numeric abnormal value.

## 9. Giới hạn trung thực

Simulator đọc sensor khi gateway gửi collect request; chưa có firmware đo định kỳ và history buffer nằm trên ESP32 thật. Cơ chế này đủ cho integration prototype nhưng hardware node cần scheduler/storage riêng.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file này vào SCRUM-65.
- [ ] Gắn link `Sensor`, `SimulatedSensor`, `Reading` và `SensorNode`.
- [ ] Đính kèm log/API sample.
- [ ] Nêu rõ on-demand simulator và giới hạn firmware thật.
- [ ] Chuyển Ready for Review.

## 11. Comment Jira có thể copy

```text
Implemented F39 common sensor-reading flow using Sensor/SimulatedSensor/Reading.
Each simulated measurement averages 5 samples, applies calibrated range/noise and reports OK or ERROR with timestamp/raw value where available.
E2E verification: 6 nodes were collected and 36 valid measurement rows passed ingestion validation.
Limitation: current prototype reads on collection request; periodic ESP32-side buffering belongs to the physical firmware phase.
Status proposed: Ready for Review.
```
