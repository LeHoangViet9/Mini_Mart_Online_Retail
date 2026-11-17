CREATE TABLE IF NOT EXISTS shipping_settings(id INTEGER PRIMARY KEY, reject_outside BOOLEAN NOT NULL);
INSERT INTO shipping_settings(id,reject_outside) SELECT 1,FALSE WHERE NOT EXISTS(SELECT 1 FROM shipping_settings WHERE id=1);
CREATE TABLE IF NOT EXISTS shipments(order_id UUID PRIMARY KEY, address VARCHAR(300) NOT NULL, zone VARCHAR(30) NOT NULL);
