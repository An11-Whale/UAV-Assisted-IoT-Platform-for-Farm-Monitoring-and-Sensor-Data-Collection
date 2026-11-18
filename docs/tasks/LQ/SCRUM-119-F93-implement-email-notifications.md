# SCRUM-119 — F93 Implement Email Notifications (F10)

## 1. Thông tin task

| Thuộc tính | Giá trị |
|---|---|
| Người phụ trách | LQ |
| Trạng thái trên board trong ảnh | To Do |
| Trạng thái đề xuất khi nộp | **Ready for Review** |
| Yêu cầu liên quan | FR F10, NFR03, NFR04 |

## 2. Mục tiêu

Khi alert mới được tạo và email đã cấu hình, tạo nội dung đủ ngữ cảnh, lưu mail vào outbox bền vững rồi gửi qua SMTP. Lỗi email không được rollback alert; mail phải được giữ để retry có backoff.

## 3. Acceptance checklist

- [x] Email template có alert ID, node, type, metric, message và thời gian.
- [x] Không hardcode SMTP credential hoặc người nhận trong source.
- [x] Cấu hình bằng environment variables.
- [x] Outbox JSONL sống qua restart.
- [x] Alert được lưu trước; lỗi SMTP không làm mất/rollback alert.
- [x] SMTP failure tăng attempt và đặt thời điểm retry có giới hạn.
- [x] Thành công xóa mail khỏi outbox.
- [x] Khi chưa cấu hình người nhận, hệ thống log rõ và alert vẫn hoạt động.

## 4. Đã thực hiện

| File/module | Nội dung |
|---|---|
| `src/farm-sim/src/farm/alert/EmailNotifier.java` | Template, durable outbox, SMTPS/plain socket, AUTH LOGIN, retry và persistence. |
| `src/farm-sim/src/farm/alert/AlertEngine.java` | Enqueue email chỉ sau khi alert mới được tạo; bắt lỗi để không chặn detector. |
| `src/farm-sim/src/farm/server/MockIngestionServer.java` | Khởi tạo notifier từ environment và flush khi dừng server. |
| `.env.example` | `SMTP_HOST/PORT/USERNAME/PASSWORD/SSL`, `ALERT_EMAIL_FROM/TO` dưới dạng placeholder. |
| `src/farm-sim/src/farm/tests/TestRunner.java` | Test outbox bền vững khi có recipient nhưng SMTP chưa cấu hình. |

## 5. Quy trình kiểm tra

### Kiểm tra outbox an toàn, không gửi ra ngoài

```powershell
cd src\farm-sim
.\build.ps1
java -cp out farm.tests.TestRunner
```

Tìm dòng:

```text
[PASS] EmailNotifier durable outbox without SMTP
```

### Cấu hình SMTP thật khi nhóm cung cấp account test

```powershell
$env:ALERT_EMAIL_TO = 'farm-admin@example.com'
$env:ALERT_EMAIL_FROM = 'alerts@example.com'
$env:SMTP_HOST = 'smtp.example.com'
$env:SMTP_PORT = '465'
$env:SMTP_SSL = 'true'
$env:SMTP_USERNAME = 'smtp-user'
$env:SMTP_PASSWORD = '<app-password-from-secret-store>'
.\run.ps1 --missions 1 --keep
```

Tạo alert rồi kiểm tra inbox hoặc `data/email-outbox.jsonl` nếu gửi lỗi. Không chép password vào Jira/log.

## 6. Kết quả đã xác minh

- Durable outbox without SMTP: PASS.
- Alert engine vẫn tạo/lưu alert khi email chưa cấu hình hoặc gửi lỗi.
- Retry metadata được lưu và đọc lại.
- Tổng TestRunner: **7 PASS, 0 FAIL**.
- **Chưa gửi thử tới SMTP thật** vì workspace không có SMTP credential được phê duyệt.

## 7. Đóng góp cho nhóm

- Cung cấp kênh thông báo ngoài Web để quản trị viên phản ứng nhanh.
- Tách lỗi notification khỏi transaction tạo alert, giúp hệ thống ổn định.
- Cho nhóm triển khai một contract environment rõ ràng, không lộ secret.

## 8. Phụ thuộc và handoff

- F90 phải tạo alert trước khi notifier enqueue.
- Nhóm triển khai cần cấp SMTP test account/app password qua secret store.
- NL có thể hiển thị trạng thái notification/outbox nếu cần trong UI sau này.

## 9. Giới hạn trung thực

- Acceptance hiện xác nhận template/outbox/retry, chưa xác nhận delivery qua SMTP thật.
- SMTP plain chỉ phù hợp môi trường test tin cậy; public deployment phải dùng TLS/SMTPS.
- Prototype chưa có nhiều recipient group/template HTML hay provider API chuyên dụng.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file task vào SCRUM-119.
- [ ] Đính kèm `EmailNotifier`, `AlertEngine` và `.env.example`.
- [ ] Dán kết quả outbox PASS; không dán credential.
- [ ] Ghi rõ “SMTP thật chưa test vì thiếu credential”.
- [ ] Nhờ nhóm triển khai cấp account test và chạy delivery smoke test.
- [ ] Chuyển Done sau khi nhóm chấp nhận outbox test hoặc xác nhận email thật.

## 11. Comment Jira để copy

```text
Implemented SCRUM-119 / F93 Email Notifications.
- Alert email template with alert ID/node/type/metric/message/time.
- Durable JSONL outbox; SMTP failure does not roll back alerts and pending mail is retried with bounded backoff.
- SMTP host/port/SSL/user/password/from/to are external environment variables; no real secret is committed.
Verification: EmailNotifier durable outbox without SMTP PASS; full TestRunner 7 PASS/0 FAIL.
Honest limitation: real SMTP delivery has not been tested because no approved SMTP credential is available. A team SMTP smoke test is still required before final Done if the acceptance criteria require external delivery.
Proposed status: Ready for Review.
```
