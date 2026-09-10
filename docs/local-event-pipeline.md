# Kafka for local JVM development

When running the services in IntelliJ with the `local` profile and Vite on port
5173, start Kafka as well. Starting the Spring applications does not start a
broker. Opening Audit or Analytics does not generate events.

Start Docker Desktop, then run these commands from the repository root:

```powershell
docker compose -f docker-compose.kafka-local.yml config --quiet
docker compose -f docker-compose.kafka-local.yml up -d --wait --wait-timeout 180 kafka
docker compose -f docker-compose.kafka-local.yml run --rm kafka-init
docker compose -f docker-compose.kafka-local.yml ps -a
Test-NetConnection localhost -Port 9092
docker compose -f docker-compose.kafka-local.yml exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list
```

This file starts only Kafka and its topic initializer. It uses the same broker
version and topic topology as the full application Compose stack. It requires no
database, JWT, or SMTP secrets and does not start or replace the local databases
or Java services. Its named Kafka volume belongs to a separate Compose project.
The host listener binds only to `127.0.0.1:9092` and advertises `localhost:9092`.
Another broker must not already own that host port.

Flag, Audit, and Analytics use `spring.kafka.bootstrap-servers=localhost:9092`
with their local profiles. Check any IntelliJ `SPRING_KAFKA_BOOTSTRAP_SERVERS`
override against that address. Docker-profile services in the full stack use
`kafka:29092`; that hostname is internal to their Docker network. The separate
stabilization fixture can publish Kafka on a different host port and is not this
local JVM setup.

The initializer waits for broker health and creates only missing topics:

| Topic | Consumer group |
| --- | --- |
| `feature-flag-events` | `audit-group`, `analytics-group` |
| `feature-flag-evaluations` | `analytics-evaluation-group` |
| `notification-events` | `notification-service-group` |
| `feature-flag-events-audit-dlt` | Audit failures |
| `feature-flag-events-analytics-dlt` | Analytics failures |
| `notification-events-dlt` | Notification failures |

Create, update, toggle, and delete a disposable `DEV` flag through the authenticated
UI/API. Lifecycle rows in `flag_db.outbox_events` should progress from `PENDING`
to `PUBLISHED`, then appear in Audit history and Analytics aggregates. Evaluations
produce `EVALUATION_ENABLED` or `EVALUATION_DISABLED` analytics; they do not create
Audit lifecycle records. JWT authorization remains required for the query APIs.

If publication fails, inspect outbox metadata (`id`, `topic`, `status`, `attempts`,
`last_error_type`, `published_at`) before inspecting consumers. Kafka connection
warnings and a pending or dead outbox row identify a failure before the consumer
databases. Existing `PENDING` rows retry normally after recovery. `DEAD` rows have
exhausted their retry budget and are deliberately not retried automatically.
Restoring Kafka does not backfill those rows: keep them for reviewed recovery;
do not reset all rows, delete processed-event markers, or manufacture history.
Best-effort evaluation telemetry lost during an outage is not replayed.

For consumer diagnosis, inspect groups without resetting their offsets:

```powershell
docker compose -f docker-compose.kafka-local.yml exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:29092 --describe --group audit-group
docker compose -f docker-compose.kafka-local.yml exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:29092 --describe --group analytics-group
docker compose -f docker-compose.kafka-local.yml exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:29092 --describe --group analytics-evaluation-group
```

Check the relevant DLT if publishing succeeds but a database remains empty. Use a
separate diagnostic consumer group when inspecting events. Notification delivery
has its own recipient-service credential and SMTP dependencies; its failure is
separate from lifecycle publication to Audit and Analytics.

Stop this broker while retaining its data:

```powershell
docker compose -f docker-compose.kafka-local.yml stop
```

Run the focused startup configuration regression checks (Docker CLI and Node are
required; the daemon need not be running):

```powershell
node --test scripts/local-kafka.test.mjs
```
