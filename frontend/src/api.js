// Same-origin requests: Vite (local) or Nginx (Docker) owns the gateway URL.
export async function request(path, options = {}) {
  let response;
  try {
    const timeout = AbortSignal.timeout(65000);
    response = await fetch(`/api${path}`, { ...options, signal: options.signal ? AbortSignal.any([options.signal, timeout]) : timeout });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new Error('Không thể kết nối đến cửa hàng. Nếu vừa đặt hàng, kết quả có thể chưa xác định; hãy kiểm tra đơn trước khi gửi lại để tránh tạo đơn trùng.');
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const messages = {
      400: 'Thông tin chưa hợp lệ. Vui lòng kiểm tra các trường đã nhập.',
      404: 'Không tìm thấy sản phẩm, khách hàng hoặc đơn hàng.',
      409: 'Chưa thể đặt hàng. Vui lòng kiểm tra mã khách hàng, sản phẩm, tồn kho và số dư ví.',
      502: 'Gateway chưa kết nối được dịch vụ. Vui lòng thử lại sau.',
      503: 'Đơn hàng cần đối soát, kết quả chưa xác định. Hãy kiểm tra đơn trước khi gửi lại để tránh tạo đơn trùng.',
    };
    const error = new Error(messages[response.status] || 'Cửa hàng đang gặp sự cố. Vui lòng thử lại sau.');
    error.detail = data?.reason || data?.message || data?.detail || data?.error;
    error.traceId = data?.traceId || response.headers.get('X-Trace-Id');
    throw error;
  }
  if (!data) throw new Error('Phản hồi từ cửa hàng không hợp lệ. Nếu vừa đặt hàng, hãy kiểm tra đơn trước khi gửi lại.');
  return data;
}

export const money = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
