# SCRUM-22 – F9 Prepare Sensor/Gateway Development Environment

## 1. Thông tin task

| Mục | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Loại bàn giao | Script môi trường, build/run và cấu hình mẫu |

## 2. Mục tiêu

Chuẩn bị môi trường tái lập để thành viên mới có thể kiểm tra công cụ, build Java farm simulator và chạy dashboard mà không phụ thuộc vào Java 8 cũ đang có trong `PATH`.

## 3. Acceptance checklist

- [x] Kiểm tra được JDK 17+ và bắt buộc có cả `java`/`javac`.
- [x] Tự tìm JDK trong `JAVA_HOME`, `%USERPROFILE%\.jdks` và `PATH`.
- [x] Kiểm tra Node.js 20+ và npm.
- [x] Script build trả exit code thật, không in thành công khi `javac` lỗi.
- [x] Script run tự build khi chưa có output.
- [x] Có `.env.example` không chứa credential thật.
- [x] Bỏ qua dependency, build output và runtime secret trong Git.

## 4. Đã thực hiện

- [`scripts/check-environment.ps1`](../../../scripts/check-environment.ps1): kiểm tra JDK, Node và npm; in hướng dẫn `winget` khi thiếu.
- [`src/farm-sim/build.ps1`](../../../src/farm-sim/build.ps1): compile với `--release 17 -Xlint:all`.
- [`src/farm-sim/run.ps1`](../../../src/farm-sim/run.ps1): tìm đúng JDK và chuyển nguyên tham số tới `farm.Main`.
- `build.bat`, `run.bat`, `scripts/run-demo.bat`: wrapper Windows có fail-fast.
- [`.env.example`](../../../.env.example) và [`.gitignore`](../../../.gitignore).

## 5. Quy trình kiểm tra

```powershell
cd <thu-muc-repository>
.\scripts\check-environment.ps1
cd .\src\farm-sim
.\build.ps1
java -cp out farm.tests.TestRunner
```

Kết quả mong đợi: checker báo `[READY]`, build báo `[OK]`, test kết thúc `7 PASS, 0 FAIL`.

## 6. Kết quả đã xác minh

- Nhận đúng JDK 27 trong `.jdks` dù `PATH` trỏ Java 8.
- Nhận Node.js 24 và npm 11.
- Compile thành công 36 source Java theo target Java 17, không warning.
- Negative path khi giấu JDK/Node trả exit code `1` và hướng dẫn cài đặt.

## 7. Đóng góp cho nhóm

Giúp H, NL và QA dùng cùng một môi trường, giảm lỗi “máy em chạy được” và tạo nền để tái hiện mọi test sensor/gateway/alert.

## 8. Phụ thuộc và handoff

- Thành viên dashboard cần chạy `npm ci` từ `package-lock.json`.
- Thành viên Java chỉ cần JDK 17+; không cần Docker/PostgreSQL cho demo.

## 9. Giới hạn trung thực

- Script không tự cài phần mềm; chỉ kiểm tra và đưa lệnh cài an toàn.
- `npm ci` cần mạng/cache npm và có thể chậm trong OneDrive.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file này vào SCRUM-22.
- [ ] Đính kèm link commit/PR chứa các script.
- [ ] Chụp output `[READY]`, Java build và `7 PASS, 0 FAIL`.
- [ ] Nhờ một thành viên khác chạy checker trên máy của họ.
- [ ] Chuyển task sang Ready for Review; chỉ Done sau khi reviewer xác nhận.

## 11. Comment Jira có thể copy

```text
Implemented F9 development environment: Windows environment checker, JDK auto-discovery, fail-fast build/run scripts, .env.example and Git ignore rules.
Verification: JDK 27 / Node 24 / npm 11 detected; 36 Java sources compiled for Java 17; acceptance suite 7 PASS, 0 FAIL.
Contribution: provides a reproducible setup for sensor, gateway, backend-integration and QA work.
Status proposed: Ready for Review.
```
