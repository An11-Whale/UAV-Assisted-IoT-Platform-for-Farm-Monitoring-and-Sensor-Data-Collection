# SCRUM-69 – F43 Implement Sensor Authentication

## 1. Thông tin task

| Mục | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Loại bàn giao | HMAC authentication, key provisioning và replay protection |

## 2. Mục tiêu

Chỉ cho phép UAV Gateway lưu dữ liệu từ Sensor Node đã provision đúng key; phát hiện giả mạo, nonce sai, replay sequence và clock skew.

## 3. Acceptance checklist

- [x] HMAC-SHA256 theo key riêng từng node.
- [x] So sánh signature constant-time.
- [x] Unknown node bị reject trước collection.
- [x] Signature sai, nodeId mismatch và nonce sai bị reject.
- [x] Sequence cũ/trùng và timestamp lệch quá 30 giây bị reject.
- [x] Key ngẫu nhiên 32 byte, lưu ngoài Git và reload qua restart.
- [x] Lỗi packet/parser không làm chết collection loop.

## 4. Đã thực hiện

- [`Auth.java`](../../../src/farm-sim/src/farm/common/Auth.java): sign/verify/attach/split HMAC.
- [`KeyStore.java`](../../../src/farm-sim/src/farm/gateway/KeyStore.java): provision, random key persistence và defensive copy.
- [`DataCollector.verify()`](../../../src/farm-sim/src/farm/gateway/DataCollector.java): signature/node/nonce/seq/timestamp checks.
- [`PacketBuilder.java`](../../../src/farm-sim/src/farm/node/PacketBuilder.java): node ký packet.
- `NODE-098` và `NODE-099` trong [`Main.java`](../../../src/farm-sim/src/farm/Main.java): security scenarios unknown/bad-key.

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
java -cp out farm.tests.TestRunner
.\run.ps1 --missions 1 --tick-ms 0 --reset-queue
```

Kiểm tra `NODE-098: unknown node`, `NODE-099: chu ky HMAC khong hop le`; sáu node provision phải được nhận.

## 6. Kết quả đã xác minh

- Packet HMAC verify/tamper test PASS.
- KeyStore reload cùng key và không để caller sửa key nội bộ.
- E2E: 6 node hợp lệ accepted; unknown và bad signature rejected, không vào queue.

## 7. Đóng góp cho nhóm

Bảo vệ F18/NFR03, ngăn dữ liệu từ thiết bị không đăng ký đi vào H ingestion và tạo alert/dashboard giả.

## 8. Phụ thuộc và handoff

- Provisioning phải đưa cùng key vào sensor và gateway.
- Backend vẫn cần TLS/API authentication cho đường Gateway → Server; HMAC này bảo vệ Sensor → Gateway.

## 9. Giới hạn trung thực

Prototype dùng pre-shared key. Chưa có UI/quy trình rotate/revoke key tập trung hoặc secure element phần cứng; production cần lifecycle quản lý khóa riêng.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file này vào SCRUM-69.
- [ ] Gắn link `Auth`, `KeyStore`, `DataCollector`, `PacketBuilder`.
- [ ] Đính kèm HMAC test và log reject 098/099.
- [ ] Không đính kèm file `data/node-keys.properties`.
- [ ] Chuyển Ready for Review.

## 11. Comment Jira có thể copy

```text
Implemented F43 Sensor Authentication with per-node 32-byte random keys and HMAC-SHA256.
Gateway verifies signature, nodeId, nonce, monotonic sequence and ±30s clock skew; parser failures are safely rejected.
Verification: HMAC/tamper and KeyStore persistence tests PASS; E2E accepted 6 provisioned nodes and rejected NODE-098 unknown / NODE-099 invalid signature.
Runtime key files are excluded from Git.
Status proposed: Ready for Review.
```
