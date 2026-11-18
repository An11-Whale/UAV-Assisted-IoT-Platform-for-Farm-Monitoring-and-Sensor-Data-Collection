# Hồ sơ nộp thủ công — 23 task Sensor/Gateway

Thư mục này chứa **một file độc lập cho mỗi task**. Bạn có thể mở đúng file, đính kèm vào Jira hoặc gửi cho nhóm mà không cần cắt nội dung từ báo cáo tổng.

## Cách dùng bảng theo dõi

Sau mỗi bước, đổi ô tương ứng từ `[ ]` thành `[x]`:

- **Đã nộp:** file task, source/PR và bằng chứng test đã gửi cho nhóm hoặc gắn vào Jira.
- **Đã review:** đúng người phụ thuộc đã xác nhận, ví dụ backend owner review ingestion hoặc frontend owner review Alert API.
- **Done:** acceptance checklist đã pass và board đã được chuyển `Done`.

Hai task `SCRUM-19` và `SCRUM-29` đang `Done` trên board cũ, nhưng vẫn nên review artifact mới trước khi đánh dấu ô Done trong bảng này.

## A. Môi trường và thiết kế

| Task | Hồ sơ độc lập | Đã nộp | Đã review | Done |
|---|---|:---:|:---:|:---:|
| SCRUM-22 — F9 Prepare Sensor/Gateway Development Environment | [Mở file](SCRUM-22-F9-prepare-sensor-gateway-development-environment.md) | [ ] | [ ] | [ ] |
| SCRUM-19 — F20 Create Deployment Diagram | [Mở file](SCRUM-19-F20-create-deployment-diagram.md) | [ ] | [ ] | [ ] |
| SCRUM-29 — F23 Design Sensor–Gateway Communication Protocol | [Mở file](SCRUM-29-F23-design-sensor-gateway-communication-protocol.md) | [ ] | [ ] | [ ] |

## B. Sensor node và UAV gateway

| Task | Hồ sơ độc lập | Đã nộp | Đã review | Done |
|---|---|:---:|:---:|:---:|
| SCRUM-64 — F38 Build Light, pH and Water Level Sensor Prototype | [Mở file](SCRUM-64-F38-build-light-ph-water-level-sensor-prototype.md) | [ ] | [ ] | [ ] |
| SCRUM-65 — F39 Implement Sensor Data Reading | [Mở file](SCRUM-65-F39-implement-sensor-data-reading.md) | [ ] | [ ] | [ ] |
| SCRUM-66 — F40 Implement Sensor Data Packaging | [Mở file](SCRUM-66-F40-implement-sensor-data-packaging.md) | [ ] | [ ] | [ ] |
| SCRUM-67 — F41 Implement Sensor Node Identification | [Mở file](SCRUM-67-F41-implement-sensor-node-identification.md) | [ ] | [ ] | [ ] |
| SCRUM-68 — F42 Implement Sensor Discovery Mechanism | [Mở file](SCRUM-68-F42-implement-sensor-discovery-mechanism.md) | [ ] | [ ] | [ ] |
| SCRUM-69 — F43 Implement Sensor Authentication | [Mở file](SCRUM-69-F43-implement-sensor-authentication.md) | [ ] | [ ] | [ ] |
| SCRUM-70 — F44 Build UAV Mobile Gateway Prototype | [Mở file](SCRUM-70-F44-build-uav-mobile-gateway-prototype.md) | [ ] | [ ] | [ ] |
| SCRUM-71 — F45 Implement Gateway Data Collection | [Mở file](SCRUM-71-F45-implement-gateway-data-collection.md) | [ ] | [ ] | [ ] |
| SCRUM-74 — F48 Implement Local Data Storage | [Mở file](SCRUM-74-F48-implement-local-data-storage.md) | [ ] | [ ] | [ ] |

## C. Offline queue, connectivity và retry

| Task | Hồ sơ độc lập | Đã nộp | Đã review | Done |
|---|---|:---:|:---:|:---:|
| SCRUM-76 — F50 Implement Offline Data Queue | [Mở file](SCRUM-76-F50-implement-offline-data-queue.md) | [ ] | [ ] | [ ] |
| SCRUM-77 — F51 Implement Internet Connectivity Detection | [Mở file](SCRUM-77-F51-implement-internet-connectivity-detection.md) | [ ] | [ ] | [ ] |
| SCRUM-78 — F52 Implement Automatic Retry Mechanism | [Mở file](SCRUM-78-F52-implement-automatic-retry-mechanism.md) | [ ] | [ ] | [ ] |

## D. Threshold, detection, alert và email

