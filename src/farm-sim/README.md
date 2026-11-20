# Farm Sensor, Gateway and Alert Simulator

Giả lập toàn bộ chuỗi: cảm biến -> node -> UAV gateway -> API nhận dữ liệu. Chỉ dùng JDK (17+), không cần thư viện ngoài.

## Chạy
    .\build.ps1                       # Windows; tự tìm JDK 17+ trong .jdks/JAVA_HOME/PATH
    .\run.ps1                         # mặc định: 4 chuyến bay, mock server cổng 8080
    .\run.ps1 --keep                  # giữ server để xem API/dashboard
    .\run.ps1 --offline-first         # server tắt ở chuyến 1 -> thử queue + retry
    .\run.ps1 --reset-queue           # chỉ dùng khi chủ động muốn xóa queue/DLQ cũ
    .\run.ps1 --no-server --url http://localhost:8080/api/ingest

Linux/macOS dùng `build.sh` và `java -cp out farm.Main ...`. Cấu hình mặc định: `config/farm-sim.properties`; đổi bằng `--config PATH` hoặc `FARM_SIM_CONFIG`.

## Ticket -> code
| Ticket | Nội dung | File |
|---|---|---|
| SCRUM-62 F36 | Soil Moisture Sensor | sensor/SoilMoistureSensor |
| SCRUM-63 F37 | Temperature & Humidity | sensor/TempHumiditySensor |
| SCRUM-64 F38 | Light, pH, Water Level | sensor/LightSensor, PhSensor, WaterLevelSensor |
| SCRUM-65 F39 | Sensor Data Reading | sensor/Sensor, SimulatedSensor, Reading (lấy 5 mẫu, trung bình, cờ OK/ERROR) |
| SCRUM-66 F40 | Data Packaging | node/PacketBuilder (JSON v1) |
| SCRUM-67 F41 | Node Identification | node/NodeIdentity (NODE-001...) |
| SCRUM-68 F42 | Discovery | SensorNode.beaconJson, RadioMedium.discover |
| SCRUM-69 F43 | Authentication | common/Auth (HMAC-SHA256), gateway/KeyStore, DataCollector.verify (chữ ký, nonce, seq, lệch giờ) |
| SCRUM-70 F44 | UAV Mobile Gateway | gateway/UavGateway |
| SCRUM-71 F45 | Gateway Data Collection | gateway/DataCollector |
| SCRUM-72 F46 | Ingestion API (của bạn H) | server/MockIngestionServer (CHỈ để test, thay bằng Spring Boot thật) |
| SCRUM-73 F47 | Validation (của bạn H) | server/SensorDataValidator |
| SCRUM-74 F48 | Local Data Storage | gateway/LocalStore (JSONL bền vững), gateway/Uploader (store-and-forward) |
| SCRUM-76 F50 | Offline Data Queue | gateway/LocalStore + dead-letter; FIFO/batch/ack, không xóa trước response hợp lệ |
| SCRUM-77 F51 | Connectivity Detection | gateway/ConnectivityMonitor (`UNKNOWN/ONLINE/DEGRADED/OFFLINE`) |
| SCRUM-78 F52 | Automatic Retry | gateway/RetryPolicy + `Uploader.syncIfDue()` (backoff có max+jitter) |
| SCRUM-112 F86 | Threshold Configuration | alert/AlertPolicy + `/api/v1/config/alert-rules` |
| SCRUM-113..116 F87..F90 | Timeout, pin, bất thường, alert | alert/AlertEngine, AlertRepository; dedupe + audit + lifecycle |
| SCRUM-119 F93 | Email Notifications | alert/EmailNotifier; SMTPS + durable outbox/retry |
| SCRUM-142 F116 | Gateway/Node Configuration | config/FarmConfig + `config/farm-sim.properties`; key random ngoài Git |

## Định dạng gói từ node (F40)
    {"v":1,"nodeId":"NODE-001","seq":7,"nonce":"9f3a..","ts":"2026-11-11T23:00:03Z","battery":99.9,
     "readings":[{"sensor":"soil_moisture","value":55.20,"unit":"%","raw":2117,"status":"OK"}, ...],
     "sig":"<base64 HMAC-SHA256 của phần body trước ,\"sig\">"}

