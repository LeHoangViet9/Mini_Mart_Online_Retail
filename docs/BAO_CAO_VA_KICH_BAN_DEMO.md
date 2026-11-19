# MiniMart — Báo cáo hiện trạng và kịch bản demo

**Bản dự thảo theo tình trạng xác minh ngày 19/11/2026**  
**Biến thể:** Bán lẻ trực tuyến  
**Repository:** `LeHoangViet9/Mini_Mart_Online_Retail`  
**Địa chỉ đang chạy:** http://54.79.162.59  

> Bản này chỉ ghi những phần đã được chạy hoặc quan sát trực tiếp trong quá trình triển khai. Đây chưa phải bản nộp hoàn chỉnh theo đề: HTTPS/tên miền, Prometheus/Grafana và EFK/Kibana chưa được xác nhận triển khai. Không trình bày các hạng mục đó là đã hoàn tất.

## 1. Tóm tắt dự án

MiniMart là ứng dụng bán lẻ trực tuyến thu nhỏ, được xây dựng theo kiến trúc microservice. Người dùng có thể xem sản phẩm, nhập thông tin giao hàng và đặt hàng. Gateway điều phối yêu cầu tới các service nghiệp vụ. Nếu giao hàng bị từ chối do địa chỉ ngoài vùng, luồng xử lý hoàn tiền và kết thúc đơn ở trạng thái `CANCELLED`.

Trọng tâm DevOps của phiên bản hiện tại là đóng gói các thành phần bằng Docker Compose, chạy ứng dụng trên EC2 Ubuntu, cung cấp giao diện web, và kiểm tra pipeline GitHub Actions.

## 2. Kiến trúc và công nghệ

| Thành phần | Vai trò | Công nghệ / trạng thái |
|---|---|---|
| Frontend | Trang đặt hàng; gửi yêu cầu `/api` qua Nginx | React 19, Vite 7, Nginx |
| Gateway | Cổng API, định tuyến tới service | Spring Boot; chỉ lắng nghe nội bộ Compose |
| Catalog service | Sản phẩm và tồn kho | Java 21, Spring Boot, JDBC |
| Customer service | Dữ liệu khách hàng và ví | Java 21, Spring Boot, JDBC |
| Order service | Tạo đơn và điều phối giao dịch | Java 21, Spring Boot, JDBC |
| Shipping service | Xác nhận hoặc từ chối giao hàng | Java 21, Spring Boot, JDBC |
| PostgreSQL | Lưu dữ liệu; tách bốn database theo service | PostgreSQL 17; các database catalog, customer, order, shipping |
| Máy chủ | Chạy Docker Compose; thao tác quản trị qua Session Manager | EC2 `t3.small`, Ubuntu 26.04, vùng `ap-southeast-2` |

Luồng chính: trình duyệt → Nginx frontend → gateway → order service → catalog/customer/shipping service → PostgreSQL. Các service giao tiếp qua HTTP trong mạng Docker; bốn database là các database riêng biệt. Trong cấu hình đang chạy trên EC2, frontend công khai cổng 80; gateway và các service dùng cổng 8080 nội bộ; PostgreSQL dùng cổng 5432 nội bộ.

## 3. Triển khai và kiểm tra đã thực hiện

### 3.1. Chạy ứng dụng

Ứng dụng đã được chạy bằng Docker Compose trên EC2. Container PostgreSQL báo `healthy`; gateway, bốn service nghiệp vụ và frontend đều báo `Up`. Frontend được truy cập tại `http://54.79.162.59`. Các thao tác cập nhật ứng dụng được thực hiện bằng Session Manager, đăng nhập vào máy với người dùng `ubuntu`.

Frontend image được build trên EC2 từ mã nguồn sau khi kéo thay đổi từ nhánh `main`. Vì vậy, cách triển khai hiện tại **chưa đáp ứng** yêu cầu của đề là máy chủ chỉ kéo image đã tag từ GHCR, không build image trên máy chủ.

### 3.2. Luồng đặt hàng nội vùng

Đặt hàng qua giao diện công khai trả kết quả `CONFIRMED`. Ảnh chụp đã ghi nhận số tiền `25.000 đ`, `refunded: false` và một mã đơn cùng mã tra cứu. Ở lần kiểm tra API trước đó, `GET /api/products/1` trả `HTTP 200`; lệnh tạo đơn cũng trả `HTTP 200`.

### 3.3. Luồng từ chối giao hàng ngoài vùng

Shipping service có cờ kiểm thử `rejectOutsideZone`, bật/tắt được bằng `PUT /api/shipping/rejection`. Khi bật cờ và đặt đơn ngoài vùng qua giao diện công khai, kết quả quan sát được là `CANCELLED`, `refunded: true`, lý do `OUTSIDE_DELIVERY_ZONE`.

Một kết quả đã ghi nhận:

- Mã đơn: `63ebd530-2a7f-49f9-abdb-9b798ece7c51`
- Trace ID trả trên giao diện: `06d296b72f9f44c6954f06835d6cf226`
- Trạng thái: `CANCELLED`
- Hoàn tiền: `true`
- Lý do: `OUTSIDE_DELIVERY_ZONE`

