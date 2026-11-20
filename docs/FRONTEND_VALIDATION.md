# Kiểm chứng frontend — 18/11/2026

## Cập nhật kiểm tra frontend và CI — 20/11/2026

Phần bên dưới mục này ghi nhận phiên 18/11; kết quả hiện tại là **23/23** test, thay cho số 10/10 của phiên cũ. Đã giữ thay đổi đối soát đang có trong App/API/tests; chỉ chỉnh thêm tương phản chữ và tên truy cập logo. Dấu cách trước `package` trong CatalogService đã được xóa, nên file Java không còn diff so với HEAD. Không tìm thấy AGENTS.md trong workspace hoặc các thư mục cha đã kiểm tra. Không stage/commit/push/deploy; không đọc deploy/.env hoặc gọi API production.

Lệnh thực tế trên Windows, từ gốc repository (Node 24.16.0, npm 11.13.0):

```powershell
Push-Location frontend
npm.cmd ci --cache ./npm-cache --no-audit --no-fund
npm.cmd test
npm.cmd run build
Pop-Location
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
& .tools/apache-maven-3.9.11/bin/mvn.cmd -B '-Dmaven.repo.local=.tools/m2' clean verify
& .tools/apache-maven-3.9.11/bin/mvn.cmd -B '-Dmaven.repo.local=.tools/m2' -Plocal-test clean package
python scripts/smoke.py --local
# Khôi phục JAR mặc định không chứa H2 sau smoke:
& .tools/apache-maven-3.9.11/bin/mvn.cmd -B '-Dmaven.repo.local=.tools/m2' clean verify
python .tools/check-ci.py
git diff --check
git check-ignore frontend-build.log deploy/.env frontend/.env frontend/node_modules frontend/dist frontend/npm-cache
docker version
```

- npm ci thành công; Vitest 23 passed, 2 files; Vite build thành công (JS 235,38 kB, CSS 6,78 kB). Test/build đã chạy lại sau sửa accessibility và vẫn qua.
- Cả hai lần Maven mặc định và build profile local-test đều BUILD SUCCESS; bốn unit test Order qua, không failure/error. Smoke H2 qua CONFIRMED, CANCELLED/refund/stock release, đọc lại đơn, LOCAL, validation, gateway boundary, tám đơn hủy đồng thời và regression API nội bộ. WARN cùng trace có độ lệch shipping → order 18 ms. Log chỉ ở `.tools/`, đã ignore.
- `.tools/check-ci.py` là công cụ kiểm tra cục bộ đã ignore, không phải thành phần CI: PyYAML kiểm tra trigger/quyền/lệnh/tag, Bash `-n` kiểm tra hai script publish, kiểm tra đủ sáu Dockerfile/context và UTF-8. Compose hiện có **9 service** vì đã bao gồm Prometheus/Grafana; kiểm tra cấu trúc xác nhận backend internal, frontend chỉ nối edge, Java/PostgreSQL không publish cổng.
- CI mới chạy Java verify, frontend test/build và smoke H2 khi push/PR main. Chỉ job publish sau push main có packages:write và dùng GITHUB_TOKEN; xuất bản sáu image cùng namespace hiện có, tag SHA đầy đủ và latest. Không có bước deploy hoặc đọc/in `.env`. Chưa chạy workflow trên GitHub hoặc push image từ phiên này.
- FE chỉ tạo đơn bằng POST `/api/orders` cùng origin qua gateway. Đọc sản phẩm và đối soát dùng GET; không gọi service nội bộ. Test xác nhận CONFIRMED, CANCELLED/refunded true/false/reason, PENDING, 503 có orderId, timeout/502/504 không có ID, giữ form và không tự retry POST.
- Đã mở preview bằng `npm.cmd run preview -- --port 4173 --strictPort` và kiểm tra HTTP local 200. Kết nối trình duyệt đến preview bị timeout, nên QA trình duyệt dùng HTML/CSS/JS đã build được nạp vào trang biệt lập, mọi fetch được giả lập. Kiểm tra ảnh mobile 375×812 (scrollWidth=375), desktop 1440px không tràn ngang; nhãn form, aria-live, Tab từ customerId sang productId và focus hiển thị đúng. Đặt thử với mock cho CONFIRMED, giữ địa chỉ, chỉ một POST. Lighthouse snapshot accessibility 100 trên cả mobile/desktop sau chỉnh màu và logo. Cảnh báo SEO về link `/` do trang QA là about:blank; đây không phải kiểm thử Nginx hay tích hợp stack thật.
- `git diff --check` qua; log/env/dependencies/dist/cache đều được ignore. `frontend-build.log` cũ được giữ trên đĩa và bổ sung ignore; lỗi Docker trong log cũ chưa được xác nhận đã hết trên Linux/Docker.
- `docker version` thất bại vì không tìm thấy lệnh docker. **Chưa xác nhận Docker Compose thật**, Nginx/envsubst/DNS hoặc build image bằng Node 22 trên Linux. Các kiểm tra hiện tại không thay thế bước đó.

### Xác nhận thủ công trên máy cá nhân có Docker

Dùng project và dữ liệu test riêng, không chạy trên EC2/production. Từ gốc repo, tạo `.tools/compose-validation.env` từ `deploy/.env.example`, đặt mật khẩu test riêng (gồm GRAFANA_ADMIN_PASSWORD), HTTP_PORT=18088, giữ FRONTEND_API_URL=http://gateway:8080 và MONITORING_BIND_ADDRESS=127.0.0.1. Kiểm tra cổng 18088/3000/9090 còn trống. Không dùng thông tin hoặc volume production.

```powershell
New-Item -ItemType Directory -Force .tools | Out-Null
Copy-Item deploy/.env.example .tools/compose-validation.env
# Chỉnh file test như hướng dẫn trên, không in nội dung ra log.
$composeArgs=@('--env-file','.tools/compose-validation.env','-p','minimart-validation','-f','deploy/docker-compose.yml')
docker compose @composeArgs config --quiet
docker compose @composeArgs build
docker compose @composeArgs up -d
docker compose @composeArgs ps
curl.exe -fsS http://localhost:18088/healthz
curl.exe -fsS http://localhost:18088/api/products/1
# Chờ API sẵn sàng; lệnh này thay đổi dữ liệu test riêng:
python scripts/smoke.py --base http://localhost:18088
docker compose @composeArgs down
```

Mở http://localhost:18088, thử form LOCAL và kịch bản OUTSIDE/refunded theo README trên stack test; kiểm tra mobile, bàn phím và lỗi kết nối còn giữ form. Smoke kết thúc với chế độ từ chối ngoài vùng đang bật; khi thử UI cần chủ động đặt lại rejection=false theo README. Không dùng `down -v`. Sau khi người quản trị tự push main, xác nhận workflow xanh và đủ sáu package GHCR có tag SHA/latest. Không tự commit/push/deploy trong phiên này.

---

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
