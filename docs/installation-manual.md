# Hướng dẫn cài đặt và chạy UAV–IoT Farm Demo

## 1. Mục đích

Tài liệu này phục vụ **SCRUM-143 / F117 – Prepare Installation Manual**. Hướng dẫn ưu tiên Windows, sau đó là Linux/macOS. Nó cài và chạy môi trường demo có sẵn:

- Java farm simulator: sensor node → UAV gateway → local queue → mock ingestion API;
- React/Vite dashboard;
- kiểm tra online và store-and-forward khi API ban đầu chưa sẵn sàng.

Đây là **development/integration demo**, chưa phải production package. Spring Boot, PostgreSQL, Nginx, MQTT/LoRa và phần cứng ESP32/Raspberry Pi là kiến trúc mục tiêu, không được cài tự động bởi repository hiện tại.

## 2. Cấu trúc liên quan

```text
UAV-IoT-Farm-integrated/
├── scripts/
│   ├── run-demo.bat
│   └── run-demo.sh
├── src/
│   ├── farm-sim/
│   │   ├── build.bat / build.sh
│   │   ├── run.bat
│   │   └── src/
│   └── dashboard/
│       ├── package.json
│       └── package-lock.json
└── docs/
```

Các lệnh bên dưới giả định terminal đang ở thư mục `UAV-IoT-Farm-integrated` chứa `README.md`, `scripts` và `src`.

## 3. Yêu cầu hệ thống

### Bắt buộc cho demo

| Thành phần | Phiên bản | Cách kiểm tra |
|---|---:|---|
| JDK | 17 trở lên | `java -version` và `javac -version` |
| Node.js | 20 trở lên | `node --version` |
| npm | Đi kèm Node | `npm --version` |
| Trình duyệt | Chrome, Edge hoặc Firefox | Mở dashboard local |
| Port local | 8080 và 5173 mặc định | Không bị ứng dụng khác chiếm |

Không cần PostgreSQL, Docker, Python hay broker MQTT để chạy demo hiện tại.

### Khuyến nghị

- Windows 10/11 và PowerShell 5.1+;
- tối thiểu 2 GB dung lượng trống cho JDK, Node modules và build output;
- Git nếu lấy source từ repository thay vì file nén.

## 4. Cài đặt nhanh trên Windows

### Bước 1 – Mở PowerShell tại repository

Trong File Explorer, mở thư mục `UAV-IoT-Farm-integrated`, nhấp thanh địa chỉ, gõ `powershell` rồi Enter. Kiểm tra đúng vị trí:

```powershell
Get-ChildItem README.md, scripts, src
```

### Bước 2 – Kiểm tra công cụ

```powershell
.\scripts\check-environment.ps1
```

Checker tìm JDK 17+ trong `JAVA_HOME`, `%USERPROFILE%\.jdks` và `PATH`, rồi kiểm tra Node 20+/npm. Vì vậy máy có Java 8 cũ trong `PATH` nhưng có JDK mới do IntelliJ tải vẫn chạy được. Nếu checker báo lỗi, dùng lệnh `winget` mà script in ra, mở terminal mới và chạy lại.

### Bước 3 – Chạy demo một lệnh

```powershell
.\scripts\run-demo.bat
```

Script sẽ:

1. build Java farm simulator;
2. mở simulator/mock API ở port `8080` trong cửa sổ riêng;
3. cài dependency dashboard nếu chưa có `node_modules`;
4. đợi dữ liệu mô phỏng được tạo;
5. chạy Vite dashboard ở `http://localhost:5173`.

