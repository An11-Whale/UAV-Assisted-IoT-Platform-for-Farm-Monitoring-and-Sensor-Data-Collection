# SCRUM-143 — F117 Prepare Installation Manual

## 1. Thông tin task

| Thuộc tính | Giá trị |
|---|---|
| Jira | `SCRUM-143` |
| Feature | `F117 – Prepare Installation Manual` |
| Người phụ trách | Quang Lực |
| Board hiện tại | `To Do` |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Deliverable chính | `docs/installation-manual.md` |
| Yêu cầu liên quan | Task package 5, NFR03, NFR05, NFR08, NFR09 |

## 2. Mục tiêu

Cung cấp tài liệu để một thành viên không viết module vẫn có thể chuẩn bị môi trường, build, cấu hình, chạy, kiểm tra online/offline, xử lý lỗi và dừng demo an toàn trên Windows hoặc Linux/macOS.

## 3. Acceptance checklist

- [x] Nêu rõ JDK 17+, Node 20+, npm, browser và port cần dùng.
- [x] Có quy trình Windows một lệnh và quy trình chạy thủ công từng terminal.
- [x] Có hướng dẫn Linux/macOS.
- [x] Có lệnh build Java, cài dashboard, lint và production build.
- [x] Có cách kiểm tra health, latest sensor và history API.
- [x] Có kịch bản offline-first, queue persistence, retry và dead-letter.
- [x] Có hướng dẫn đổi port, backend URL và file cấu hình gateway/node.
- [x] Có hướng dẫn Alert API, admin key và SMTP environment variables.
- [x] Có troubleshooting cho JDK, port, mock data, npm/OneDrive và queue.
- [x] Có security checklist và acceptance checklist cuối tài liệu.

## 4. Đã thực hiện và bằng chứng

| File | Nội dung |
|---|---|
| `docs/installation-manual.md` | Manual 13 phần từ prerequisite đến acceptance |
| `scripts/check-environment.ps1` | Tự tìm JDK 17+ trong `JAVA_HOME`, `.jdks`, `PATH`; kiểm tra Node/npm |
| `scripts/run-demo.bat` / `run-demo.sh` | Chạy demo tích hợp |
| `src/farm-sim/build.ps1` / `run.ps1` | Build/run Windows, fail-fast và truyền exit code đúng |
| `.env.example` | Danh sách biến API/admin/SMTP không chứa credential thật |
| `docs/tasks/sensor-gateway/README.md` | Mục lục và checklist 23 task Sensor/Gateway |

Manual phân biệt rõ:

- demo hiện tại với kiến trúc production mục tiêu;
- dữ liệu source được nộp với runtime data phải loại bỏ;
- `Ready for Review` với `Done` sau walkthrough độc lập.

## 5. Quy trình kiểm tra manual

Nhờ một thành viên khác dùng checkout/gói nộp sạch và chỉ đọc `docs/installation-manual.md`.

### Bước 1 — Kiểm tra môi trường

```powershell
.\scripts\check-environment.ps1
```

Kết quả cần có `[READY]` và phiên bản JDK/Node/npm hợp lệ.

### Bước 2 — Build và test farm-sim

```powershell
cd src\farm-sim
.\build.ps1
java -cp out farm.tests.TestRunner
```

### Bước 3 — Build dashboard

```powershell
cd ..\dashboard
npm ci
npm run lint
npm run build
```

### Bước 4 — Chạy và kiểm tra API

```powershell
cd ..\..
.\scripts\run-demo.bat
```

Mở `http://localhost:5173` và kiểm tra `http://localhost:8080/api/health`.

### Bước 5 — Kiểm tra offline-first

```powershell
cd src\farm-sim
.\run.ps1 --offline-first --missions 2 --tick-ms 0 --reset-queue
```

Ghi lại PASS/FAIL của checklist cuối manual và mọi bước cần chỉnh.

## 6. Kết quả đã xác minh

- Environment checker tìm đúng JDK 27 trong `.jdks`, Node 24 và npm 11: **PASS**.
- Java build 36 source với target Java 17: **PASS**.
- Acceptance tests: **7 PASS, 0 FAIL**.
- Dashboard lint: **PASS**.
- Dashboard production build: **PASS**; chỉ còn warning khuyến nghị code-split bundle.
- Offline-first: 12 record sync, queue `0`, state `ONLINE`: **PASS**.
- Windows/OneDrive smoke test và tự giải phóng port: **PASS**.

## 7. Đóng góp cho nhóm

- Giảm thời gian cài đặt và tránh phụ thuộc người viết code.
- Cho H/NL/QA một quy trình tái hiện cùng môi trường và contract.
- Cung cấp checklist demo/bàn giao cho task package 5.
- Giúp phát hiện sớm thiếu JDK, sai port, sai API URL, secret bị commit hoặc queue bị reset nhầm.

## 8. Phụ thuộc và handoff

- H xác nhận endpoint/backend thật trước khi thay URL mock.
- NL dùng Alert API theo manual khi tích hợp handling/web notification.
- Một thành viên không tham gia viết manual phải hoàn thành walkthrough và ký xác nhận PASS/FAIL.
- Người quản lý repo gắn link commit/PR và log/screenshot vào Jira.

## 9. Giới hạn trung thực

- Manual hiện nghiệm thu development/integration demo, không tự triển khai Spring Boot/PostgreSQL/Nginx hay phần cứng thật.
- Gửi email ra ngoài cần SMTP credential thật; manual chỉ cung cấp cách cấu hình an toàn.
- Trên OneDrive, `npm ci` có thể chậm hoặc gặp `EPERM`; troubleshooting đã nêu cách xử lý.
- SCRUM-143 chỉ nên chuyển `Done` sau khi một người khác thực hiện manual trên bản sạch.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file task này vào `SCRUM-143`.
- [ ] Đính kèm/link `docs/installation-manual.md`.
- [ ] Đính kèm screenshot `[READY]`, `7 PASS, 0 FAIL`, dashboard build/lint.
- [ ] Giao một thành viên khác chạy checklist cuối manual.
- [ ] Ghi rõ limitation simulator/SMTP trong Jira.
- [ ] Chuyển `Ready for Review`; chỉ chuyển `Done` sau walkthrough.

## 11. Comment Jira có thể copy

```text
Implemented SCRUM-143 / F117 – Prepare Installation Manual.

Deliverable:
- docs/installation-manual.md: prerequisites, Windows/Linux setup, configuration, build/run, API verification, offline-first, troubleshooting, security and final acceptance checklist.
- Environment/build/run scripts are linked from the manual.

Verification completed:
- Environment checker: PASS (JDK 27, Node 24, npm 11).
- Java build: PASS; TestRunner: 7 PASS, 0 FAIL.
- Dashboard lint/build: PASS.
- Offline-first E2E: 12 records synchronized, queue=0, connectivity=ONLINE.

Handoff: one team member who did not write the module should follow the manual on a clean checkout and attach the PASS/FAIL checklist.
Limitations: this manual covers the integration demo; real SMTP and physical deployment require target-environment credentials/hardware.
Proposed status: Ready for Review.
```
