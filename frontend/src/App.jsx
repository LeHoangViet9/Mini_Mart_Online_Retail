import React, { useEffect, useRef, useState } from 'react';
import { money, request } from './api';
import './style.css';

function ErrorMessage({ error }) {
  return <div className="notice error" role="alert"><strong>{error.message}</strong>{error.detail && <p>Chi tiết: {error.detail}</p>}{error.traceId && <small>Mã tra cứu: {error.traceId}</small>}</div>;
}

export function App() {
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [productError, setProductError] = useState(null);
  const [reload, setReload] = useState(0);
  const [form, setForm] = useState({ customerId: '1', productId: '1', quantity: '1', address: '', zone: 'LOCAL' });
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false);
  const [result, setResult] = useState(null);
  const [orderError, setOrderError] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setProductError(null);
    request('/products/1', { signal: controller.signal }).then(setProduct).catch(error => {
      if (error.name !== 'AbortError') setProductError(error);
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [reload]);

  function change(event) { setForm(current => ({ ...current, [event.target.name]: event.target.value })); }
  async function submit(event) {
    event.preventDefault();
    if (submitting.current) return;
    if (!form.address.trim()) { setOrderError(new Error('Vui lòng nhập địa chỉ nhận hàng.')); return; }
    const numbers = ['customerId', 'productId', 'quantity'].map(key => Number(form[key]));
    if (numbers.some(value => !Number.isSafeInteger(value) || value < 1) || numbers[2] > 100) {
      setOrderError(new Error('Mã khách hàng, mã sản phẩm phải là số nguyên dương; số lượng từ 1 đến 100.')); return;
    }
    submitting.current = true;
    setBusy(true); setResult(null); setOrderError(null);
    try {
      const data = await request('/orders', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...form, customerId: numbers[0], productId: numbers[1], quantity: numbers[2], address: form.address.trim() }) });
      setResult(data);
      setReload(value => value + 1);
    } catch (error) { setOrderError(error); }
    finally { submitting.current = false; setBusy(false); }
  }
  const estimated = product && Number(form.productId) === product.id && Number(form.quantity) >= 1 && Number(form.quantity) <= 100;

  return <>
    <header><a className="brand" href="/" aria-label="MiniMart trang chủ"><span className="brand-icon">m<span>•</span></span>MiniMart<span className="brand-dot">.</span></a><span className="header-note">Cửa hàng nhỏ, tiện ích mỗi ngày</span><a className="header-link" href="#order">Đặt hàng <span aria-hidden="true">↗</span></a></header>
    <main>
      <section className="intro"><span className="eyebrow"><i /> MUA SẮM THẬT ĐƠN GIẢN</span><h1>Món quen mỗi ngày.<br /><span>Đặt ngay trong vài bước.</span></h1><p>Chọn sản phẩm, điền thông tin và để MiniMart lo phần còn lại.</p></section>
      <div className="shop-grid">
        <section className="product-section" aria-labelledby="product-title">
          <div className="section-heading"><h2 id="product-title">Sản phẩm hôm nay</h2><button className="text-button" disabled={loading} onClick={() => setReload(value => value + 1)}>↻ {loading ? 'Đang tải…' : 'Tải lại'}</button></div>
          {loading ? <div className="product-placeholder" role="status"><span className="spinner" />Đang tải sản phẩm…</div> : productError ? <ErrorMessage error={productError} /> : product && <article className="product-card">
            <div className="product-art"><span className="stock-label">{product.stock > 0 ? '● Còn hàng' : 'Hết hàng'}</span><svg viewBox="0 0 320 240" role="img" aria-label="Minh họa túi hàng MiniMart"><ellipse cx="160" cy="215" rx="86" ry="12" fill="#d2ddc4"/><path d="M116 91V65c0-59 88-59 88 0v26" fill="none" stroke="#38644d" strokeWidth="9"/><path d="M92 77h136l15 133H77z" fill="#e4c799"/><path d="M92 77h136l-14 133H92z" fill="#f0d8ac"/><path d="M92 77h136l-11 16H102z" fill="#d9b780"/><rect x="115" y="118" width="85" height="64" rx="8" fill="#175e43"/><text x="157" y="163" textAnchor="middle" fill="#fff9eb" fontSize="46" fontFamily="sans-serif" fontWeight="700">m.</text><path d="M238 49c-28-14-43 0-37 24 23 4 40-4 37-24" fill="#779664"/><path d="M211 72l20-17" stroke="#426d43" strokeWidth="2"/></svg><span className="art-caption">Một chút tiện lợi, mỗi ngày.</span></div>
            <div className="product-info"><span className="eyebrow muted">SẢN PHẨM #{product.id}</span><h3>{product.name}</h3><div className="price-row"><strong>{money(product.price)}</strong><span>/ sản phẩm</span></div><div className="stock-row"><span>Tồn kho hiện tại</span><strong>{product.stock.toLocaleString('vi-VN')} sản phẩm</strong></div></div>
          </article>}
          <div className="how-it-works"><h3>Từ cửa hàng đến tay bạn</h3><ol><li><span>01</span>Chọn sản phẩm</li><li><span>02</span>Điền địa chỉ</li><li><span>03</span>Nhận kết quả</li></ol></div>
        </section>
        <section className="order-card" id="order" aria-labelledby="order-title"><div className="order-heading"><span className="eyebrow muted">CHỈ VÀI THÔNG TIN</span><h2 id="order-title">Đặt hàng của bạn</h2><p>Thông tin có dấu * là bắt buộc.</p></div>
          <form onSubmit={submit} aria-busy={busy}><fieldset disabled={busy}>
            <div className="form-row"><label>Mã khách hàng *<input name="customerId" type="number" min="1" max="9007199254740991" step="1" required value={form.customerId} onChange={change} /><small>Tài khoản demo: 1</small></label><label>Mã sản phẩm *<input name="productId" type="number" min="1" max="9007199254740991" step="1" required value={form.productId} onChange={change} /><small>Sản phẩm đang giới thiệu: 1</small></label></div>
            <label>Số lượng *<input name="quantity" type="number" min="1" max="100" step="1" required value={form.quantity} onChange={change} /><small>Từ 1 đến 100 sản phẩm mỗi đơn</small></label>
            <label>Địa chỉ nhận hàng *<textarea name="address" autoComplete="street-address" maxLength="300" required rows="3" placeholder="Số nhà, tên đường, phường/xã, tỉnh/thành phố" value={form.address} onChange={change} /></label>
            <label>Khu vực giao hàng *<select name="zone" value={form.zone} onChange={change}><option value="LOCAL">Nội vùng (LOCAL)</option><option value="OUTSIDE">Ngoài vùng (OUTSIDE)</option></select></label>
            <p className="delivery-note">Ngoài vùng có thể bị từ chối tùy cấu hình giao hàng. Kết quả và trạng thái hoàn tiền sẽ hiển thị bên dưới.</p>
            <div className="estimate"><span>Tạm tính</span><strong>{estimated ? money(product.price * Number(form.quantity)) : 'Theo sản phẩm đã chọn'}</strong></div><p className="estimate-note">Số tiền chính thức được xác nhận khi tạo đơn.</p>
            <button className="submit-button" type="submit" disabled={busy}>{busy ? 'Đang xử lý đơn hàng…' : 'Đặt hàng'}<span aria-hidden="true">{busy ? '◌' : '→'}</span></button>
          </fieldset></form>
          <div className="results" aria-live="polite" aria-atomic="true">{orderError && <ErrorMessage error={orderError} />}{result && <div className={`notice ${result.status === 'CONFIRMED' ? 'success' : 'warning'}`}><strong>{result.status === 'CONFIRMED' ? 'Đặt hàng thành công' : result.status === 'CANCELLED' ? 'Đơn hàng đã bị hủy' : 'Đơn hàng đang chờ xử lý'}</strong><dl><dt>Trạng thái</dt><dd>{result.status}</dd><dt>Mã đơn</dt><dd>{result.id}</dd><dt>Số tiền</dt><dd>{money(result.amount)}</dd><dt>Hoàn tiền (refunded)</dt><dd>{result.refunded ? 'Đã hoàn tiền (true)' : 'Không (false)'}</dd>{result.reason && <><dt>Lý do (reason)</dt><dd>{result.reason === 'OUTSIDE_DELIVERY_ZONE' ? 'Địa chỉ ngoài vùng giao hàng (OUTSIDE_DELIVERY_ZONE)' : result.reason}</dd></>}{result.traceId && <><dt>Mã tra cứu</dt><dd>{result.traceId}</dd></>}</dl></div>}</div>
        </section>
      </div>
    </main><footer><a className="brand" href="/">MiniMart.</a><p>Phiên bản demo · Không cần đăng nhập · Không thanh toán thật</p><span>Mua sắm nhẹ nhàng hơn.</span></footer>
  </>;
}