Sau phần demo, phải đặt lại `rejectOutsideZone=false` và xác nhận API trả về giá trị `false`.

## 4. CI và mã nguồn

Repository có workflow **MiniMart CI and GHCR**. Lần chạy mới nhất được quan sát trên GitHub Actions là commit `eb82f94` (`feat: add MiniMart frontend`), dấu trạng thái xanh, thời lượng khoảng 1 phút 55 giây. Đây là bằng chứng pipeline chạy thành công cho commit đó.

Cần kiểm tra thêm trang Packages của GitHub để xác nhận đủ image, quyền truy cập package, và tag bảy ký tự đầu của commit. Ảnh Actions xanh một mình chưa chứng minh toàn bộ image/tag đã đúng theo yêu cầu đề.

## 5. Báo cáo sự cố nghiệp vụ — bản dự thảo

Đề yêu cầu báo cáo sự cố một trang dựa trên log tập trung, gồm sáu mục. Hiện có response của API và trace ID trên giao diện nhưng chưa có bằng chứng Kibana/log JSON cho từng service. Vì vậy, phần dưới đây là phân tích từ response quan sát được, **chưa thay thế báo cáo điều tra EFK hoàn chỉnh**.

### 5.1. Hiện tượng

Người dùng đặt đơn với khu vực giao hàng ngoài vùng. Giao diện nhận phản hồi thành công về mặt xử lý yêu cầu nhưng đơn hàng ở trạng thái `CANCELLED`; số tiền được hoàn lại (`refunded: true`).

### 5.2. Mã truy vết

`06d296b72f9f44c6954f06835d6cf226` (hiển thị trên giao diện kết quả đơn).

### 5.3. Dòng thời gian

| Mốc | Thành phần | Sự kiện | Thời gian / độ trễ |
|---|---|---|---|
| T0 | Người vận hành | Bật `rejectOutsideZone=true` | Timestamp chi tiết chưa được lưu |
| T1 | Shipping service | Từ chối vùng giao hàng; kết quả được phản ánh bằng `OUTSIDE_DELIVERY_ZONE` | Chưa có log service để xác định timestamp |
| T2 | Order service / API | Trả đơn `CANCELLED`, `refunded=true` | Chưa đo được chênh lệch mili giây |

### 5.4. Nguyên nhân gốc

Nguyên nhân theo kết quả nghiệp vụ là điều kiện từ chối có kiểm soát ở shipping service: địa chỉ nằm ngoài vùng giao hàng. API trả `OUTSIDE_DELIVERY_ZONE`. Kết luận này dựa trên response hiển thị, chưa được xác nhận bằng dòng `WARN` đầu tiên trong log của shipping service.

### 5.5. Hậu quả kéo theo

Order service kết thúc đơn ở trạng thái `CANCELLED` và hoàn tiền. Giao diện cho thấy `refunded: true`. Hiện chưa có log liên service để chứng minh thứ tự sự kiện, ngoại lệ tại order service, hoặc trace ID giống nhau trong log của bốn service.

### 5.6. Kết luận

Đây là **từ chối nghiệp vụ có kiểm soát**, không phải lỗi hạ tầng theo response quan sát được. Để đáp ứng đúng đề, cần bổ sung log JSON chuẩn ECS, EFK/Kibana, log `WARN` ở shipping và order cùng `trace.id`, timestamp và độ trễ mili giây. Không tự điền timestamp hoặc độ trễ khi chưa có log.

## 6. Đối chiếu yêu cầu đồ án

| Yêu cầu | Hiện trạng đã xác minh |
|---|---|
| Dockerfile và Docker Compose | Đã có Dockerfile cho ứng dụng; Compose chạy các container. Cần kiểm tra lại dựng sạch từ repo để chốt bằng chứng. |
| Bốn service và bốn database | Có bốn service nghiệp vụ và bốn database riêng; đã kiểm tra quyền DB trong quá trình làm đồ án. |
| CI GitHub Actions | Workflow mới nhất trên `main` đã xanh ở commit `eb82f94`. |
| Image/tag GHCR theo commit | Workflow có tên CI and GHCR; cần kiểm tra Packages và tag cụ thể trước khi khẳng định đạt. |
| Máy chủ theo đề | Có EC2 và truy cập Session Manager; hiện là Ubuntu 26.04, `t3.small`, 20 GiB. Đề ghi Ubuntu 24.04, máy chủ quản trị riêng và SSH khóa; hiện chưa có bằng chứng đáp ứng các điều kiện này. |
| Nginx + tên miền + HTTPS | Nginx frontend đang phục vụ HTTP trên IP công khai. Chưa có tên miền hoặc chứng chỉ HTTPS. |
| Prometheus | Chưa có bằng chứng Prometheus scrape đủ bốn actuator endpoint. |
| Grafana | Chưa có dashboard sáu biểu đồ. |
| ECS JSON + EFK/Kibana + trace xuyên service | Chưa có bằng chứng log tập trung hoặc truy vấn trace ID qua các service. |
| Video demo 7–10 phút | Có thể quay demo chức năng hiện có, nhưng chưa đáp ứng đủ các bước bắt buộc Grafana, Kibana và sửa code/push để CI chạy trực tiếp trong video. |

