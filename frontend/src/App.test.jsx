import React from 'react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from './App';

const product = { id: 1, name: 'Gạo thơm', price: 25000, stock: 1000 };
const response = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', 'X-Trace-Id': 'trace-demo' } });
afterEach(() => vi.unstubAllGlobals());
function mockApi(order, status = 200) {
  const mock = vi.fn(async url => url === '/api/products/1' ? response(product) : response(order, status));
  vi.stubGlobal('fetch', mock);
  return mock;
}
async function placeOrder(zone = 'LOCAL') {
  const user = userEvent.setup();
  await screen.findByText('Gạo thơm');
  await user.type(screen.getByLabelText(/Địa chỉ nhận hàng/), '12 Nguyễn Trãi');
  await user.selectOptions(screen.getByLabelText(/Khu vực giao hàng/), zone);
  await user.click(screen.getByRole('button', { name: /^Đặt hàng/ }));
  return user;
}
describe('MiniMart', () => {
  it('shows loading, product error, and reload', async () => {
    let resolve;
    vi.stubGlobal('fetch', vi.fn().mockImplementationOnce(() => new Promise(r => { resolve = r; })).mockResolvedValueOnce(response(product)));
    render(<App />);
    expect(screen.getByRole('status').textContent).toContain('Đang tải');
    resolve(response({}, 502));
    await screen.findByRole('alert');
    await userEvent.click(screen.getByRole('button', { name: /Tải lại/ }));
    await screen.findByText('Gạo thơm');
  });
  it('sends the exact contract and shows CONFIRMED', async () => {
    const mock = mockApi({ id: 'order-1', status: 'CONFIRMED', amount: 25000, refunded: false });
    render(<App />);
    await placeOrder();
    await screen.findByText('Đặt hàng thành công');
    const [, options] = mock.mock.calls.find(([url]) => url === '/api/orders');
    expect(options.method).toBe('POST');
    expect(JSON.parse(options.body)).toEqual({ customerId: 1, productId: 1, quantity: 1, address: '12 Nguyễn Trãi', zone: 'LOCAL' });
    expect(screen.getByText('CONFIRMED')).toBeTruthy();
  });
  it.each([true, false])('shows CANCELLED with reason and refunded=%s', async refunded => {
    mockApi({ id: 'order-2', status: 'CANCELLED', amount: 25000, refunded, reason: 'OUTSIDE_DELIVERY_ZONE' });
    render(<App />);
    await placeOrder('OUTSIDE');
    await screen.findByText('Đơn hàng đã bị hủy');
    expect(screen.getByText(/Địa chỉ ngoài vùng giao hàng/)).toBeTruthy();
    expect(screen.getByText(refunded ? 'Đã hoàn tiền (true)' : 'Không (false)')).toBeTruthy();
  });
  it.each([400, 409, 503])('preserves form and reconciliation detail after HTTP %s', async status => {
    mockApi({ reason: 'RECONCILIATION_REQUIRED orderId=demo-id' }, status);
    render(<App />);
    await placeOrder('OUTSIDE');
    await screen.findByRole('alert');
    expect(screen.getByLabelText(/Địa chỉ nhận hàng/).value).toBe('12 Nguyễn Trãi');
    expect(screen.getByLabelText(/Khu vực giao hàng/).value).toBe('OUTSIDE');
    expect(screen.getByText(/orderId=demo-id/)).toBeTruthy();
    expect(screen.getByRole('button', { name: /^Đặt hàng/ }).disabled).toBe(false);
  });
  it('prevents duplicate submissions while pending', async () => {
    let finish;
    const mock = mockApi(null);
    mock.mockImplementation(url => url === '/api/products/1' ? Promise.resolve(response(product)) : new Promise(resolve => { finish = resolve; }));
    render(<App />);
    const user = await placeOrder();
    const button = screen.getByRole('button', { name: /Đang xử lý/ });
    expect(button.disabled).toBe(true);
    await user.click(button);
    expect(mock.mock.calls.filter(([url]) => url === '/api/orders')).toHaveLength(1);
    finish(response({ id: 'order-3', status: 'CONFIRMED', amount: 25000, refunded: false }));
    await screen.findByText('Đặt hàng thành công');
  });
  it('preserves data after network failure', async () => {
    const mock = mockApi(null);
    mock.mockImplementation(url => url === '/api/products/1' ? Promise.resolve(response(product)) : Promise.reject(new TypeError('Failed to fetch')));
    render(<App />);
    await placeOrder();
    await screen.findByRole('alert');
    expect(screen.getByLabelText(/Địa chỉ nhận hàng/).value).toBe('12 Nguyễn Trãi');
    expect(screen.getByText(/liên hệ người hướng dẫn trước khi thử lại/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Kiểm tra đơn hàng' })).toBeNull();
    expect(mock.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
  });
  it('blocks quantity outside the API limit', async () => {
    const mock = mockApi(null);
    render(<App />);
    const user = userEvent.setup();
    await screen.findByText('Gạo thơm');
    await user.type(screen.getByLabelText(/Địa chỉ nhận hàng/), 'Địa chỉ');
    const quantity = screen.getByLabelText(/Số lượng/);
    await user.clear(quantity);
    await user.type(quantity, '101');
    await user.click(screen.getByRole('button', { name: /^Đặt hàng/ }));
    expect(mock.mock.calls.filter(([url]) => url === '/api/orders')).toHaveLength(0);
    expect(quantity.validity.rangeOverflow).toBe(true);
  });

  it('reconciles PENDING using only GET with the received ID', async () => {
    const id = 'ddc3818b-52db-4bad-bac2-aa47c72c6895';
    let finish;
    const mock = mockApi({ id, status: 'PENDING', amount: 25000, refunded: false });
    render(<App />);
    const user = await placeOrder();
    await screen.findByText('Đơn hàng cần đối soát');
    expect(screen.getByText(/liên hệ người hướng dẫn trước khi thử lại/)).toBeTruthy();
    mock.mockImplementation(url => url === `/api/orders/${id}` ? new Promise(resolve => { finish = resolve; }) : Promise.resolve(response(product)));
    await user.click(screen.getByRole('button', { name: 'Kiểm tra đơn hàng' }));
    expect(screen.getByRole('button', { name: 'Kiểm tra đơn hàng' }).disabled).toBe(true);
    expect(screen.getByRole('button', { name: /^Đặt hàng/ }).disabled).toBe(true);
    expect(screen.getByText('Đang kiểm tra đơn hàng…')).toBeTruthy();
    finish(response({ id, status: 'CONFIRMED', amount: 25000, refunded: false }));
    await screen.findByText('Đặt hàng thành công');
    expect(mock.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
    expect(mock.mock.calls.every(([url]) => ['/api/products/1', '/api/orders', `/api/orders/${id}`].includes(url))).toBe(true);
  });

  it('uses an explicit orderId in a 503 error for lookup, preserving trace', async () => {
    const id = 'ddc3818b-52db-4bad-bac2-aa47c72c6895';
    const mock = mockApi({ reason: `RECONCILIATION_REQUIRED orderId=${id}` }, 503);
    render(<App />);
    const user = await placeOrder();
    await screen.findByRole('alert');
    expect(screen.getByText(/Mã tra cứu: trace-demo/)).toBeTruthy();
    mock.mockImplementation(url => url === `/api/orders/${id}` ? Promise.resolve(response({ reason: 'ORDER_NOT_FOUND' }, 404)) : Promise.resolve(response(product)));
    await user.click(screen.getByRole('button', { name: 'Kiểm tra đơn hàng' }));
    await screen.findByText('Không tìm thấy đơn hàng.');
    expect(mock.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
  });

  it.each([502, 503, 504])('does not infer an ID or retry POST after HTTP %s', async status => {
    const mock = mockApi({}, status);
    render(<App />);
    await placeOrder();
    await screen.findByRole('alert');
    expect(screen.getByText(/kết quả chưa xác định/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Kiểm tra đơn hàng' })).toBeNull();
    expect(mock.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
  });

  it('does not present a malformed successful response as a completed order', async () => {
    const mock = mockApi({});
    render(<App />);
    await placeOrder();
    await screen.findByRole('alert');
    expect(screen.getByText(/Phản hồi từ cửa hàng không hợp lệ/)).toBeTruthy();
    expect(screen.queryByText('Đặt hàng thành công')).toBeNull();
    expect(mock.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
  });
});
