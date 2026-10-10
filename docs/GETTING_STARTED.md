# Local Development

[Project overview](../README.md) · [Production operations](DEPLOYMENT.md)

### Prerequisites

- Java 21
- Maven 3.9.x
- Node.js 24 and npm
- Docker with Compose v2
- OpenSSL or another RSA key-generation tool
- SMTP credentials for the normal Compose configuration; the [disposable E2E suite](../scripts/e2e/README.md) provides a local SMTP sink

CI/container frontend builds currently pin Node `24.18.1`. Docker builds supply the Java and Node toolchains; host Java/Maven/Node are needed for direct development and tests.

### Configuration

Copy the example environment file:

```bash
cp .env.example .env
```

PowerShell:

```powershell
Copy-Item .env.example .env
```

Fill the required values.

**Never commit `.env`, PEM private keys, database passwords, SMTP credentials, SDK keys, or internal service credentials.**

Important settings:

| Configuration | Purpose |
|---|---|
| `MYSQL_ROOT_PASSWORD` | Local MySQL provisioning |
| `*_DB_USERNAME`, `*_DB_PASSWORD` | Service-specific DB credentials |
| `AUTH_JWT_PRIVATE_KEY_FILE` | Auth private RSA key for Compose |
| `JWT_PUBLIC_KEY_FILE` | Public key mounted into resource services |
| `JWT_ISSUER` | JWT issuer |
| `JWT_AUDIENCE` | JWT audience |
| `JWT_KEY_ID` | JWT key identifier |
| `JWT_ACCESS_TOKEN_TTL` | Token lifetime |
| `AUTH_INTERNAL_SERVICE_KEY` | Current account status/role lookup; defaults to recipient key |
| `AUTH_RECIPIENTS_SERVICE_KEY` | Flag -> Auth recipient lookup |
| `NOTIFICATION_INTERNAL_SERVICE_KEY` | Auth -> Notification invitation delivery |
| `NOTIFICATION_MAIL_USERNAME` | SMTP username |
| `NOTIFICATION_MAIL_PASSWORD` | SMTP password |
| `FRONTEND_BASE_URL` | Public browser origin used in invitation links |
| `FRONTEND_PORT` | Public frontend port in Compose |
| `BOOTSTRAP_OWNER_*` | Explicit first-run owner provisioning |

### RSA Key Generation

Generate keys in a private directory outside the repository. Run the following from that directory:

```bash
openssl genpkey \
  -algorithm RSA \
  -pkeyopt rsa_keygen_bits:2048 \
  -out jwt-private.pem

openssl pkey \
  -in jwt-private.pem \
  -pubout \
  -out jwt-public.pem
```

Configure the environment variables with absolute host paths. On Linux, narrowly grant the backend container UID/GID `10001` read access to the private key; see the [production permissions instructions](DEPLOYMENT.md).

For a fresh Auth database, enable owner bootstrap only long enough to create the initial owner and then disable it.

### Run with Docker Compose

Validate:

```bash
docker compose config --quiet
```

Start:

```bash
docker compose up --build -d
```

Check status:

```bash
docker compose ps
```

Open:

```text
http://localhost:3000
```

or the configured `FRONTEND_PORT`.

Stop while preserving local volumes:

```bash
docker compose stop
```

The Compose environment includes MySQL, Redis, Kafka, Eureka, backend services, and the Nginx-served frontend.

### Run Services from the JVM

For IntelliJ/terminal development, provide local MySQL, Redis on `6379`, Kafka on `9092`, and Eureka on `8761`.

Start Kafka for local JVM services:

```bash
docker compose -f docker-compose.kafka-local.yml up -d --wait --wait-timeout 180 kafka
docker compose -f docker-compose.kafka-local.yml run --rm kafka-init
```

Maven does **not** automatically load the root `.env` into independently launched services. Configure required variables per JVM/run configuration.

Recommended startup order:

```text
1. Eureka
2. Auth
3. Flag
4. Audit
5. Analytics
6. Notification
7. API Gateway
8. Frontend
```

Example:

```bash
mvn -f eureka-server/eureka-server/pom.xml spring-boot:run

mvn -f auth-service/auth-service/pom.xml \
  spring-boot:run \
  "-Dspring-boot.run.profiles=local"
```

When using IntelliJ, one service does not inherit another service's environment variables. Shared internal credentials must therefore be configured consistently on both sides of an internal call.

### Run the Frontend

```bash
cd Frontend/feature-flag-ui
npm ci
npm run dev
```

Vite runs on:

```text
http://localhost:5174
```

and proxies local API requests to Gateway.

### Vite origin configuration

Vite uses port `5174` with `strictPort`. Some backend defaults still reference `5173`, so explicitly set `FRONTEND_BASE_URL=http://localhost:5174` and include `http://localhost:5174` in `CORS_ALLOWED_ORIGINS` for direct JVM development. Set these variables in every relevant run configuration; Maven does not read `.env` automatically.
