# SCRUM-64 – F38 Build Light, pH and Water Level Sensor Prototype

## 1. Thông tin task

| Mục | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Loại bàn giao | Sensor prototype bằng simulator Java |

## 2. Mục tiêu

Bổ sung ba loại cảm biến Light, pH và Water Level vào prototype để chuỗi thu thập có đủ sáu chỉ số môi trường nông trại theo đề xuất.

## 3. Acceptance checklist

- [x] Light trả đơn vị `lux`, khoảng hợp lệ `0..120000`.
- [x] pH trả đơn vị `pH`, khoảng hợp lệ `0..14` và raw ADC mô phỏng.
- [x] Water level trả `%`, khoảng hợp lệ `0..100` và raw ADC mô phỏng.
- [x] Có noise, seed tái lập và xác suất lỗi đọc.
- [x] Giá trị được clamp theo `SensorType`.
- [x] Được tích hợp vào mỗi Sensor Node và upload end-to-end.

## 4. Đã thực hiện

- [`LightSensor.java`](../../../src/farm-sim/src/farm/sensor/LightSensor.java): mô hình ánh sáng theo giờ ban ngày và mây.
- [`PhSensor.java`](../../../src/farm-sim/src/farm/sensor/PhSensor.java): drift pH chậm và quy đổi raw ADC.
- [`WaterLevelSensor.java`](../../../src/farm-sim/src/farm/sensor/WaterLevelSensor.java): mực nước giảm theo thời gian, mô phỏng bơm châm.
- [`SimulatedSensor.java`](../../../src/farm-sim/src/farm/sensor/SimulatedSensor.java): lấy mẫu/noise/error chung.
- [`SensorType.java`](../../../src/farm-sim/src/farm/common/SensorType.java): key, unit và khoảng hợp lệ.
- [`Main.java`](../../../src/farm-sim/src/farm/Main.java): gắn ba sensor vào 6 node cấu hình.

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
.\run.ps1 --missions 1 --tick-ms 0 --port 18084 --reset-queue
```

Kiểm tra summary có `rows=36`: 6 node × 6 metric. Nếu chạy `--keep`, gọi `GET /api/readings?sensor=light`, `ph` và `water_level` để xem value/unit.

## 6. Kết quả đã xác minh

- Java compile PASS.
- E2E thu 6 node và tạo 36 measurement rows.
- Cả ba sensor được đóng gói, validate và hiển thị qua API/dashboard mapping.

## 7. Đóng góp cho nhóm

Cung cấp ba metric còn thiếu cho H kiểm thử ingestion/validation và cho dashboard/alert engine có dữ liệu ánh sáng, pH, mực nước.

## 8. Phụ thuộc và handoff

- H dùng `SensorType` để giữ cùng unit/range khi làm validation.
- Dashboard đổi `water_level %` sang cm chỉ ở presentation layer.

## 9. Giới hạn trung thực

Đây là **prototype mô phỏng**, không phải ESP32/đầu dò vật lý. Yêu cầu cho phép nghiệm thu hardware-dependent feature bằng simulation environment khi chưa có testbed; đo thực tế vẫn cần calibration riêng.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file này vào SCRUM-64.
- [ ] Gắn link ba class sensor và `SensorType`.
- [ ] Đính kèm log `rows=36` hoặc screenshot API readings.
- [ ] Ghi rõ “simulator prototype, chưa phải hardware”.
- [ ] Chuyển Ready for Review; Done sau khi H/UI xác nhận contract.

## 11. Comment Jira có thể copy

```text
Implemented F38 Light, pH and Water Level sensor prototypes in the Java simulation environment.
Each sensor provides the agreed key/unit/range, noise/raw values where applicable and ERROR simulation.
Integration result: 6 registered nodes produced 36 measurement rows (all six metrics) through gateway and ingestion API.
Limitation: simulator prototype; physical ESP32/probe calibration requires the hardware testbed.
Status proposed: Ready for Review.
```
