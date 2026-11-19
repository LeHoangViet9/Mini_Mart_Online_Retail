# Kiểm chứng frontend — 18/11/2026

Đã đọc README, pom.xml gốc, deploy/docker-compose.yml, toàn bộ controller, cấu hình gateway, DTO và luồng Order/Catalog trước khi sửa. Không sửa mã Java, hợp đồng API, schema hoặc dữ liệu production. Không đọc/copy deploy/.env hoặc thông tin AWS vào frontend.

## Thay đổi

- `frontend/`: React + Vite, CSS responsive tiếng Việt, API client, 10 kiểm thử, lockfile, cấu hình môi trường mẫu, Dockerfile nhiều giai đoạn và Nginx template.
- `deploy/docker-compose.yml`: thêm frontend; chỉ publish cổng HTTP 80, bỏ publish gateway 8080. Gateway vẫn ở edge/backend; service/database vẫn nội bộ.
- `deploy/.env.example`: HTTP_PORT và FRONTEND_API_URL thay GATEWAY_PORT.
- `scripts/smoke.py`: base URL Compose mặc định chuyển từ localhost:8080 sang localhost; chế độ --local vẫn dùng 18080.
- `.gitignore`, `.dockerignore`: bỏ qua frontend build/dependencies/cache, file môi trường và khóa bí mật.
- `README.md`: hướng dẫn local, build, kiểm tra, cấu hình proxy, giữ volume và triển khai EC2.

Không commit hoặc triển khai từ phiên làm việc này. `docs/DA-02-DE-BAI.md` đã ở trạng thái bị xóa trước khi bắt đầu; thay đổi đó được giữ nguyên.

## Đã chạy thành công

Môi trường Windows, Node.js v24.16.0, npm 11.13.0. Dùng npm.cmd vì npm.ps1 bị execution policy chặn. Cache npm mặc định không ghi được; chuyển cache vào frontend/npm-cache (đã ignore).

```powershell
cd frontend
npm.cmd install --cache ./npm-cache
npm.cmd test
npm.cmd run build
npm.cmd audit --omit=dev --cache ./npm-cache
```

- Vitest: **10/10** kiểm thử qua. HTTP được mock, không gọi EC2: tải/lỗi/tải lại sản phẩm, JSON POST đúng hợp đồng, CONFIRMED, CANCELLED/refunded true và false, reason, lỗi 400/409/503, giữ form, lỗi mạng, giới hạn quantity và chặn gửi lặp khi chờ.
- Vite build thành công: JS khoảng 232,62 kB (gzip 73,07 kB), CSS 6,60 kB (gzip 2,06 kB).
- Audit dependency production: **0 vulnerabilities**.
- Phân tích YAML bằng package `yaml` cài tạm không ghi vào package/lockfile: xác nhận 7 container, mọi đường dẫn Dockerfile tồn tại, chỉ frontend có ports, backend internal=true, frontend chỉ nối edge, gateway nối edge/backend, origin proxy đúng cấu hình. Đây là kiểm tra cấu trúc, không thay thế Docker Compose CLI.
- `git diff --check` không báo lỗi khoảng trắng. `git check-ignore` xác nhận deploy/.env, frontend/.env, node_modules, npm-cache, dist được bỏ qua.
- Trình duyệt Chromium với API giả lập: kiểm tra hình ảnh desktop (1440px), mobile 375×812; không tràn ngang (document width = viewport width = 375px). Form có nhãn truy cập, trạng thái kết quả aria-live. Thử điền địa chỉ và bấm đặt hàng trên mobile cho kết quả CONFIRMED, dữ liệu form còn nguyên. Đã quan sát cả màn hình sản phẩm và lỗi tải.

Vite kiểm tra trình duyệt dùng API_URL trỏ loopback không có backend và fetch giả lập trong trình duyệt. Không gửi POST thật hoặc thay cấu hình Shipping trên EC2. Preview server dùng cho QA đã dừng sau kiểm tra.

## Giới hạn và việc cần làm thủ công

- Lệnh `docker compose -f deploy/docker-compose.yml build` không chạy được: máy không có Docker CLI/Engine (không tìm thấy lệnh docker). Chưa xác minh image Nginx, envsubst, DNS Docker hoặc luồng HTTP qua stack thật. Trên máy có Docker cần chạy `config --quiet`, `build`, `up -d`, kiểm tra `/healthz`, `/api/products/1` và các kịch bản đặt đơn trên dữ liệu test theo README.
- Audit đầy đủ báo **5 high**, cùng bắt nguồn từ picomatch qua fdir/tinyglobby/Vite/Vitest. Bản picomatch mới nhất registry trả về lúc kiểm tra là 4.0.7, vẫn trong dải bị báo; không force downgrade công cụ để che cảnh báo. Các package này thuộc bộ công cụ dev/build/test, không nằm trong image Nginx cuối. Cần theo dõi bản vá và chạy lại audit sau cập nhật.
- Không chạy lại Maven vì không thay mã Java. Chưa kiểm thử tích hợp frontend với năm service thật trong phiên này.
- EC2 cần cấu hình deploy/.env trực tiếp trên server, giữ volume và thông tin đăng nhập hiện tại, mở HTTP 80 và đóng inbound 8080/5432. Chưa triển khai hoặc sửa Security Group. Không chạy smoke hay POST thử trên production.