Mở [http://localhost:5173](http://localhost:5173). Khi hoàn tất, nhấn `Ctrl+C` tại cửa sổ dashboard và tại cửa sổ farm-sim (hoặc đóng cả hai cửa sổ).

## 5. Cài và chạy thủ công trên Windows

Cách này dễ chẩn đoán hơn vì xem được log từng thành phần.

### Terminal 1 – Build và chạy sensor/gateway/backend mock

```powershell
Set-Location .\src\farm-sim
.\build.ps1
.\run.ps1 --missions 24 --tick-ms 5 --keep
```

`build.bat`/`run.bat` là wrapper tương đương nếu bạn dùng Command Prompt.

Expected output:

- log `MISSION`, `COLLECT`, `REJECT`, `UPLOAD`;
- `NODE-098` bị từ chối vì chưa provision;
- `NODE-099` bị từ chối vì ký sai key;
- phần tổng kết có số collected/uploaded và queue;
- dòng `Dashboard API` và `Alert API` khi dùng `--keep`.

Giữ terminal này mở.

### Terminal 2 – Cài và chạy dashboard

```powershell
Set-Location .\src\dashboard
npm ci
$env:VITE_API_BASE_URL = 'http://localhost:8080/api/v1'
npm run dev
```

`npm ci` dùng chính xác version trong `package-lock.json`, phù hợp máy sạch và CI. Sau khi Vite báo URL, mở [http://localhost:5173](http://localhost:5173).

Biến `$env:VITE_API_BASE_URL` chỉ có hiệu lực trong terminal hiện tại và không chứa secret. Nếu không đặt, dashboard mặc định dùng `http://localhost:8080/api/v1`.

### Build dashboard để kiểm tra release bundle

```powershell
Set-Location .\src\dashboard
npm ci
npm run lint
npm run build
```

Build output được tạo trong `src/dashboard/dist`.

## 6. Xác minh hệ thống

Mở PowerShell thứ ba:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/health'
```

Kết quả mong đợi:

```text
status
------
UP
```

Đọc trạng thái node:

```powershell
$nodes = Invoke-RestMethod 'http://localhost:8080/api/v1/sensors/latest'
$nodes | Select-Object id, zoneName, status, batteryPct, lastMeasurementAt, lastSyncAt
```

Đọc lịch sử một metric:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/v1/sensors/NODE-001/history?metric=soilMoisture&range=24h'
```

Checklist UI:

- dashboard không hiện nhãn “Dữ liệu mẫu” khi API hoạt động;
- có các node `NODE-001` đến `NODE-006`;
- hiển thị battery, measurement time và sync time;
- đổi sensor/metric/range hiển thị dữ liệu lịch sử;
- status `WARNING/OFFLINE/NO_DATA` có cách thể hiện rõ.

## 7. Kiểm thử mất kết nối và store-and-forward

Dừng demo đang chạy, sau đó tại `src/farm-sim`:

```powershell
.\build.ps1
.\run.ps1 --offline-first --missions 4 --tick-ms 5 --keep
```

Hành vi mong đợi:

1. Mission 1 chạy khi mock API chưa khởi động.
2. Upload báo không kết nối được và các record vẫn nằm trong queue.
3. Mock API khởi động trước mission 2.
4. Lần upload tiếp theo gửi cả dữ liệu pending và dữ liệu mới.
5. Tổng kết cho thấy queue còn lại bằng `0` nếu mọi batch được nhận.

File queue demo nằm tại:

```text
src/farm-sim/data/gateway-buffer.jsonl
```

Queue **không bị xóa khi restart**. Muốn bắt đầu một kịch bản sạch, phải yêu cầu rõ bằng cờ:

```powershell
.\run.ps1 --reset-queue --missions 1
```

Record bị backend từ chối được giữ riêng tại `data/gateway-dead-letter.jsonl`; raw record và lý do lỗi không bị mất.

## 8. Dùng backend thật hoặc đổi port

### Gửi gateway tới backend thật

Backend phải hỗ trợ contract trong [Sensor–Gateway Protocol](protocol/sensor-gateway-protocol.md), đặc biệt `POST /api/ingest` và deduplication `(nodeId, seq)`.

```powershell
Set-Location .\src\farm-sim
.\build.bat
.\run.bat --no-server --url 'https://api.example.test/api/ingest' --missions 4
```

Không dùng URL production thật nếu chưa có quyền và chưa cấu hình TLS/credential phù hợp.

### Đổi port mock API

Terminal farm-sim:

```powershell
.\run.bat --port 8081 --missions 24 --tick-ms 5 --keep
```

Terminal dashboard:

```powershell
$env:VITE_API_BASE_URL = 'http://localhost:8081/api/v1'
npm run dev
```

### Tham số farm-sim

| Tham số | Mặc định | Ý nghĩa |
|---|---:|---|
| `--missions N` | 4 | Số chuyến thu thập mô phỏng |
| `--tick-ms N` | 20 | Thời gian chờ thật giữa mỗi simulation tick |
| `--port N` | 8080 | Port mock API |
| `--url URL` | `http://localhost:{port}/api/ingest` | Ingestion endpoint |
| `--no-server` | false | Không khởi động mock server |
| `--offline-first` | false | Chỉ khởi động mock server từ mission 2 |
| `--keep` | false | Giữ mock server sống sau các mission |

## 9. Linux/macOS

Tại repository root:

```bash
java -version
javac -version
node --version
npm --version
sh scripts/run-demo.sh
```

Hoặc chạy thủ công.

Terminal 1:

```bash
cd src/farm-sim
sh build.sh
java -cp out farm.Main --missions 24 --tick-ms 5 --keep
```

Terminal 2:

```bash
cd src/dashboard
npm ci
VITE_API_BASE_URL=http://localhost:8080/api/v1 npm run dev
```

Kiểm tra health:

```bash
curl http://localhost:8080/api/health
```

## 10. Cấu hình gateway, node, alert và email

### Gateway và sensor node

File mặc định là `src/farm-sim/config/farm-sim.properties`. Có thể cấu hình:

- gateway ID, speed, altitude, radio range và waypoints;
- ingestion URL, connect/request timeout;
- queue/dead-letter path và capacity;
- packet-loss simulation và seed;
- retry initial/max delay, multiplier, jitter;
- danh sách node, model, firmware, farm, vị trí và simulation seed.

Chạy bằng file khác:

```powershell
$env:FARM_SIM_CONFIG = 'C:\config\farm-prod.properties'
# hoặc
.\run.ps1 --config 'C:\config\farm-prod.properties'
```

Ứng dụng validate ID/range/URL/capacity ngay khi start. Mỗi node được sinh khóa HMAC ngẫu nhiên 32 byte vào `src/farm-sim/data/node-keys.properties`; file `data/` đã bị `.gitignore` loại khỏi source control. Sao lưu file này an toàn nếu cần giữ cùng identity qua máy khác.

### Alert policy API

Read policy không cần key trong local demo. Mutation bị khóa an toàn nếu chưa đặt `FARM_ADMIN_KEY`:

```powershell
$env:FARM_ADMIN_KEY = 'generate-a-long-random-value'
$headers = @{ 'X-Admin-Key'=$env:FARM_ADMIN_KEY; 'X-Actor'='LQ' }
$body = '{"metric":"temperature","min":18,"max":32,"nodeId":"NODE-001"}'
Invoke-RestMethod -Method Patch -Uri http://localhost:8080/api/v1/config/alert-rules `
  -Headers $headers -ContentType application/json -Body $body
Invoke-RestMethod http://localhost:8080/api/v1/alerts
```

Policy, audit và alert event store nằm trong `data/` và sống qua restart.

### Email

Xem tên biến đầy đủ trong `.env.example`. Java không tự nạp file `.env`; hãy đặt biến môi trường trước khi chạy:

```powershell
$env:ALERT_EMAIL_TO = 'admin@example.com'
$env:ALERT_EMAIL_FROM = 'alerts@example.com'
$env:SMTP_HOST = 'smtp.example.com'
$env:SMTP_PORT = '465'
$env:SMTP_SSL = 'true'
$env:SMTP_USERNAME = 'smtp-user'
$env:SMTP_PASSWORD = 'app-password-from-secret-store'
```

Nếu SMTP tạm lỗi, alert vẫn được lưu và mail ở `data/email-outbox.jsonl` để retry; không log password và không rollback alert.

## 11. Xử lý lỗi thường gặp

### `javac` is not recognized

- Cài JDK 17+ thay vì chỉ JRE.
- Thêm thư mục `bin` của JDK vào `PATH`.
- Đóng và mở lại PowerShell, chạy `javac -version`.

### Port 8080 bị chiếm

Kiểm tra process:

```powershell
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
```

Dừng đúng ứng dụng đang dùng port hoặc chạy farm-sim với `--port 8081` rồi đổi `VITE_API_BASE_URL` tương ứng. Không kill process lạ khi chưa xác định chủ sở hữu.

### Port 5173 bị chiếm

Vite thường đề xuất port khác. Mở đúng URL Vite in ra; API base vẫn là port 8080 trừ khi đã đổi mock server.

### Dashboard hiện “Dữ liệu mẫu”

- Kiểm tra `http://localhost:8080/api/health`.
- Xác nhận terminal farm-sim vẫn chạy với `--keep`.
- Kiểm tra `VITE_API_BASE_URL` có kết thúc bằng `/api/v1`.
- Mở browser developer console để xem network/CORS error.

### `npm ci` lỗi

- Xác nhận Node 20+ và có `package-lock.json`.
- Không sửa `package-lock.json` thủ công.
- Nếu lỗi tải package do proxy/network, cấu hình npm theo quy định mạng của nhóm rồi chạy lại.
- Nếu gặp `EPERM/ENOTEMPTY` trong thư mục OneDrive, đóng Vite/VS Code đang giữ file; tạm dừng đồng bộ hoặc đặt checkout ngoài OneDrive, xóa **chỉ** `src/dashboard/node_modules`, rồi chạy lại `npm ci`. Không xóa source hay `package-lock.json`.

### `NODE-098` và `NODE-099` bị REJECT

Đây là hành vi mong đợi của security scenario: node 098 chưa provision, node 099 dùng key sai.

### Queue không còn sau khi restart demo

Không dùng `--reset-queue`; kiểm tra đang chạy đúng file config và `storage.queue-file`. Queue mặc định phải còn sau restart. Chỉ cờ `--reset-queue` mới chủ động xóa queue/dead-letter.

## 12. Security checklist trước khi triển khai thật

- Giữ file khóa ngẫu nhiên 32 byte/node ngoài Git; không thay bằng khóa suy ra từ node ID.
- Không commit `.env`, API key, SMTP password, DB URL có password hoặc private certificate.
- Gateway → backend qua Internet phải dùng HTTPS và xác minh certificate.
- MQTT public/LAN không tin cậy phải dùng TLS/ACL; mỗi node chỉ được publish/subscribe topic được cấp.
- PostgreSQL chỉ nằm trong private network; không mở `5432` công khai.
- Chạy service bằng tài khoản ít quyền, giới hạn quyền đọc secret và local queue.
- Thiết lập backup/recovery cho database và storage capacity policy cho gateway.
- Log authentication failure và retry đủ để chẩn đoán nhưng không log secret.

## 13. Checklist nghiệm thu cài đặt

Nhờ một thành viên không viết module thực hiện trên checkout/máy sạch và ghi PASS/FAIL:

- [ ] JDK/Node/npm đúng version.
- [ ] `build.bat` hoặc `build.sh` hoàn thành không lỗi.
- [ ] `npm ci`, lint và build dashboard pass.
- [ ] Health endpoint trả `UP`.
- [ ] Six registered nodes có latest data.
- [ ] Unknown/bad-key nodes bị reject.
- [ ] Dashboard dùng API thật và xem được history.
- [ ] Offline-first giữ queue rồi upload hết khi API trở lại.
- [ ] Gửi lại không sinh duplicate record ở backend.
- [ ] Người kiểm thử dừng được tất cả process và hiểu cách đổi port.
- [ ] Không có secret thật trong source/log.

Chỉ chuyển SCRUM-143 sang `Done` sau khi checklist trên được một người khác thực hiện và bằng chứng được đính kèm ticket.
