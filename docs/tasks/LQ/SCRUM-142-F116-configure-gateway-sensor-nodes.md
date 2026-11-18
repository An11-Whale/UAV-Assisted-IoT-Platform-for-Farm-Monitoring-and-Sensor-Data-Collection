# SCRUM-142 — F116 Configure Gateway and Sensor Nodes

## 1. Thông tin task

| Thuộc tính | Giá trị |
|---|---|
| Jira | `SCRUM-142` |
| Feature | `F116 – Configure Gateway and Sensor Nodes` |
| Người phụ trách | LQ |
| Board hiện tại | `To Do` |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Yêu cầu liên quan | F18, F20, NFR03, NFR05, NFR07, NFR08 |

## 2. Mục tiêu

Cho phép cấu hình gateway và sensor node từ file bên ngoài mã nguồn, kiểm tra cấu hình ngay khi khởi động và quản lý khóa HMAC mà không hardcode secret. Nhờ đó nhóm có thể đổi node, route, API, timeout hoặc dung lượng queue mà không sửa logic Java.

## 3. Acceptance checklist

- [x] Gateway ID, tốc độ, độ cao, radio range và waypoint nằm ngoài code.
- [x] Danh sách node có ID, model, firmware, farm, vị trí và simulation seed.
- [x] Ingestion URL, timeout, storage, radio loss và retry policy đều cấu hình được.
- [x] Startup từ chối ID, URL, range, capacity hoặc waypoint không hợp lệ.
- [x] Khóa HMAC được sinh ngẫu nhiên tối thiểu 32 byte/node và lưu ngoài Git.
- [x] Khóa được nạp lại ổn định sau restart; getter không trả mảng nội bộ.
- [x] Hỗ trợ file cấu hình khác qua `--config` hoặc `FARM_SIM_CONFIG`.
- [x] `data/`, `.env`, build output và dependency không bị đưa vào source control.

## 4. Đã thực hiện và bằng chứng

| Thành phần | Vai trò |
|---|---|
| `src/farm-sim/config/farm-sim.properties` | Cấu hình mẫu đầy đủ, không chứa secret thật |
| `src/farm-sim/src/farm/config/FarmConfig.java` | Đọc, chuẩn hóa đường dẫn và validate toàn bộ cấu hình |
| `src/farm-sim/src/farm/gateway/KeyStore.java` | Sinh/lưu/nạp khóa ngẫu nhiên 32 byte, defensive copy |
| `src/farm-sim/src/farm/Main.java` | Dựng node, radio, gateway, queue và uploader từ `FarmConfig` |
| `.gitignore` | Loại `data/`, `.env`, `node_modules/`, `dist/`, `out/` |
| `.env.example` | Tên biến quản trị/SMTP chỉ dùng placeholder |
| `src/farm-sim/src/farm/tests/TestRunner.java` | Test cấu hình và KeyStore qua thư mục tạm |

Các nhóm cấu hình hiện có:

```text
gateway.*  backend.*  storage.*  security.*
radio.*    retry.*    nodes.ids  node.NODE-xxx.*
```

File khóa runtime mặc định: `src/farm-sim/data/node-keys.properties`. File này **không được nộp**.

## 5. Quy trình kiểm tra

### Bước 1 — Build và chạy test tự động

```powershell
cd src\farm-sim
.\build.ps1
java -cp out farm.tests.TestRunner
```

Kiểm tra có dòng:

```text
[PASS] FarmConfig load and generated KeyStore persistence
RESULT: 7 PASS, 0 FAIL
```

### Bước 2 — Chạy bằng cấu hình mặc định

```powershell
.\run.ps1 --config config\farm-sim.properties --missions 1 --tick-ms 0 --reset-queue
```

Kiểm tra log `CONFIG`, sáu node `NODE-001..006`, gateway `UAV-GW-01`, queue về `0` khi server hoạt động.

### Bước 3 — Kiểm tra cấu hình ngoài

Sao chép file mẫu ra ngoài repository, đổi `gateway.id` hoặc `backend.ingest-url`, rồi chạy:

```powershell
$env:FARM_SIM_CONFIG = 'C:\config\farm-sim-test.properties'
.\run.ps1 --missions 1
```

### Bước 4 — Kiểm tra negative case

Đặt tạm `storage.capacity=0` hoặc URL không hợp lệ trong bản sao cấu hình. Chương trình phải dừng sớm và chỉ rõ tên property lỗi; không dùng file lỗi đó để commit.

## 6. Kết quả đã xác minh

- Java build target 17 với `-Xlint:all`: **PASS**.
- `TestRunner`: **7 PASS, 0 FAIL**.
- FarmConfig nạp đúng 6 node: **PASS**.
- KeyStore restart giữ nguyên key và getter defensive-copy: **PASS**.
- E2E gateway dùng cấu hình mới: 6/6 record sync, queue `0`, connectivity `ONLINE`.
- Không còn khóa suy ra từ `nodeId` hoặc secret thật trong file cấu hình commit.

## 7. Đóng góp cho nhóm

- **H/backend:** có một ingestion URL, timeout và gateway ID rõ ràng để tích hợp.
- **Sensor/gateway:** thêm hoặc đổi node/route mà không sửa core code.
- **QA:** tạo nhiều testbed cấu hình khác nhau và tái hiện lỗi dễ hơn.
- **Triển khai:** tách non-secret config khỏi secret/runtime state, đáp ứng NFR03/NFR07.

## 8. Phụ thuộc và handoff

- Nhóm H cần xác nhận `backend.ingest-url` và contract `POST /api/ingest` khi thay mock server.
- Người triển khai phải cấp/quản lý file khóa node ở môi trường thật và giữ quyền đọc tối thiểu.
- Khi thay simulator bằng ESP32/Raspberry Pi, giữ nguyên `nodeId`, key provisioning và payload protocol v1.

## 9. Giới hạn trung thực

- Cấu hình hiện điều khiển **simulator Java**, chưa tự provision phần cứng ESP32/Raspberry Pi.
- File key local phù hợp demo/test; production nên dùng secret manager hoặc secure provisioning phù hợp hạ tầng.
- Không nộp `src/farm-sim/data/` vì chứa runtime key, queue, alert và email outbox.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file task này vào `SCRUM-142`.
- [ ] Đính kèm/link `config/farm-sim.properties` và `FarmConfig.java`.
- [ ] Đính kèm log `7 PASS, 0 FAIL` hoặc screenshot terminal.
- [ ] Xác nhận gói nộp không có `data/`, `.env` hay secret thật.
- [ ] Nhờ H hoặc người tích hợp review URL/payload config.
- [ ] Chuyển `Ready for Review`; chỉ chuyển `Done` sau khi reviewer xác nhận.

## 11. Comment Jira có thể copy

```text
Implemented SCRUM-142 / F116 – Configure Gateway and Sensor Nodes.

Evidence:
- config/farm-sim.properties: gateway, nodes, backend, storage, radio and retry settings.
- FarmConfig validates IDs, URL, range, waypoint, timeout and capacity at startup.
- KeyStore generates/persists random 32-byte per-node HMAC keys outside Git.
- Main now builds the gateway and six nodes from external configuration.

Verification:
- Java 17 build: PASS.
- FarmConfig/KeyStore automated test: PASS.
- Full TestRunner: 7 PASS, 0 FAIL.
- E2E: 6/6 records synchronized, queue=0, connectivity=ONLINE.

Handoff: H should confirm the production ingestion URL/contract. Runtime data and node keys are intentionally excluded from submission.
Limitation: hardware provisioning is simulated; production keys require the deployment secret process.
Proposed status: Ready for Review.
```
