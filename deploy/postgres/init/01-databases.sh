#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=catalog_password="$CATALOG_DB_PASSWORD" \
  --set=customer_password="$CUSTOMER_DB_PASSWORD" \
  --set=order_password="$ORDER_DB_PASSWORD" \
  --set=shipping_password="$SHIPPING_DB_PASSWORD" <<'SQL'
CREATE ROLE catalog LOGIN PASSWORD :'catalog_password';
CREATE ROLE customer LOGIN PASSWORD :'customer_password';
CREATE ROLE orders LOGIN PASSWORD :'order_password';
CREATE ROLE shipping LOGIN PASSWORD :'shipping_password';
CREATE DATABASE minimart_catalog_db OWNER catalog;
CREATE DATABASE minimart_customer_db OWNER customer;
CREATE DATABASE minimart_order_db OWNER orders;
CREATE DATABASE minimart_shipping_db OWNER shipping;
REVOKE ALL ON DATABASE minimart_catalog_db FROM PUBLIC;
REVOKE ALL ON DATABASE minimart_customer_db FROM PUBLIC;
REVOKE ALL ON DATABASE minimart_order_db FROM PUBLIC;
REVOKE ALL ON DATABASE minimart_shipping_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;
REVOKE CONNECT ON DATABASE template1 FROM PUBLIC;
REVOKE CONNECT ON DATABASE minimart_admin FROM PUBLIC;
SQL
