"""Additional regression assertions for existing internal APIs; no new application endpoints.

Called by smoke.py while its five local applications are running. All temporary
reservations/payments are released/refunded; the existing shipping setting is restored.
"""
import json
import urllib.error
import urllib.request
import uuid


def check_internal_contracts():
    def call(port, method, path, body=None, expected=200):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(f'http://localhost:{port}{path}', method=method,
                                        data=data, headers={'Content-Type': 'application/json'})
        try:
            response = urllib.request.urlopen(request, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        raw = response.read()
        assert response.code == expected, (path, response.code, raw)
        return json.loads(raw) if raw else None

    product = call(18081, 'GET', '/api/products/1')
    customer = call(18082, 'GET', '/api/customers/1')
    assert set(product) == {'id', 'name', 'price', 'stock'}
    assert set(customer) == {'id', 'name', 'balance'}
    assert call(18081, 'GET', '/api/products/99999', expected=404) == {'reason': 'PRODUCT_NOT_FOUND'}
    assert call(18082, 'GET', '/api/customers/99999', expected=404) == {'reason': 'CUSTOMER_NOT_FOUND'}
    assert call(18082, 'POST', f'/internal/payments/{uuid.uuid4()}/refund', expected=404) == {'reason': 'PAYMENT_NOT_FOUND'}

    order_id = str(uuid.uuid4())
    reservation = {'orderId': order_id, 'productId': 1, 'quantity': 3}
    assert call(18081, 'POST', '/internal/reservations', reservation) == {'amount': 75000}
    assert call(18081, 'POST', '/internal/reservations', reservation) == {'amount': 75000}
    assert call(18081, 'GET', '/api/products/1')['stock'] == product['stock'] - 3
    assert call(18081, 'POST', '/internal/reservations', dict(reservation, quantity=4), 409) == {'reason': 'RESERVATION_CONFLICT'}
    for _ in range(2):
        assert call(18081, 'POST', f'/internal/reservations/{order_id}/release') is None
    assert call(18081, 'GET', '/api/products/1')['stock'] == product['stock']
    assert call(18081, 'POST', '/internal/reservations', reservation, 409) == {'reason': 'RESERVATION_CONFLICT'}
    assert call(18081, 'POST', f'/internal/reservations/{uuid.uuid4()}/release') is None

    payment = {'orderId': order_id, 'customerId': 1, 'amount': 1000}
    for _ in range(2):
        assert call(18082, 'POST', '/internal/payments', payment) is None
    assert call(18082, 'GET', '/api/customers/1')['balance'] == customer['balance'] - 1000
    assert call(18082, 'POST', '/internal/payments', dict(payment, amount=1001), 409) == {'reason': 'PAYMENT_CONFLICT'}
    for _ in range(2):
        assert call(18082, 'POST', f'/internal/payments/{order_id}/refund') is None
    assert call(18082, 'GET', '/api/customers/1')['balance'] == customer['balance']
    assert call(18082, 'POST', '/internal/payments', payment, 409) == {'reason': 'PAYMENT_CONFLICT'}
    assert call(18082, 'POST', '/internal/payments', dict(payment, orderId=str(uuid.uuid4()), amount=customer['balance'] + 1), 409) == {'reason': 'INSUFFICIENT_BALANCE'}
    assert call(18082, 'GET', '/api/customers/1')['balance'] == customer['balance']

    # Existing definite rejection path must release the stock reserved before debit.
    invalid_customer = {'customerId': 99999, 'productId': 1, 'quantity': 1,
                        'address': 'Demo', 'zone': 'LOCAL'}
    error = call(18083, 'POST', '/api/orders', invalid_customer, 409)
    assert error['reason'].startswith('BUSINESS_REJECTED orderId=')
    rejected_id = error['reason'].split('orderId=', 1)[1]
    rejected = call(18083, 'GET', '/api/orders/' + rejected_id)
    assert set(rejected) == {'id', 'status', 'amount', 'refunded', 'reason', 'traceId'}
    assert rejected['status'] == 'CANCELLED' and not rejected['refunded']
    assert rejected['reason'] == 'BUSINESS_REJECTED'
    assert call(18081, 'GET', '/api/products/1')['stock'] == product['stock']

    shipment = {'orderId': str(uuid.uuid4()), 'address': 'Demo', 'zone': 'OUTSIDE'}
    assert call(18084, 'PUT', '/api/shipping/rejection', {'rejectOutsideZone': True}) == {'rejectOutsideZone': True}
    assert call(18084, 'POST', '/internal/shipments', shipment, 422) == {'reason': 'OUTSIDE_DELIVERY_ZONE'}
    local = dict(shipment, zone='LOCAL')
    created = {'orderId': shipment['orderId'], 'status': 'CREATED'}
    assert call(18084, 'POST', '/internal/shipments', local) == created
    assert call(18084, 'POST', '/internal/shipments', local) == created
    assert call(18084, 'PUT', '/api/shipping/rejection', {'rejectOutsideZone': False}) == {'rejectOutsideZone': False}
    assert call(18084, 'POST', '/internal/shipments', dict(shipment, orderId=str(uuid.uuid4())))['status'] == 'CREATED'
    call(18084, 'PUT', '/api/shipping/rejection', {'rejectOutsideZone': True})

    # All original request constraints remain at the HTTP boundary.
    call(18081, 'POST', '/internal/reservations', dict(reservation, orderId=None), 400)
    call(18081, 'POST', '/internal/reservations', dict(reservation, quantity=101), 400)
    call(18082, 'POST', '/internal/payments', dict(payment, amount=0), 400)
    call(18084, 'PUT', '/api/shipping/rejection', {'rejectOutsideZone': None}, 400)
    call(18084, 'POST', '/internal/shipments', dict(shipment, address=' '), 400)
    call(18084, 'POST', '/internal/shipments', dict(shipment, zone='UNKNOWN'), 400)
    print('PASS: internal API JSON/status contracts, duplicate reserve/release/debit/refund, insufficient balance, pre-debit cancellation, shipping toggle, validation', flush=True)
