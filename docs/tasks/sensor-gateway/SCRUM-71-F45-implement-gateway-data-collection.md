# SCRUM-71 – F45 Implement Gateway Data Collection

## 1. Thông tin task

| Mục | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Loại bàn giao | Gateway collection pipeline |

## 2. Mục tiêu

Hoàn thiện luồng Gateway: discover → request → receive → authenticate → wrap metadata → lưu local queue, đồng thời retry packet loss và không thu trùng một node trong cùng mission.

## 3. Acceptance checklist

- [x] Discover node trong tầm và bỏ qua node đã xử lý.
- [x] Gửi request với nonce ngẫu nhiên.
- [x] Retry ở tick sau khi radio packet loss.
- [x] Verify signature/node/nonce/seq/timestamp.
- [x] Bọc `gatewayId`, receive time, lat/lon/altitude và RSSI.
- [x] Append local queue trước khi upload.
- [x] Mỗi node hợp lệ tối đa một lần trong mỗi mission.
- [x] Node bị reject được block/log với lý do.

## 4. Đã thực hiện

- [`DataCollector.java`](../../../src/farm-sim/src/farm/gateway/DataCollector.java): orchestration toàn bộ collection flow.
- [`RadioMedium.java`](../../../src/farm-sim/src/farm/node/RadioMedium.java): discovery/request, range, loss và RSSI.
- [`LocalStore.java`](../../../src/farm-sim/src/farm/gateway/LocalStore.java): durable append trước upload.
- [`UavGateway.java`](../../../src/farm-sim/src/farm/gateway/UavGateway.java): position metadata.
- [`Main.java`](../../../src/farm-sim/src/farm/Main.java): per-mission reset và polling loop.

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
.\run.ps1 --missions 1 --tick-ms 0 --reset-queue
```

Quan sát `COLLECT`, `REJECT`, `MISSION`, `UPLOAD`. Với packet-loss rate cấu hình, lost packet phải được thử lại khi node còn trong tầm.

## 6. Kết quả đã xác minh

- E2E một mission: 6 node hợp lệ collected, 2 node security rejected.
- Mỗi accepted packet có metadata gateway/time/location/RSSI.
- Online smoke test sync 6/6, queue về 0 và server lưu 36 readings.

## 7. Đóng góp cho nhóm

Đây là cầu nối trực tiếp từ sensor layer tới offline queue và H ingestion API; cung cấp collection metadata cho mission result/dashboard.

## 8. Phụ thuộc và handoff

- F42 discovery, F43 authentication, F48/F50 local queue.
- H nhận gateway record/batch theo F23 protocol.

## 9. Giới hạn trung thực

Radio transport đang được mô phỏng. Chưa có MQTT/LoRa adapter thật; packet loss/retry là deterministic simulation phục vụ test tích hợp.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file này vào SCRUM-71.
- [ ] Gắn link `DataCollector`, `RadioMedium`, `LocalStore`, `UavGateway`.
- [ ] Đính kèm summary 6 collected/2 rejected/queue 0.
- [ ] Gửi sample wrapped record cho H.
- [ ] Chuyển Ready for Review.

## 11. Comment Jira có thể copy

```text
Implemented F45 Gateway Data Collection pipeline: discover → nonce request → receive → HMAC/replay verification → gateway metadata wrap → durable local queue.
Packet loss is retried while in range; a node is collected once per mission; rejected nodes are blocked and logged.
Online smoke result: 6 valid nodes synchronized, queue=0, 36 readings stored; unknown/bad-signature nodes rejected.
Handoff: wrapped record/batch schema is documented for Backend ingestion.
Status proposed: Ready for Review.
```
