# BUSINESS_RULES — MiniMart

## 1. Phạm vi

MiniMart chỉ triển khai một luồng nghiệp vụ: khách đặt đơn hàng trực tuyến. Ngoài luồng này, mỗi service chỉ được có tối đa ba endpoint phụ trợ để tạo dữ liệu mẫu và kiểm tra.

## 2. Quy tắc đặt hàng

1. `order-service` nhận yêu cầu đặt hàng và tạo đơn với trạng thái `PENDING`.
2. `catalog-service` kiểm tra sản phẩm và giữ tồn kho cho đơn.
3. `customer-service` trừ tiền từ ví của khách hàng.
4. `shipping-service` lập phiếu giao nếu địa chỉ được phục vụ; nếu địa chỉ nằm ngoài vùng giao, service từ chối có kiểm soát.
5. Nếu `shipping-service` từ chối sau khi ví đã bị trừ, `customer-service` hoàn lại số tiền đã trừ.
6. `order-service` kết thúc đơn ở trạng thái `CONFIRMED` khi các bước thành công, hoặc `CANCELLED` khi giao hàng bị từ chối.

## 3. Kịch bản từ chối giao hàng

- Điều kiện từ chối là địa chỉ nằm ngoài vùng giao hàng.
- Có thể bật hoặc tắt điều kiện này bằng một lời gọi API.
- Khi điều kiện từ chối xảy ra, API đặt hàng trả `HTTP 200`, kèm đơn `CANCELLED` và xác nhận tiền đã được hoàn.
- `shipping-service` ghi log `WARN` không kèm ngoại lệ để ghi nhận nguyên nhân gốc.
- `order-service` ghi log `WARN` có kèm ngoại lệ từ lời gọi liên service để ghi nhận hậu quả. Hai log phải dùng cùng `trace.id` và có thời điểm cách nhau đo được bằng mili giây.

## 4. Quy tắc phân chia dữ liệu và gọi service

- `catalog-service` sở hữu danh mục hàng và tồn kho, dùng database `minimart_catalog_db`.
- `customer-service` sở hữu khách hàng và số dư ví, dùng database `minimart_customer_db`.
- `order-service` sở hữu đơn hàng, dùng database `minimart_order_db`.
- `shipping-service` sở hữu phiếu giao, dùng database `minimart_shipping_db`.
- Mỗi service chỉ được đọc hoặc ghi database do mình sở hữu. Không truy vấn trực tiếp database của service khác.
- Các service trao đổi dữ liệu qua HTTP. `gateway` là cổng vào duy nhất từ bên ngoài và định tuyến request theo đường dẫn; gateway không có database.

## 5. Trạng thái đơn hàng được yêu cầu

| Trạng thái | Ý nghĩa |
|---|---|
| `PENDING` | Đơn vừa được tạo, luồng xử lý đang tiếp tục. |
| `CONFIRMED` | Các bước đặt hàng và giao hàng đã thành công. |
| `CANCELLED` | Giao hàng bị từ chối có kiểm soát; tiền đã được hoàn. |

## 6. Ngoài phạm vi đề bài

Đề không yêu cầu thêm giỏ hàng, mã giảm giá, đăng nhập, thanh toán cổng ngân hàng, theo dõi giao hàng hoặc quản lý đổi trả. Không tự mở rộng các chức năng này nếu chúng làm chậm yêu cầu vận hành và truy vết của đồ án.
