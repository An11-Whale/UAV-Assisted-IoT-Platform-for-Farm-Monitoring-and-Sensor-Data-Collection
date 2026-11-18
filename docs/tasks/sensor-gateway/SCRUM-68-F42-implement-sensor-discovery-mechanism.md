# SCRUM-68 – F42 Implement Sensor Discovery Mechanism

## 1. Thông tin task

| Mục | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Loại bàn giao | Discovery beacon + radio range simulator |

## 2. Mục tiêu

Cho UAV Gateway phát hiện các Sensor Node đang nằm trong vùng phủ sóng, đọc capability từ beacon và chỉ bắt đầu collection với node phù hợp.

## 3. Acceptance checklist

- [x] Sensor Node phát beacon có type/version/node/model/fw/farm/sensors.
- [x] Gateway chỉ nghe node trong bán kính cấu hình.
- [x] Discovery trả kèm RSSI mô phỏng.
- [x] Node ngoài tầm không được request.
- [x] Beacon lỗi/thiếu nodeId bị reject an toàn, không làm chết collection loop.
- [x] Discovery tách khỏi authentication; beacon không tự chứng minh danh tính.

## 4. Đã thực hiện

- [`SensorNode.beaconJson()`](../../../src/farm-sim/src/farm/node/SensorNode.java): tạo capability beacon.
- [`RadioMedium.java`](../../../src/farm-sim/src/farm/node/RadioMedium.java): register node, tính distance/RSSI và `discover(x,y,range)`.
- [`DataCollector.java`](../../../src/farm-sim/src/farm/gateway/DataCollector.java): parse beacon, kiểm tra key và gửi request.
- [`FarmConfig.java`](../../../src/farm-sim/src/farm/config/FarmConfig.java): cấu hình node coordinates và gateway radio range.

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
.\run.ps1 --missions 1 --tick-ms 0 --reset-queue
```

Quan sát node được collect dọc route khi UAV đi vào vùng 40 m. Có thể đổi `gateway.radio-range-m` hoặc tọa độ node trong một config test để kiểm tra node ngoài tầm không xuất hiện.

## 6. Kết quả đã xác minh

- E2E gateway phát hiện và thu đủ `NODE-001..006` trên route cấu hình.
- `NODE-098/099` cũng được nghe khi trong tầm nhưng chuyển sang authentication/reject đúng quy trình.
- Duplicate node ID bị registry từ chối.

## 7. Đóng góp cho nhóm

Hiện thực phần discovery của F18 và cung cấp danh sách candidate node cho F43/F45; operator/dashboard có capability/identity thống nhất.

## 8. Phụ thuộc và handoff

- F43 quyết định node nghe thấy có hợp lệ hay không.
- Khi thay bằng MQTT/LoRa, adapter phải giữ beacon schema trong F23 protocol.

## 9. Giới hạn trung thực

Discovery hiện dùng `RadioMedium` in-memory, không phải scan Wi‑Fi/BLE/LoRa thật. Range/RSSI là mô hình toán học phục vụ integration test.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file này vào SCRUM-68.
- [ ] Gắn link `SensorNode`, `RadioMedium`, `DataCollector` và protocol.
- [ ] Đính kèm log 6 node được discover/collect.
- [ ] Ghi rõ simulator radio, chưa phải hardware scan.
- [ ] Chuyển Ready for Review.

## 11. Comment Jira có thể copy

```text
Implemented F42 Sensor Discovery using versioned capability beacons and configurable radio range/RSSI simulation.
Gateway discovers only nodes in range, safely rejects malformed beacons and passes registered candidates to authentication/collection.
E2E result: NODE-001..006 were discovered along the configured UAV route.
Limitation: RadioMedium is the approved integration simulator; MQTT/LoRa hardware adapter remains a later deployment step.
Status proposed: Ready for Review.
```
