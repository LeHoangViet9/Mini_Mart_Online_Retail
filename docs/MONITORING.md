# Prometheus and Grafana

MiniMart services already expose Spring Boot Actuator metrics at `/actuator/prometheus`. Compose starts Prometheus and Grafana on the private backend network and provisions a six-panel dashboard at startup.

## Local setup

1. Add a strong `GRAFANA_ADMIN_PASSWORD` and `MONITORING_BIND_ADDRESS=127.0.0.1` to `deploy/.env`. Do not commit this file.
2. Start the stack from the repository root:

   ```powershell
   docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
   ```

3. Open `http://localhost:3000`, sign in with the configured Grafana username/password, and select **Dashboards → MiniMart → MiniMart Service Overview**.
4. Open `http://localhost:9090/targets` and confirm `gateway`, `catalog-service`, `customer-service`, `order-service`, and `shipping-service` show **UP**.

The dashboard shows request rate, HTTP 5xx ratio, average response time, JVM heap use, process CPU, and scrape health. Metrics have a 15-second scrape interval and a 7-day retention period.

## EC2 access

The default bind address is `127.0.0.1`, so ports 3000 and 9090 are not exposed to the public internet. Keep this default. For a browser demo on EC2, use an authenticated tunnel or later put Grafana behind the HTTPS reverse proxy. Do not add public security-group rules for Prometheus. Do not paste Grafana credentials into source control or chat.

After deployment, inspect target health from the EC2 shell:

```bash
curl -fsS http://localhost:9090/api/v1/targets
```

Do not claim the dashboard is operational until all five application scrape targets are `UP` and the six panels display real samples. A graph with no traffic can be empty until API requests are made and Prometheus has scraped the resulting counters.
