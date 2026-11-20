import { afterEach, describe, expect, it, vi } from 'vitest';
import { request } from './api';

afterEach(() => vi.unstubAllGlobals());

describe('gateway API errors', () => {
  it.each([
    [404, 'PRODUCT_NOT_FOUND', 'Không tìm thấy sản phẩm.'],
    [404, 'CUSTOMER_NOT_FOUND', 'Không tìm thấy khách hàng.'],
    [409, 'INSUFFICIENT_STOCK', 'Sản phẩm không đủ tồn kho.'],
    [409, 'INSUFFICIENT_BALANCE', 'Ví khách hàng không đủ tiền.'],
    [409, 'BUSINESS_REJECTED', 'backend chưa cung cấp nguyên nhân cụ thể'],
  ])('translates HTTP %s %s while retaining diagnostics', async (status, reason, message) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ reason }), { status, headers: { 'X-Trace-Id': 'trace-test' } })));
    const error = await request('/orders', { method: 'POST' }).catch(error => error);
    expect(error.message).toContain(message);
    expect(error.detail).toBe(reason);
    expect(error.traceId).toBe('trace-test');
  });

  it('reports a POST timeout as uncertain without retrying or inventing an ID', async () => {
    const mock = vi.fn().mockRejectedValue(new DOMException('Timeout', 'TimeoutError'));
    vi.stubGlobal('fetch', mock);
    const error = await request('/orders', { method: 'POST' }).catch(error => error);
    expect(error.message).toContain('cần đối soát');
    expect(error.orderId).toBeUndefined();
    expect(mock).toHaveBeenCalledTimes(1);
  });

  it('handles a gateway HTML error and preserves its trace header', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502, headers: { 'X-Trace-Id': 'trace-html' } })));
    const error = await request('/orders', { method: 'POST' }).catch(error => error);
    expect(error.message).toContain('Gateway');
    expect(error.message).toContain('cần đối soát');
    expect(error.traceId).toBe('trace-html');
  });
});