| Task | Hồ sơ độc lập | Đã nộp | Đã review | Done |
|---|---|:---:|:---:|:---:|
| SCRUM-112 — F86 Implement Sensor Threshold Configuration | [Mở file](SCRUM-112-F86-implement-sensor-threshold-configuration.md) | [ ] | [ ] | [ ] |
| SCRUM-113 — F87 Implement Data Timeout Detection | [Mở file](SCRUM-113-F87-implement-data-timeout-detection.md) | [ ] | [ ] | [ ] |
| SCRUM-114 — F88 Implement Low Battery Detection | [Mở file](SCRUM-114-F88-implement-low-battery-detection.md) | [ ] | [ ] | [ ] |
| SCRUM-115 — F89 Implement Abnormal Sensor Data Detection | [Mở file](SCRUM-115-F89-implement-abnormal-sensor-data-detection.md) | [ ] | [ ] | [ ] |
| SCRUM-116 — F90 Implement Alert Creation and Management | [Mở file](SCRUM-116-F90-implement-alert-creation-management.md) | [ ] | [ ] | [ ] |
| SCRUM-119 — F93 Implement Email Notifications | [Mở file](SCRUM-119-F93-implement-email-notifications.md) | [ ] | [ ] | [ ] |

## E. Cấu hình, cài đặt và bàn giao

| Task | Hồ sơ độc lập | Đã nộp | Đã review | Done |
|---|---|:---:|:---:|:---:|
| SCRUM-142 — F116 Configure Gateway and Sensor Nodes | [Mở file](SCRUM-142-F116-configure-gateway-sensor-nodes.md) | [ ] | [ ] | [ ] |
| SCRUM-143 — F117 Prepare Installation Manual | [Mở file](SCRUM-143-F117-prepare-installation-manual.md) | [ ] | [ ] | [ ] |

## Quy trình nộp thủ công cho từng task

1. Mở file task tương ứng và kiểm tra toàn bộ acceptance checklist.
2. Chạy lệnh test trong mục **Quy trình kiểm tra** của file đó.
3. Chụp kết quả terminal hoặc lưu log ngắn làm bằng chứng.
4. Đính kèm file Markdown vào Jira hoặc gửi file cho nhóm.
5. Copy nguyên khối **Comment Jira có thể copy** ở cuối file vào issue.
6. Gắn link source/commit/PR hoặc đường dẫn ZIP sạch.
7. Chuyển task sang `Ready for Review`.
8. Sau khi đúng reviewer xác nhận, đánh dấu `Đã review`, chuyển `Done` và cập nhật bảng này.

## Bằng chứng dùng chung đã đạt

```text
Environment checker: PASS
Java 17 build (-Xlint:all): PASS
TestRunner: 7 PASS, 0 FAIL
Dashboard lint: PASS
Dashboard production build: PASS
Offline-first E2E: 12 records synchronized, queue=0, ONLINE
Security: unknown node và bad-HMAC node đều bị reject
Alert API: GET/PATCH/auth/audit PASS
```

Mỗi file task chỉ trích phần bằng chứng liên quan trực tiếp; không cần đính kèm toàn bộ log dài cho mọi issue.

## Nội dung được nộp và nội dung phải loại bỏ

Nộp source, cấu hình mẫu, test và tài liệu trong project. **Không nộp** các thư mục/file sinh tự động hoặc chứa runtime secret:

```text
node_modules/
dist/
out/
data/
.env
.idea/
```

Phải giữ `.env.example`, `config/farm-sim.properties`, source Java/TypeScript, `package-lock.json`, scripts, test và `docs/`.

## Thứ tự reviewer nên kiểm tra

1. **H:** F40, F45, F48, F50–F52, F86–F90 và contract ingestion/validation/deduplication.
2. **NL:** F90/F93 và Alert API làm đầu vào cho handling/closing/web notification.
3. **QA hoặc thành viên khác:** F9 và F117 trên checkout/gói ZIP sạch.
4. **Nhóm hardware/gateway:** F38–F44; xác nhận simulator là acceptance hiện tại hoặc lên lịch test ESP32/Raspberry Pi riêng.

## Lưu ý khi chuyển `Done`

- F38/F44 hiện là prototype mô phỏng, không được mô tả thành phần cứng thật.
- F93 đã pass durable outbox/retry; gửi email ra ngoài cần SMTP credential thật.
- F117 cần một thành viên khác thực hiện walkthrough manual.
- Không chuyển hàng loạt 23 task sang `Done` chỉ vì file tồn tại; cần reviewer theo dependency xác nhận.
