"""Windows fallback when Docker is unavailable. Uses existing JDK/PostgreSQL only.
Creates an isolated PostgreSQL cluster inside .tools; does not touch installed clusters.
Usage: python scripts/verify-local-postgres.py --pg-bin "C:/Program Files/PostgreSQL/18/bin"
Build normal jars with mvn package first. This is not a Docker validation.
"""
import argparse
import os
from pathlib import Path
import secrets
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--pg-bin', required=True)
args = parser.parse_args()
bin_dir = Path(args.pg_bin)
directory = ROOT / '.tools' / ('postgres-test-' + secrets.token_hex(4))
directory.mkdir(parents=True)
data = directory / 'data'
password = secrets.token_hex(20)
pwfile = directory / 'password'
pwfile.write_text(password, encoding='utf-8')
env = dict(os.environ, PGPASSWORD=password, DB_TEST_PASSWORD=password)

def run(program, *arguments, **kwargs):
    # pg_ctl can leave pipe handles inherited by postgres on Windows.
    # File output lets subprocess wait for pg_ctl itself instead of pipe EOF.
    output = directory / (program + '-output.log')
    with output.open('w', encoding='utf-8') as stream:
        result = subprocess.run([str(bin_dir / (program + '.exe')), *map(str, arguments)],
                                env=env, stdout=stream, stderr=subprocess.STDOUT,
                                creationflags=subprocess.CREATE_NO_WINDOW, **kwargs)
    print(output.read_text(encoding='utf-8', errors='replace'), flush=True)
    result.check_returncode()
    return result

started = False
try:
    run('initdb', '-D', data, '-U', 'minimart_admin', '--pwfile', pwfile,
        '--auth=scram-sha-256', '--encoding=UTF8', '--locale=C')
    run('pg_ctl', '-D', data, '-l', directory / 'postgres.log',
        '-o', '-p 15432 -h 127.0.0.1', '-w', 'start')
    started = True
    run('psql', '-h', '127.0.0.1', '-p', '15432', '-U', 'minimart_admin', '-d', 'postgres',
        '-v', 'ON_ERROR_STOP=1', '-c', 'CREATE DATABASE minimart_admin')
    # Reuse the exact SQL body from Docker's initialization script.
    init = (ROOT / 'deploy/postgres/init/01-databases.sh').read_text(encoding='utf-8')
    sql = init.split("<<'SQL'\n", 1)[1].rsplit('\nSQL', 1)[0]
    sqlfile = directory / 'init.sql'
    sqlfile.write_text(sql, encoding='utf-8')
    run('psql', '-h', '127.0.0.1', '-p', '15432', '-U', 'minimart_admin', '-d', 'minimart_admin',
        '-v', 'ON_ERROR_STOP=1', '-v', 'catalog_password=' + password,
        '-v', 'customer_password=' + password, '-v', 'order_password=' + password,
        '-v', 'shipping_password=' + password, '-f', sqlfile)
    databases = ['minimart_' + name + '_db' for name in ('catalog', 'customer', 'order', 'shipping')]
    for role, own in zip(('catalog', 'customer', 'orders', 'shipping'), databases):
        for database in databases:
            result = subprocess.run([str(bin_dir / 'psql.exe'), '-h', '127.0.0.1', '-p', '15432',
                '-U', role, '-d', database, '-c', 'SELECT current_database()'],
                env=env, capture_output=True, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
            assert (result.returncode == 0) == (own == database), (role, database, result.stderr)
    print('PASS: 4 own-database connections allowed; 12 cross-database connections denied', flush=True)
    subprocess.run([sys.executable, str(ROOT / 'scripts/smoke.py'), '--local', '--postgres'],
                   env=env, check=True)
finally:
    if started:
        run('pg_ctl', '-D', data, '-m', 'fast', '-w', 'stop')
    pwfile.unlink(missing_ok=True)
