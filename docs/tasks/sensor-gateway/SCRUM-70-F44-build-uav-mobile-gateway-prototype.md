# SCRUM-70 – F44 Build UAV Mobile Gateway Prototype

## 1. Thông tin task

| Mục | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Loại bàn giao | UAV-mounted gateway simulation prototype |

## 2. Mục tiêu

Mô phỏng Mobile Gateway gắn trên UAV, di chuyển theo route/waypoint, mang gateway ID, altitude, radio range và vị trí để thu dữ liệu node ngoài đồng.

## 3. Acceptance checklist

- [x] Có gateway ID, speed, altitude và radio range.
- [x] Bay theo danh sách waypoint cấu hình và quay về base.
- [x] Cập nhật tọa độ theo từng simulation tick.
- [x] Quy đổi vị trí tương đối sang latitude/longitude cho collection metadata.
- [x] Có thể chạy nhiều mission.
- [x] Không triển khai direct flight control ngoài phạm vi.

## 4. Đã thực hiện

- [`UavGateway.java`](../../../src/farm-sim/src/farm/gateway/UavGateway.java): state vị trí, speed/range/altitude, waypoint stepping và lat/lon mapping.
- [`FarmConfig.java`](../../../src/farm-sim/src/farm/config/FarmConfig.java) và [`farm-sim.properties`](../../../src/farm-sim/config/farm-sim.properties): cấu hình gateway/route ngoài code.
- [`Main.java`](../../../src/farm-sim/src/farm/Main.java): mission loop và integration với discovery/collector/uploader.

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
.\run.ps1 --missions 2 --tick-ms 0 --reset-queue
```

Kiểm tra mỗi mission có log bắt đầu/về base; sáu node dọc hai hàng được thu khi UAV đi qua route.

## 6. Kết quả đã xác minh

- E2E hai mission thu tổng 12 sensor packets.
- Gateway ghi lat/lon/altitude/RSSI vào mỗi record.
- Route, range, speed, altitude được load/validate từ config.

## 7. Đóng góp cho nhóm

Cung cấp edge/mobile collection prototype cho Task Package 4, tạo position/time metadata để H lưu và dashboard/mission module đối chiếu collection point.

## 8. Phụ thuộc và handoff

- F42/F43/F45 chạy dựa trên vị trí/range của gateway.
- Mission/telemetry thật có thể cấp route/vị trí qua adapter sau này.

## 9. Giới hạn trung thực

Đây là mô phỏng chuyển động, không điều khiển motor/flight controller, không có obstacle avoidance và không phải Raspberry Pi/UAV vật lý. Những chức năng đó nằm ngoài phạm vi phiên bản đầu.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file này vào SCRUM-70.
- [ ] Gắn link `UavGateway`, config và `Main`.
- [ ] Đính kèm log hai mission/12 packets.
- [ ] Ghi rõ không có direct flight control.
- [ ] Chuyển Ready for Review.

## 11. Comment Jira có thể copy

```text
Implemented F44 UAV Mobile Gateway simulation prototype.
Gateway identity, speed, altitude, radio range and waypoint route are externally configured; position is converted to collection lat/lon metadata.
E2E verification: two missions collected 12 valid node packets and returned to base.
Scope note: this is the approved mobile-gateway simulator, not direct UAV flight-control or physical Raspberry Pi integration.
Status proposed: Ready for Review.
```
