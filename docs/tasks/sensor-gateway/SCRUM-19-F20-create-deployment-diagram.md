# SCRUM-19 – F20 Create Deployment Diagram

## 1. Thông tin task

| Mục | Giá trị |
|---|---|
| Người phụ trách | Quang Lực |
| Trạng thái trên board | Done |
| Trạng thái đề xuất khi nộp | **Done – cần nhóm xác nhận artifact mới** |
| Loại bàn giao | UML-style Deployment Diagram + mô tả triển khai |

## 2. Mục tiêu

Thể hiện rõ phần mềm/artifact chạy ở đâu, các thiết bị giao tiếp bằng protocol/port nào, đồng thời phân biệt demo hiện có với kiến trúc triển khai mục tiêu.

## 3. Acceptance checklist

- [x] Có Sensor Node/ESP32, UAV, Mobile Gateway/Raspberry Pi và local storage.
- [x] Có Backend API, Alert service, PostgreSQL, Web server và trình duyệt người dùng.
- [x] Có stereotype `device`, `executionEnvironment`, `artifact`.
- [x] Ghi rõ HTTP local và HTTPS production.
- [x] Ghi rõ MQTT/LoRa là target, `RadioMedium` là simulator hiện tại.
- [x] Có luồng store-and-forward và traceability F18–F20/NFR03–05.

## 4. Đã thực hiện

- [`docs/deployment/deployment-diagram.md`](../../deployment/deployment-diagram.md): Mermaid deployment diagram, bảng node/artifact, protocol/port, sequence store-and-forward và ràng buộc security.
- Tách rõ [`docs/Componment_design.drawio.png`](../../Componment_design.drawio.png) là Component Diagram, không dùng thay Deployment Diagram.

## 5. Quy trình kiểm tra

1. Mở file Markdown trên GitHub, VS Code Mermaid Preview hoặc Mermaid Live Editor.
2. Kiểm tra sơ đồ render không lỗi.
3. Đối chiếu Sensor → Gateway → Queue → Backend → Database → Web.
4. Nhờ H xác nhận API/port; nhờ thành viên deploy xác nhận TLS, DB private network.

## 6. Kết quả đã xác minh

- Markdown code fence cân bằng và Mermaid có đầy đủ deployment node/artifact.
- Port local `8080/5173` và target `443/5432 private` được phân biệt.
- Direct UAV flight control được ghi ngoài phạm vi.

## 7. Đóng góp cho nhóm

Cho cả nhóm một bản đồ thống nhất để biết module nào chạy trên sensor, gateway, server hay browser; giúp H/NL không thiết kế sai ranh giới triển khai.

## 8. Phụ thuộc và handoff

- H xác nhận Backend/Spring/PostgreSQL target.
- NL xác nhận Web/Alert consumers.
- Nhóm deployment quyết định Docker/Nginx/TLS thật.

## 9. Giới hạn trung thực

Spring Boot, PostgreSQL, Nginx và phần cứng ESP32/Raspberry Pi là kiến trúc mục tiêu; demo hiện chạy Java mock server và simulator trên máy phát triển.

## 10. Checklist nộp thủ công

- [ ] Đính kèm file deployment diagram vào SCRUM-19.
- [ ] Xuất ảnh Mermaid hoặc chụp preview để Jira xem nhanh.
- [ ] Gắn link tới protocol SCRUM-29.
- [ ] Xin xác nhận của H và thành viên deployment.
- [ ] Giữ Done sau khi review artifact mới.

## 11. Comment Jira có thể copy

```text
Updated F20 with a real Deployment Diagram (not the existing Component Diagram).
Artifact includes Sensor/ESP32, UAV, Raspberry Pi gateway, durable local queue, Backend/Alert services, PostgreSQL, Web server/browser, protocols and ports.
Demo and target-production architecture are explicitly separated.
Review requested from Backend and Deployment members; board status can remain Done after confirmation.
```
