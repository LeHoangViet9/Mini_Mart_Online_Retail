"""End-to-end assertions through gateway. Uses the running Compose stack by default.
--local launches five locally built -Plocal-test jars with isolated H2 databases.
No global installation or extra Docker container is needed.
"""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--local', action='store_true')
parser.add_argument('--postgres', action='store_true', help='Local apps use four databases at localhost:15432; passwords from DB_TEST_PASSWORD')
parser.add_argument('--base', default='http://localhost:8080')
args = parser.parse_args()
base = 'http://localhost:18080' if args.local else args.base
processes, files, logs = [], [], {}

def call(method, path, data=None, expected=200):
    payload = None if data is None else json.dumps(data).encode()
    request = urllib.request.Request(base + path, data=payload, method=method,
                                     headers={'Content-Type': 'application/json'})
    try:
        response = urllib.request.urlopen(request, timeout=45)
    except urllib.error.HTTPError as error:
        response = error
    raw = response.read()
    assert response.code == expected, (method, path, response.code, raw)
    return json.loads(raw) if raw else None

try:
    if args.local:
        java_home = os.environ.get('JAVA_HOME')
        java = str(Path(java_home) / 'bin' / 'java') if java_home else 'java'
        ports = {'gateway': 18080, 'catalog-service': 18081, 'customer-service': 18082,
                 'order-service': 18083, 'shipping-service': 18084}
        target = ROOT / '.tools' / 'smoke'
        target.mkdir(parents=True, exist_ok=True)
        for name, port in ports.items():
            env = dict(os.environ, PORT=str(port), DB_PASSWORD='test', DB_USER='sa',
                       DB_URL=f'jdbc:h2:mem:{name};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1')
            if args.postgres and name != 'gateway':
                service = name.split('-')[0]
                env.update(DB_URL=f'jdbc:postgresql://localhost:15432/minimart_{service}_db',
                           DB_USER='orders' if service == 'order' else service,
                           DB_PASSWORD=os.environ['DB_TEST_PASSWORD'])
            for other, other_port in ports.items():
                env[other.split('-')[0].upper() + '_URL'] = f'http://localhost:{other_port}'
            log = target / (name + '.log')
            logs[name] = log
            stream = log.open('w', encoding='utf-8')
            files.append(stream)
            processes.append(subprocess.Popen([java, '-Xmx192m', '-jar',
                str(ROOT / 'services' / name / 'target' / (name + '-0.1.0.jar'))],
                env=env, stdout=stream, stderr=subprocess.STDOUT))

    deadline = time.monotonic() + 120
    while True:
        try:
            call('GET', '/api/products/1')
            call('GET', '/api/customers/1')
            call('GET', '/api/orders/00000000-0000-0000-0000-000000000000', expected=404)
            call('PUT', '/api/shipping/rejection', {'rejectOutsideZone': False})
            break
        except (OSError, AssertionError):
            if time.monotonic() > deadline or any(p.poll() is not None for p in processes):
                raise
            time.sleep(1)

    order = {'customerId': 1, 'productId': 1, 'quantity': 2,
             'address': 'Demo outside address', 'zone': 'OUTSIDE'}
    before = call('GET', '/api/customers/1')['balance']
    stock = call('GET', '/api/products/1')['stock']
    success = call('POST', '/api/orders', order)
    assert success['status'] == 'CONFIRMED' and not success['refunded'], success
    assert call('GET', '/api/customers/1')['balance'] == before - 50000
    assert call('GET', '/api/products/1')['stock'] == stock - 2
    call('PUT', '/api/shipping/rejection', {'rejectOutsideZone': True})
    before_cancel = call('GET', '/api/customers/1')['balance']
    cancelled = call('POST', '/api/orders', order)
    assert cancelled['status'] == 'CANCELLED' and cancelled['refunded'], cancelled
    assert cancelled['reason'] == 'OUTSIDE_DELIVERY_ZONE'
    assert call('GET', '/api/customers/1')['balance'] == before_cancel
    assert call('GET', '/api/products/1')['stock'] == stock - 2
    assert call('GET', '/api/orders/' + cancelled['id']) == cancelled
    local_order = dict(order, zone='LOCAL')
    assert call('POST', '/api/orders', local_order)['status'] == 'CONFIRMED'
    call('POST', '/api/orders', dict(order, quantity=0), expected=400)
    call('POST', '/api/orders', dict(order, productId=99999), expected=409)
    call('POST', '/internal/payments', {}, expected=404)
    call('GET', '/api/shipping/internal/shipments', expected=404)
    # Concurrent rejections must not lose wallet updates or inventory releases.
    concurrent_balance = call('GET', '/api/customers/1')['balance']
    concurrent_stock = call('GET', '/api/products/1')['stock']
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(lambda _: call('POST', '/api/orders', order), range(8)))
    assert all(r['status'] == 'CANCELLED' and r['refunded'] for r in results)
    assert call('GET', '/api/customers/1')['balance'] == concurrent_balance
    assert call('GET', '/api/products/1')['stock'] == concurrent_stock
    if args.local:
        from refactor_api_contracts import check_internal_contracts
        check_internal_contracts()
        for port in (18081, 18082, 18083, 18084):
            with urllib.request.urlopen(f'http://localhost:{port}/actuator/prometheus') as response:
                assert response.code == 200 and b'jvm_memory' in response.read()
        parsed = {}
        for name, path in logs.items():
            parsed[name] = []
            for line in path.read_text(encoding='utf-8').splitlines():
                try:
                    entry = json.loads(line)
                    if entry.get('trace', {}).get('id') == cancelled['traceId']:
                        parsed[name].append(entry)
                except json.JSONDecodeError:
                    pass
        for name in ('catalog-service', 'customer-service', 'order-service', 'shipping-service'):
            assert parsed[name], (name, 'missing trace')
        source = next(e for e in parsed['shipping-service'] if e.get('log', {}).get('level') == 'WARN')
        consequence = next(e for e in parsed['order-service'] if e.get('log', {}).get('level') == 'WARN')
        assert 'stack_trace' not in source.get('error', {}), source
        assert 'stack_trace' in consequence.get('error', {}), consequence
        from datetime import datetime
        delta = (datetime.fromisoformat(consequence['@timestamp']) -
                 datetime.fromisoformat(source['@timestamp'])).total_seconds() * 1000
        assert delta >= 0
        print(f'WARN trace={cancelled["traceId"]} shipping -> order: {delta:.3f} ms')
    print('PASS: CONFIRMED, CANCELLED HTTP 200, refund, stock release, persistence, LOCAL zone, validation, gateway boundary, 8 concurrent cancellations')
    print(json.dumps({'confirmed': success, 'cancelled': cancelled}, indent=2))
finally:
    for process in processes:
        process.terminate()
    for process in processes:
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
    for stream in files:
        stream.close()
