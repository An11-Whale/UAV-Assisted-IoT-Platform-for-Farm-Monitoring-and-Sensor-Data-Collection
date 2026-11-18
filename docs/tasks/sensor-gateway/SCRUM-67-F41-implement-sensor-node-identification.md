# SCRUM-67 – F41 Implement Sensor Node Identification

## 1. Thông tin task

| Mục | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Loại bàn giao | Node identity model, validation và registry protection |

## 2. Mục tiêu

Mỗi Sensor Node phải có ID duy nhất, đúng format và kèm model/firmware/farm để discovery, authentication, dedupe và quản lý thiết bị không gắn nhầm dữ liệu.

## 3. Acceptance checklist

- [x] `nodeId` đúng regex `^NODE-\d{3}$`.
- [x] Identity có model, firmware và farm ID.
- [x] Config từ chối node ID sai hoặc trùng.
- [x] Radio registry không silently overwrite node trùng ID.
- [x] Beacon và packet đều mang cùng `nodeId`.
- [x] Key store liên kết khóa theo từng node.

## 4. Đã thực hiện

- [`NodeIdentity.java`](../../../src/farm-sim/src/farm/node/NodeIdentity.java): immutable identity và format validation.
- [`SensorNode.java`](../../../src/farm-sim/src/farm/node/SensorNode.java): dùng identity trong beacon/packet.
- [`RadioMedium.java`](../../../src/farm-sim/src/farm/node/RadioMedium.java): `putIfAbsent`, ném lỗi khi ID trùng.
- [`FarmConfig.java`](../../../src/farm-sim/src/farm/config/FarmConfig.java): validate danh sách node/config duplicate.
- [`KeyStore.java`](../../../src/farm-sim/src/farm/gateway/KeyStore.java): mapping node → random key.

## 5. Quy trình kiểm tra

1. Build và chạy cấu hình mặc định; xác nhận `NODE-001..006` được collect.
2. Trong bản sao config test, đổi một ID thành `NODE-X` và chạy; startup phải báo config sai.
3. Lặp lại cùng ID hai lần trong `nodes.ids`; startup phải từ chối duplicate.
4. Chạy acceptance suite để kiểm tra FarmConfig/KeyStore persistence.

## 6. Kết quả đã xác minh

- Config mặc định load đúng 6 node duy nhất.
- Generated KeyStore reload giữ cùng khóa và trả defensive copy.
- E2E hiển thị đúng ID của 6 node trong log/API.

## 7. Đóng góp cho nhóm

Cho H khóa dedupe/foreign key ổn định, cho dashboard tên thiết bị nhất quán và cho Gateway biết node nào được provision.

## 8. Phụ thuộc và handoff

- H dùng `nodeId` làm identity/dedupe cùng `seq`.
- F42 beacon và F43 authentication phụ thuộc identity này.

## 9. Giới hạn trung thực

Format demo hỗ trợ tối đa ba chữ số sau `NODE-`. Nếu production cần UUID/serial dài hơn, phải version hóa contract và migration dữ liệu.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file này vào SCRUM-67.
- [ ] Gắn link `NodeIdentity`, `FarmConfig`, `RadioMedium`, `KeyStore`.
- [ ] Đính kèm output load 6 node và KeyStore test PASS.
- [ ] Xác nhận H dùng cùng format `NODE-001`.
- [ ] Chuyển Ready for Review.

## 11. Comment Jira có thể copy

```text
Implemented F41 unique Sensor Node identification.
NodeIdentity validates NODE-xxx and carries model/firmware/farm; FarmConfig and RadioMedium reject duplicate/invalid IDs; KeyStore provisions a separate key per node.
Verification: six configured nodes load and collect successfully; FarmConfig/KeyStore persistence test PASS.
Handoff: Backend should use nodeId + seq as the deduplication identity.
Status proposed: Ready for Review.
```
