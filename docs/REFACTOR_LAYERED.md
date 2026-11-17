# Refactor layered architecture

## Hợp đồng hiện có được ghi nhận trước khi sửa

Giữ Spring Boot 3.5.16 / Java 21, JdbcTemplate, RestClient, dependency và cấu hình hiện tại. Chưa có JUnit trong repository; kiểm thử hiện có là `scripts/smoke.py` và `scripts/verify-local-postgres.py`.

| Service | API | Request | Response / HTTP |
|---|---|---|---|
| catalog | POST /internal/reservations | orderId: UUID bắt buộc; productId > 0; quantity 1–100 | 200 `{amount: integer}` |
| catalog | POST /internal/reservations/{id}/release | id: UUID | 200, body rỗng; không tồn tại/đã trả kho thì không thay đổi |
| catalog | GET /api/products/{id} | id: long | 200 `{id,name,price,stock}` |
| customer | POST /internal/payments | orderId: UUID bắt buộc; customerId > 0; amount > 0 | 200, body rỗng |
| customer | POST /internal/payments/{id}/refund | id: UUID | 200, body rỗng; gọi lặp không hoàn thêm |
| customer | GET /api/customers/{id} | id: long | 200 `{id,name,balance}` |
| shipping | PUT /api/shipping/rejection | rejectOutsideZone: Boolean bắt buộc | 200 `{rejectOutsideZone}` |
| shipping | POST /internal/shipments | orderId: UUID bắt buộc; address không blank, tối đa 300 ký tự; zone bắt buộc LOCAL/OUTSIDE | 200 `{orderId,status:"CREATED"}` hoặc 422 `{reason:"OUTSIDE_DELIVERY_ZONE"}` |
| order | POST /api/orders | customerId > 0; productId > 0; quantity 1–100; address không blank, tối đa 300 ký tự; zone bắt buộc LOCAL/OUTSIDE | 200 `{id,status,amount,refunded,reason,traceId}` |
| order | GET /api/orders/{id} | id: UUID | 200, cùng cấu trúc response đặt hàng |

Các số tiền là số nguyên VND; `reason` của đơn thành công vẫn xuất hiện với giá trị null. Các POST trả void vẫn là HTTP 200 và body rỗng. Dữ liệu sai qua Bean Validation/JSON binding trả 400 theo Spring hiện tại. `ApiErrors` tiếp tục chuyển ResponseStatusException thành `{reason: string}`.

Các lỗi nghiệp vụ hiện có:

- Catalog: 404 PRODUCT_NOT_FOUND; 409 RESERVATION_CONFLICT / INSUFFICIENT_STOCK.
- Customer: 404 CUSTOMER_NOT_FOUND / PAYMENT_NOT_FOUND; 409 PAYMENT_CONFLICT / INSUFFICIENT_BALANCE.
- Order: 404 ORDER_NOT_FOUND; lỗi HTTP 4xx trước khi debit thành công trả 409 `BUSINESS_REJECTED orderId=<UUID>` và lưu CANCELLED; lỗi liên service không chắc chắn trả 503 `RECONCILIATION_REQUIRED orderId=<UUID>` và giữ PENDING.
- Shipping từ chối sau debit: order hoàn ví, trả kho, sau đó lưu CANCELLED/refunded=true và trả 200. Shipping ghi WARN không exception; order ghi WARN kèm exception HTTP 422. Giữ nguyên nội dung log và trace.id.

Gateway giữ route whitelist và forwarding hiện tại: POST /api/orders; GET /api/orders/{UUID}, /api/products/{digits}, /api/customers/{digits}; PUT /api/shipping/rejection. Route ngoài whitelist trả 404; lỗi kết nối upstream trả 502 UPSTREAM_UNAVAILABLE. Giữ timeout gateway 3s/40s và HTTP client của order 3s/5s.

Mọi ứng dụng giữ `X-Trace-Id` 32 ký tự hex thường; sinh mới nếu thiếu/sai; dọn MDC sau request. Health và Prometheus giữ nguyên. Không đổi schema, tên database, tài khoản, Dockerfile, Compose, properties hoặc dữ liệu seed.

