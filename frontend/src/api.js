export const reconciliationMessage = 'Đơn hàng cần đối soát, kết quả chưa xác định. Không tự gửi lại yêu cầu đặt hàng. Hãy kiểm tra đơn nếu đã nhận được mã đơn hoặc liên hệ người hướng dẫn trước khi thử lại.';

export function reasonMessage(reason = '') {
  const messages = {
    PRODUCT_NOT_FOUND: 'Không tìm thấy sản phẩm.',
    CUSTOMER_NOT_FOUND: 'Không tìm thấy khách hàng.',
    ORDER_NOT_FOUND: 'Không tìm thấy đơn hàng.',
    INSUFFICIENT_STOCK: 'Sản phẩm không đủ tồn kho.',
    INSUFFICIENT_BALANCE: 'Ví khách hàng không đủ tiền.',
    OUTSIDE_DELIVERY_ZONE: 'Địa chỉ ngoài vùng giao hàng.',
    BUSINESS_REJECTED: 'Đơn bị từ chối. Vui lòng kiểm tra mã khách hàng, mã sản phẩm, tồn kho và số dư ví; backend chưa cung cấp nguyên nhân cụ thể.',
  };
  return messages[reason.split(' ')[0]];
}

// Accept only an ID explicitly returned by the API, never infer one after a timeout.
function receivedOrderId(data) {
  const uuid = '[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}';
  const direct = data?.id || data?.orderId;
  if (typeof direct === 'string' && new RegExp(`^${uuid}$`).test(direct)) return direct;
  for (const detail of [data?.reason, data?.message, data?.detail]) {
    const match = typeof detail === 'string' && detail.match(new RegExp(`\\borderId=(${uuid})(?![a-zA-Z0-9-])`));
    if (match) return match[1];
  }
  return null;
}

// Same-origin requests: Vite (local) or Nginx (Docker) owns the gateway URL.
export async function request(path, options = {}) {
  let response;
  try {
    const timeout = AbortSignal.timeout(65000);
    response = await fetch(`/api${path}`, { ...options, signal: options.signal ? AbortSignal.any([options.signal, timeout]) : timeout });
  } catch (error) {
    if (options.signal?.aborted) throw error;
    throw new Error(`Không thể kết nối đến cửa hàng hoặc yêu cầu đã quá thời gian chờ. ${options.method === 'POST' ? reconciliationMessage : 'Vui lòng kiểm tra kết nối rồi tải lại.'}`);
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const messages = {
      400: 'Thông tin chưa hợp lệ. Vui lòng kiểm tra các trường đã nhập.',
      404: 'Không tìm thấy sản phẩm, khách hàng hoặc đơn hàng.',
      409: 'Chưa thể đặt hàng. Vui lòng kiểm tra mã khách hàng, sản phẩm, tồn kho và số dư ví.',
      502: 'Gateway chưa kết nối được dịch vụ.',
      503: reconciliationMessage,
      504: 'Gateway đã quá thời gian chờ phản hồi.',
    };
    const error = new Error(messages[response.status] || 'Cửa hàng đang gặp sự cố. Vui lòng thử lại sau.');
    error.detail = data?.reason || data?.message || data?.detail || data?.error;
    if (typeof error.detail !== 'string') error.detail = error.detail ? JSON.stringify(error.detail) : undefined;
    const translated = reasonMessage(error.detail);
    if (translated && response.status !== 503) error.message = translated;
    if (options.method === 'POST' && response.status >= 500 && response.status !== 503) error.message += ` ${reconciliationMessage}`;
    error.status = response.status;
    error.orderId = receivedOrderId(data);
    error.traceId = data?.traceId || response.headers.get('X-Trace-Id');
    throw error;
  }
  if (!data || (path.startsWith('/orders') && (!data.id || !['PENDING', 'CONFIRMED', 'CANCELLED'].includes(data.status)))) {
    const error = new Error(`Phản hồi từ cửa hàng không hợp lệ. ${options.method === 'POST' ? reconciliationMessage : 'Chưa thể đọc kết quả đơn hàng.'}`);
    error.orderId = receivedOrderId(data);
    error.traceId = response.headers.get('X-Trace-Id');
    throw error;
  }
  if (path.startsWith('/orders') && !data.traceId) data.traceId = response.headers.get('X-Trace-Id');
  return data;
}

export const money = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