**Kết luận về mức độ hoàn thành:** đã có ứng dụng web/API chạy công khai qua HTTP, hai kịch bản nghiệp vụ đã xác minh và CI xanh. Đồ án chưa đạt đầy đủ phạm vi bắt buộc; các mục HTTPS, giám sát, log tập trung/truy vết và triển khai bằng image GHCR cần làm tiếp trước khi coi là bản nộp cuối.

## 7. Kịch bản demo hiện trạng (khoảng 7 phút)

> Kịch bản này chỉ trình bày chức năng đã xác minh. Đề yêu cầu video 7–10 phút có HTTPS, Grafana, Kibana và thao tác sửa mã/push để CI chạy. Các phần đó chưa có trong môi trường hiện tại; không giả vờ trình diễn chúng. Muốn nộp đúng rubric, cần hoàn thiện các mục còn thiếu trong bảng ở trên trước khi quay video cuối.

### Chuẩn bị trước khi quay

- Mở sẵn GitHub Actions ở lần chạy xanh `eb82f94`.
- Mở `http://54.79.162.59` trên trình duyệt.
- Mở Session Manager vào EC2 với shell người dùng `ubuntu`.
- Kiểm tra container: `sudo docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps`.
- Đảm bảo `rejectOutsideZone=false` trước khi quay.
- Không để lộ nội dung `deploy/.env`, mật khẩu database hoặc access key trên video.

| Thời gian | Thao tác và lời dẫn |
|---|---|
| 0:00–0:40 | Giới thiệu MiniMart là hệ thống bán hàng nhỏ gồm frontend, gateway, bốn service và PostgreSQL; mục tiêu demo là kiểm tra triển khai và luồng xử lý đơn. |
| 0:40–1:25 | Mở GitHub Actions, chỉ vào workflow MiniMart CI and GHCR, commit `eb82f94` và dấu xanh. Nói rõ đây là CI xanh; việc cập nhật EC2 hiện vẫn chạy thủ công qua Session Manager. |
| 1:25–2:10 | Trên EC2, chạy `docker compose ps`, chỉ ra frontend, gateway, bốn service và PostgreSQL đang chạy. Không mở hoặc đọc file `.env`. |
| 2:10–3:20 | Mở trang MiniMart qua HTTP IP. Chọn nội vùng, đặt đơn. Chỉ ra trạng thái `CONFIRMED`, số tiền, mã đơn và mã tra cứu. Ghi chú đây là HTTP, chưa phải HTTPS. |
| 3:20–4:00 | Trong Session Manager bật điều kiện thử: `curl -i -X PUT http://localhost/api/shipping/rejection -H 'Content-Type: application/json' -d '{"rejectOutsideZone":true}'`. Chỉ tiếp tục khi response cho biết `true`. |
| 4:00–5:15 | Quay lại giao diện, chọn ngoài vùng và đặt đơn. Chỉ ra `CANCELLED`, `refunded: true`, `OUTSIDE_DELIVERY_ZONE` và trace ID. Giải thích trace ID mới chỉ được hiển thị ở response; chưa chứng minh log EFK xuyên service. |
| 5:15–5:45 | Quay lại Session Manager và khôi phục hoạt động bình thường: `curl -i -X PUT http://localhost/api/shipping/rejection -H 'Content-Type: application/json' -d '{"rejectOutsideZone":false}'`. Xác nhận response `false`. |
| 5:45–6:35 | Tóm tắt kiến trúc, cách frontend chuyển `/api` qua Nginx tới gateway và kết quả hai kịch bản. Nêu trung thực các hạng mục còn thiếu: HTTPS, Prometheus/Grafana, EFK/Kibana, deploy từ GHCR. |
| 6:35–7:00 | Kết luận: CI đã xanh và ứng dụng chạy trên EC2; đây là demo hiện trạng, chưa phải video đáp ứng đầy đủ rubric. |

## 8. Việc cần hoàn tất trước khi nộp cuối

1. Chuyển triển khai EC2 sang kéo image từ GHCR theo tag commit; không build trên EC2.
2. Đáp ứng cấu hình máy chủ theo đúng đề hoặc xin xác nhận giảng viên về Ubuntu 26.04/EC2 hiện tại.
3. Gắn tên miền thật và cấu hình Nginx + Let's Encrypt để có HTTPS hợp lệ.
4. Thêm Prometheus scrape actuator của bốn service và Grafana dashboard đủ sáu biểu đồ.
5. Thêm log JSON chuẩn ECS, collector/Elasticsearch/Kibana; truy vấn một trace ID thấy log tối thiểu ba service, theo đề là đủ bốn.
6. Dựng lại báo cáo sự cố bằng timestamp/log thật; tính độ trễ mili giây giữa WARN ở shipping và hậu quả ở order.
7. Xác minh bốn image GHCR và tag bảy ký tự đầu của commit.
8. Hoàn thiện README, phân công thành viên, sơ đồ kiến trúc và video demo đủ năm bước theo đề.
