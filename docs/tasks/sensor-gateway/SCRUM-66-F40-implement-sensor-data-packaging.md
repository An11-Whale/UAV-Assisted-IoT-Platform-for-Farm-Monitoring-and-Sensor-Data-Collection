# SCRUM-66 – F40 Implement Sensor Data Packaging

## 1. Thông tin task

| Mục | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Loại bàn giao | JSON packet v1 + signature |

## 2. Mục tiêu

Đóng gói readings thành JSON có version, định danh, sequence, nonce, timestamp, battery và chữ ký để Gateway/Backend xử lý ổn định.

## 3. Acceptance checklist

- [x] Packet parse được bằng JSON.
- [x] Có `v`, `nodeId`, `seq`, `nonce`, `ts`, `battery`, `readings`, `sig`.
- [x] Reading có sensor/value/unit/raw/status.
- [x] Format số không phụ thuộc locale.
- [x] HMAC ký đúng body trước field `sig`.
- [x] Packet bị sửa không verify được.

## 4. Đã thực hiện

- [`PacketBuilder.java`](../../../src/farm-sim/src/farm/node/PacketBuilder.java): tạo packet JSON v1 theo thứ tự field ổn định.
- [`Reading.java`](../../../src/farm-sim/src/farm/sensor/Reading.java): serialize reading `OK/ERROR`.
- [`Auth.java`](../../../src/farm-sim/src/farm/common/Auth.java): attach/split HMAC signature.
- [`Json.java`](../../../src/farm-sim/src/farm/common/Json.java): parser/escaping không cần thư viện ngoài.
- [`TestRunner.java`](../../../src/farm-sim/src/farm/tests/TestRunner.java): test packet HMAC/verify/tamper.

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
java -cp out farm.tests.TestRunner
```

Sau đó chạy demo và xem packet mẫu/contract trong [`src/farm-sim/README.md`](../../../src/farm-sim/README.md).

## 6. Kết quả đã xác minh

- Test `Packet HMAC split and verification` PASS.
- Packet đúng key verify thành công; payload bị sửa bị từ chối.
- E2E packet được gateway wrap và backend nhận thành công.

## 7. Đóng góp cho nhóm

Cung cấp data contract duy nhất cho sensor, gateway, H ingestion/validation và dashboard mapping; giảm lỗi lệch tên field/unit.

## 8. Phụ thuộc và handoff

- F43 dùng nguyên body/signature để verify.
- H cần giữ schema/version và thông báo trước nếu thay contract.

## 9. Giới hạn trung thực

Canonicalization hiện dựa trên thứ tự field do `PacketBuilder` tạo. Nếu nhiều ngôn ngữ cùng ký packet, nhóm nên chuẩn hóa canonical JSON hoặc tăng protocol version.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file này vào SCRUM-66.
- [ ] Gắn link `PacketBuilder`, `Reading`, `Auth` và protocol spec.
- [ ] Đính kèm output HMAC test PASS.
- [ ] Gửi sample packet cho H.
- [ ] Chuyển Ready for Review.

## 11. Comment Jira có thể copy

```text
Implemented F40 JSON sensor packet v1 with v/nodeId/seq/nonce/timestamp/battery/readings/signature fields.
HMAC is calculated over the stable packet body and attached as the final sig field.
Verification: packet parse/split/verify/tamper acceptance test PASS; packet is consumed successfully by gateway and ingestion demo.
Handoff: sample schema is documented in Sensor–Gateway Protocol v1 for Backend validation.
Status proposed: Ready for Review.
```
