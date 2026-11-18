# SCRUM-29 – F23 Design Sensor–Gateway Communication Protocol

## 1. Thông tin task

| Mục | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board | Done |
| Trạng thái đề xuất khi nộp | **Done – cần H review contract** |
| Loại bàn giao | Protocol specification v1 + implementation evidence |

## 2. Mục tiêu

Tạo hợp đồng ổn định giữa Sensor Node, UAV Gateway và Backend để discovery, collect, authentication, retry và deduplication dùng cùng schema.

## 3. Acceptance checklist

- [x] Định nghĩa discovery beacon và collection request.
- [x] Định nghĩa signed sensor packet và gateway batch.
- [x] Có `nodeId`, `seq`, `nonce`, timestamp, battery, readings và HMAC.
- [x] Có rule chống unknown node, bad signature, nonce sai, replay và clock skew.
- [x] Có timeout, retry, error behavior và versioning.
- [x] Có mapping transport simulator/MQTT/LoRa.
- [x] Backend dedupe theo `(nodeId, seq)`.

## 4. Đã thực hiện

- [`docs/protocol/sensor-gateway-protocol.md`](../../protocol/sensor-gateway-protocol.md): đặc tả protocol v1 đầy đủ.
- [`PacketBuilder.java`](../../../src/farm-sim/src/farm/node/PacketBuilder.java): JSON packet v1.
- [`Auth.java`](../../../src/farm-sim/src/farm/common/Auth.java): HMAC-SHA256 và constant-time verify.
- [`DataCollector.java`](../../../src/farm-sim/src/farm/gateway/DataCollector.java): gateway validation flow.
- [`Uploader.java`](../../../src/farm-sim/src/farm/gateway/Uploader.java): batch/ack/retry/dead-letter.

## 5. Quy trình kiểm tra

```powershell
cd src\farm-sim
.\build.ps1
java -cp out farm.tests.TestRunner
.\run.ps1 --missions 1 --tick-ms 0 --reset-queue
```

Kiểm tra log: node hợp lệ được collect; `NODE-098` unknown và `NODE-099` HMAC sai bị reject; packet hợp lệ được upload.

## 6. Kết quả đã xác minh

- HMAC packet/split/verify/tamper test PASS.
- E2E thu 6 node đăng ký, từ chối đúng 2 node đối kháng.
- Batch được backend nhận, validation/dedupe contract hoạt động.

## 7. Đóng góp cho nhóm

- H có schema chính xác để triển khai ingestion/validation/deduplication.
- Sensor và Gateway không tự đặt field/topic khác nhau.
- QA có danh sách negative cases rõ ràng.

## 8. Phụ thuộc và handoff

- H phải giữ response `accepted/duplicates/rejected[]` và dedupe `(nodeId,seq)`.
- Adapter MQTT/LoRa tương lai phải giữ semantics protocol v1.

## 9. Giới hạn trung thực

Radio hiện là `RadioMedium` trong JVM. MQTT/LoRa và mã hóa link-layer chưa được triển khai bằng phần cứng thật; tài liệu chỉ định profile mục tiêu.

## 10. Checklist nộp thủ công

- [ ] Đính kèm protocol spec vào SCRUM-29.
- [ ] Đính kèm sample packet/batch và log reject.
- [ ] Nhờ H xác nhận ingestion response/dedupe.
- [ ] Gắn link các class implementation.
- [ ] Giữ Done sau khi H review contract.

## 11. Comment Jira có thể copy

```text
Completed F23 Sensor–Gateway Application Protocol v1.
Specification covers beacon, collect request, signed packet, gateway batch, HMAC/nonce/sequence/clock-skew checks, errors, retry, dedupe and versioning.
Verification: HMAC/tamper test PASS; E2E collected 6 registered nodes and rejected unknown/bad-signature nodes.
Handoff: Backend team to confirm POST /api/ingest response and (nodeId,seq) deduplication contract.
```