## Ranh giới các tầng

```text
vn.minimart
├── Application.java
├── TraceFilter.java
├── ApiErrors.java                 # bốn service nghiệp vụ
├── controller/                    # HTTP, @Valid, response DTO
├── service/                       # nghiệp vụ, transaction cục bộ
├── repository/                    # JdbcTemplate, SQL, RowMapper
├── model/                         # dữ liệu nội bộ, trạng thái/zone
├── dto/                           # request/response API
├── client/                        # chỉ order: CatalogClient, CustomerClient, ShippingClient
└── config/                        # chỉ order: cấu hình RestClient
```

Gateway chỉ có controller định tuyến cùng Application và TraceFilter; không thêm tầng dữ liệu hay nghiệp vụ bán hàng. Không thêm interface hoặc dependency.

Transaction catalog reserve/release, customer debit/refund, shipping create chuyển từ controller sang các phương thức public của service để Spring proxy quản lý. Giữ nguyên thứ tự khóa hàng, các điều kiện chống gọi lặp và các thao tác trong cùng transaction. Order không mở transaction quanh HTTP; PENDING được commit trước lời gọi đầu tiên. Repository nhận trạng thái từ service, không tự quyết định chuyển trạng thái nghiệp vụ.

## File được tổ chức lại

Các đường dẫn dưới đây nằm trong `services/<service>/src/main/java/vn/minimart/`. Năm controller được chuyển khỏi package gốc sang `controller/`; phần nghiệp vụ/SQL cũ được tách vào các lớp sau:

| Thành phần | Các lớp |
|---|---|
| catalog controller/service/repository | controller/CatalogController; service/CatalogService; repository/CatalogRepository |
| catalog model | Product, Reservation |
| catalog DTO | ReserveRequest, ReservationResponse, ProductResponse |
| customer controller/service/repository | controller/CustomerController; service/CustomerService; repository/CustomerRepository |
| customer model | Customer, WalletPayment |
| customer DTO | PaymentRequest, CustomerResponse |
| shipping controller/service/repository | controller/ShippingController; service/ShippingService; repository/ShippingRepository |
| shipping model | Shipment, ShippingSettings, ShippingResult, Zone |
| shipping DTO | ShipmentRequest, ShipmentResponse, ShippingSettingsRequest, ShippingSettingsResponse, ShippingRejectionResponse |
| order controller/service/repository | controller/OrderController; service/OrderService; repository/OrderRepository |
| order model | Order, OrderStatus, Zone |
| order DTO | PlaceOrderRequest, OrderResponse, ReserveStockRequest, ReservationResponse, DebitWalletRequest, CreateShipmentRequest |
| order HTTP | client/CatalogClient; client/CustomerClient; client/ShippingClient; config/ServiceHttpConfig |
| gateway | controller/GatewayController (giữ nguyên thuật toán routing/forwarding) |

Các lớp model và DTO nằm trong package tương ứng ở từng service. Không dùng chung entity xuyên service. Logger nghiệp vụ tiếp tục dùng tên gốc `vn.minimart.<Tên>Controller` để giữ tương thích truy vấn log, cùng message/level/exception như trước.

Các file khác:

- `.gitignore`: bổ sung ghi chú và quy tắc `target/`; giữ các quy tắc cũ. Không xóa dữ liệu người dùng.
- `scripts/smoke.py`: giữ các assertion cũ, gọi thêm bộ regression API nội bộ khi chạy local.
- `scripts/refactor_api_contracts.py`: thêm kiểm tra schema JSON, status, validation, gọi lặp và nhánh hủy trước debit.
- `services/order-service/src/test/java/vn/minimart/service/OrderServiceTest.java`: bốn unit test cho các nhánh lỗi điều phối.
- `docs/REFACTOR_LAYERED.md`: tài liệu mới này; không sửa README hoặc tài liệu có sẵn.

## Kết quả kiểm tra