## Hợp đồng API (gateway -> backend)
POST /api/ingest
    {"gatewayId":"UAV-GW-01","batchId":"UAV-GW-01-1","sentAt":"..","records":[
       {"gatewayId":"UAV-GW-01","rxTs":"..","pos":{"lat":10.7774,"lon":106.7025,"alt":30},"rssi":-72.5,"packet":{...gói ở trên...}} ]}
Phản hồi 202: {"batchId":"..","accepted":6,"duplicates":0,"rejected":[{"index":2,"errors":["..."]}]}
Server chống trùng theo (nodeId, seq) nên gateway gửi lại khi mạng lỗi cũng an toàn.

GET /api/readings?nodeId=&sensor=&limit=  ->  [{"nodeId","sensor","value","unit","ts","lat","lon"}]  (cho dashboard)

GET   /api/v1/config/alert-rules
PATCH /api/v1/config/alert-rules              (cần `X-Admin-Key` = `FARM_ADMIN_KEY`)
GET   /api/v1/alerts?status=OPEN&limit=100
GET   /api/v1/alerts/{id}
PATCH /api/v1/alerts/{id}/status              (cần key; body: status/note)

## Khoảng giá trị hợp lệ (SensorType)
soil_moisture 0-100 %, temperature -40..80 C, humidity 0-100 %, light 0-120000 lux, ph 0-14, water_level 0-100 %

## Node "lạ" để thử bảo mật
NODE-098 (chưa cấp phép) và NODE-099 (ký sai khóa) luôn bị gateway từ chối và ghi log REJECT.

## Chuyển sang phần cứng thật
- Cảm biến: viết class implements `Sensor` đọc từ ESP32/Arduino thay cho lớp giả lập.
- Đường truyền: thay `RadioMedium` bằng UDP/MQTT/LoRa; format gói giữ nguyên.

## Tích hợp với dashboard (src/dashboard)
`MockIngestionServer` còn phục vụ API đúng định dạng dashboard React (xem `server/DashboardApi`):

    GET /api/v1/sensors/latest                                             -> SensorNode[]
    GET /api/v1/sensors/{nodeId}/history?metric=soilMoisture&range=24h|7d  -> [{timestamp,value}]

Quy ước chuyển đổi dữ liệu node -> dashboard:
| Node (farm-sim) | Dashboard |
|---|---|
| soil_moisture / temperature / humidity / light / ph / water_level | soilMoisture / airTemperature / airHumidity / lightIntensity / ph / waterLevel |
| temperature `C`, light `lux` | `°C`, `Lux` |
| water_level `%` | `cm` = % x 0.35 (giả định bể sâu 35 cm) |
| NODE-001..003 / NODE-004..006 | zone-01 "Khu dâu tây A" / zone-02 "Khu cà phê đồi" |
| trạng thái | NO_DATA, OFFLINE, WARNING, NORMAL; timeout/ngưỡng/pin đọc từ `AlertPolicy`, không hardcode trong dashboard API |

Ngưỡng dùng chung với `METRIC_CONFIGS` trong `dashboard/src/App.tsx`; riêng ánh sáng chỉ xét ngưỡng dưới trong 06:00-18:00 (ICT).

Chạy cả hai: `sh scripts/run-demo.sh` (hoặc `scripts\run-demo.bat`) ở thư mục gốc repo, rồi mở http://localhost:5173.
Dashboard đọc `VITE_API_BASE_URL` (mặc định `http://localhost:8080/api/v1`); khi backend tắt, dashboard tự dùng dữ liệu mẫu và hiện nhãn "Dữ liệu mẫu".
Khi có backend Spring Boot thật, gateway gửi tới `POST /api/ingest` (`--no-server --url ...`) và backend cần cung cấp hai endpoint `/api/v1/sensors/...` ở trên.

## Kiểm thử acceptance

    .\build.ps1
    java -cp out farm.tests.TestRunner

Kết quả chuẩn hiện tại: `7 PASS, 0 FAIL`. Bài E2E `--offline-first --missions 2 --tick-ms 0 --reset-queue` phải kết thúc với 12 record sync, queue 0 và connectivity `ONLINE`.
