# Kiểm tra giai đoạn 1

Ngày chạy: **17/11/2026**, máy Windows, múi giờ Asia/Bangkok. Hai tài liệu ban đầu `BUSINESS_RULES.md`, `DA-02-DE-BAI.md` được giữ nguyên. Không deploy, tạo workflow hoặc cài công cụ toàn cục.

## Lệnh đã chạy và kết quả

Máy mặc định nhận Java 11, không nhận `mvn`/`docker`. Đã dùng JDK 21 có sẵn tại `C:/Program Files/Java/jdk-21`, tải Maven 3.9.11 vào `.tools/` và đặt Maven cache tại `.tools/m2`. PyYAML chỉ được tải vào `.tools/python` phục vụ kiểm tra cấu hình. Toàn bộ `.tools/` được bỏ qua bởi Git.

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
& .tools/apache-maven-3.9.11/bin/mvn.cmd -B '-Dmaven.repo.local=.tools/m2' -Plocal-test package
python scripts/smoke.py --local
& .tools/apache-maven-3.9.11/bin/mvn.cmd -B '-Dmaven.repo.local=.tools/m2' clean verify
python scripts/verify-local-postgres.py --pg-bin 'C:/Program Files/PostgreSQL/18/bin'
git diff --check -- .
```

| Kiểm tra | Kết quả |
|---|---|
| Build profile H2 | BUILD SUCCESS, đủ 5 JAR |
| HTTP xuyên 5 ứng dụng với H2 | PASS |
| Build mặc định `clean verify` | BUILD SUCCESS, đủ 5 JAR, không chứa H2 |
| SQL init trên PostgreSQL 18.1 cục bộ | PASS, 4 role và 4 database, dùng chính SQL trong script init Docker |
| 4 kết nối tới database của chính service | PASS |
| 12 kết nối chéo giữa 4 tài khoản/database | Đều bị PostgreSQL từ chối |
| HTTP xuyên 5 JAR production với PostgreSQL thật | PASS |
| Thành công | HTTP 200, CONFIRMED, trừ 50.000 VND, giảm 2 tồn kho |
| Shipping từ chối | HTTP 200, CANCELLED, refunded=true, ví/tồn kho trở lại trước request |
| Bật từ chối nhưng zone LOCAL | Vẫn CONFIRMED |
| Quantity=0 / sản phẩm không tồn tại | Lần lượt 400 / 409 |
| Đọc lại đơn | Khớp response đặt hàng |
| 8 request bị từ chối đồng thời | Tất cả CANCELLED/refunded, không mất cập nhật ví/tồn kho |
| Gateway chặn đường dẫn nội bộ | HTTP 404 |
| `/actuator/prometheus` của 4 service | HTTP 200, có số đo JVM |
| Trace qua cả 4 service | Cùng trace.id |
| WARN shipping | Không có error.stack_trace |
| WARN order | Có error.stack_trace của HTTP exception |
| Phân tích YAML bằng PyYAML | 6 service, chỉ gateway publish port, backend internal=true |
| Kiểm tra artifact | 5 Dockerfile, 5 executable JAR, không có H2 trong JAR mặc định |
| Docker Hub tag PostgreSQL | Manifest `postgres:17.7-alpine` trả HTTP 200 |
| `git diff --check -- .` | Không có lỗi; Git báo cảnh báo không đọc được ignore toàn cục của máy |

`mvn verify` hiện thực hiện compile/đóng gói; chưa có bộ JUnit riêng. Nghiệp vụ được kiểm tra bằng script HTTP thực tế xuyên cả năm ứng dụng, không mock các service. PostgreSQL 18.1 là phiên bản cài sẵn dùng cho kiểm thử fallback, khác image PostgreSQL 17.7 cố định trong Compose.

## Bằng chứng hai kịch bản với PostgreSQL

```json
{
  "id": "14e0989a-6126-4457-ae4b-614a21a260c9",
  "status": "CONFIRMED",
  "amount": 50000,
  "refunded": false,
  "reason": null,
  "traceId": "20485e7d4d3a4780873ce766d0db00e1"
}
```

```json
{
  "id": "f5f55fdf-9552-4dc7-96c9-60f111d06184",
  "status": "CANCELLED",
  "amount": 50000,
  "refunded": true,
  "reason": "OUTSIDE_DELIVERY_ZONE",
  "traceId": "3a424d61a1304e8c97e9a10ff267a95d"
}
```

Chênh lệch timestamp hai WARN có cùng trace ở kịch bản PostgreSQL: **5,987 ms**; kịch bản H2: **56,571 ms**. Log ECS dùng JSON lồng nhau (`trace.id` được biểu diễn bằng `{"trace":{"id":"..."}}`).

| Service | Timestamp UTC trong log | Exception |
|---|---|---|
| shipping-service | 2026-11-17T02:53:34.712084300Z | Không |
| order-service | 2026-11-17T02:53:34.718071300Z | Có |

Log build và log ứng dụng tại `.tools/build.log`, `.tools/build-production.log`, `.tools/smoke-h2.log`, `.tools/smoke-postgres.log`, `.tools/smoke/`. Các log này là bằng chứng cục bộ, không đưa vào Git. Cluster tạm và năm tiến trình ứng dụng đã được dừng sau kiểm thử.

## Lỗi trong quá trình kiểm tra đã xử lý

- PowerShell phân tách đối số `-Dmaven.repo.local` khi chưa quote: đã quote toàn bộ đối số và build lại thành công.
- Lần smoke đầu khởi động trước khi Maven đóng gói xong: đã chạy lại sau build hoàn tất.
- Assertion log ban đầu đọc khóa phẳng thay vì JSON ECS lồng nhau: đã sửa script và chạy lại đạt với H2 lẫn PostgreSQL.
- Sandbox Windows chặn PostgreSQL tạo restricted token: kiểm thử cluster tạm được chạy ngoài sandbox trong phạm vi workspace, không đổi cấu hình hệ thống.
- `pg_ctl` trên Windows giữ pipe qua tiến trình PostgreSQL: đổi output sang file, dừng đúng cluster tạm của lần trước, chạy lại toàn bộ đạt.

## Chưa thể xác minh trong môi trường này

`docker version` trả lỗi không tìm thấy lệnh. Vì vậy **chưa chạy được** `docker compose config`, Docker build, `up`, `ps` hoặc các lệnh `exec` minh chứng ngay trong container. Kiểm tra YAML không thay thế xác minh Docker thực tế.

Khi có Docker, chạy:

```sh
docker compose -f deploy/docker-compose.yml config --quiet
docker compose -f deploy/docker-compose.yml up -d --build
docker compose -f deploy/docker-compose.yml ps
python scripts/smoke.py
```

Sau đó kiểm tra metrics và quyền database bằng các lệnh `docker compose exec` trong README. Không cần thêm container thứ bảy.