Chạy ngày 17/11/2026 (Asia/Bangkok), JDK 21 và Maven 3.9.11 cục bộ có sẵn từ giai đoạn trước. Không cài công cụ toàn cục. Các lệnh từ thư mục gốc:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
& .tools/apache-maven-3.9.11/bin/mvn.cmd -B '-Dmaven.repo.local=.tools/m2' clean verify
python scripts/verify-local-postgres.py --pg-bin 'C:/Program Files/PostgreSQL/18/bin'
& .tools/apache-maven-3.9.11/bin/mvn.cmd -B '-Dmaven.repo.local=.tools/m2' -Plocal-test -DskipTests clean package
python scripts/smoke.py --local
# Khôi phục artifact mặc định không có H2, chạy lại build/check tổng:
& .tools/apache-maven-3.9.11/bin/mvn.cmd -B '-Dmaven.repo.local=.tools/m2' clean verify
```

| Kiểm tra | Kết quả |
|---|---|
| Build cả năm ứng dụng | BUILD SUCCESS |
| Bốn unit test order | 4 passed, 0 failure/error |
| HTTP xuyên năm ứng dụng trên PostgreSQL 18.1 thật | PASS |
| HTTP xuyên năm ứng dụng trên H2 | PASS |
| CONFIRMED / CANCELLED | HTTP 200 đúng JSON; ví được hoàn, tồn kho được trả khi shipping từ chối |
| Tám đơn bị hủy đồng thời | Không mất cập nhật ví/tồn kho |
| Reserve/debit gọi lặp | Chỉ giữ kho/trừ tiền một lần; sai payload trả 409 |
| Release/refund gọi lặp | Chỉ trả kho/hoàn tiền một lần; body vẫn rỗng, HTTP 200 |
| Các lỗi và validation API | Giữ các reason, 400/404/409/422 và tên trường JSON |
| Khách hàng không tồn tại sau khi giữ kho | Order 409, CANCELLED/BUSINESS_REJECTED; kho được trả |
| LOCAL/OUTSIDE và bật/tắt từ chối | Giữ hành vi hiện tại |
| Metrics bốn service | HTTP 200, có số đo JVM |
| Trace và hai WARN | Cùng trace.id; shipping không exception, order có exception |
| Khoảng cách WARN PostgreSQL | 19,001 ms; trace 2231d6b927bb4bcaa3b7fe3ea11e5660 |
| Khoảng cách WARN H2 | 21,279 ms; trace 42d9b0ba039a4f96a141be5ec9286cec |
| Quyền PostgreSQL | 4 kết nối đúng database được phép; 12 kết nối chéo bị từ chối |
| Kiểm tra ranh giới tầng | SQL/JDBC chỉ trong repository; transaction trong service; HTTP order trong client/config |
| Đối chiếu SHA-256 trước/sau | README, tài liệu cũ, POM, schema, deploy, Dockerfile, properties, Application, TraceFilter, ApiErrors giống từng byte |
| git check-ignore | Bỏ qua target/ ở gốc và trong module |

Bốn unit test xác minh: timeout giữ PENDING, từ chối debit trả kho và hủy, timeout shipping sau debit giữ PENDING để đối soát, hoàn tiền thất bại không được báo CANCELLED/refunded. Các test HTTP nội bộ thực hiện trên ứng dụng và database thật của lần chạy, không mock repository.

Log lần refactor nằm trong `.tools/refactor-build.log`, `.tools/refactor-build-final.log`, `.tools/refactor-postgres.log`, `.tools/refactor-h2-build.log`, `.tools/refactor-h2-smoke.log`. Log và công cụ tạm không đưa vào Git. Script tự dừng các ứng dụng và cluster tạm sau kiểm thử.

Không có lỗi compile hoặc regression nghiệp vụ trong các lần chạy. Lệnh kiểm tra tĩnh viết inline ban đầu gặp lỗi quote PowerShell; đã chuyển thành script cục bộ và chạy lại thành công.

Docker vẫn không có trong PATH; **chưa chạy Docker build hoặc Docker Compose thực tế**. Compose/Dockerfile được giữ nguyên, không suy diễn kết quả chạy container từ kiểm thử JAR. Không triển khai, không thêm CI/CD, không commit/push. Các hạn chế saga/retry/khôi phục sau crash của giai đoạn 1 được giữ nguyên.
