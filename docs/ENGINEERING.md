# Engineering Reference

[Project overview](../README.md) · [Local setup](GETTING_STARTED.md) · [Production operations](DEPLOYMENT.md)


## Core Domain Behavior

### Flag Evaluation

A flag is evaluated using these rules:

1. The flag must exist in the SDK key's environment.
2. The flag must be enabled.
3. The current time must be inside the configured schedule, if present.
4. Explicitly targeted subjects are enabled after the global flag/schedule checks pass.
5. Other subjects are assigned a deterministic rollout bucket.
6. The configured rollout percentage decides the final result.

The rollout bucket is derived from environment, stored flag key, and subject using SHA-256 so a subject remains stable for the same flag/environment combination.

Flag keys use case-insensitive ASCII identity. Cache keys use a normalized namespace so aliases resolve to the same cached configuration.

### Redis Cache

Flag configuration uses cache-aside behavior:

```text
Evaluation
  -> Redis GET
       -> hit: use cached configuration
       -> miss / unreadable entry / Redis failure:
            -> query MySQL
            -> cache successful configuration
  -> apply schedule / targeting / rollout rules
```

Current behavior includes:

- 5-minute configuration TTL
- bounded Redis connect and command timeouts
- post-commit cache invalidation after mutations
- MySQL fallback when Redis is unavailable

Redis stores reusable configuration rather than per-user final decisions. Targeting and rollout still execute for every evaluation.

### Optimistic Concurrency

Feature flags and member records use optimistic locking.

For flag edits:

- responses expose a version
- update requests provide the version being edited as `expectedVersion`
- missing versions return `400`
- stale writes return `409 Conflict`
- clients refresh rather than silently overwrite newer state

This protects the control plane from stale browser sessions overwriting more recent changes.

---

## Security Model

### JWT and RBAC

Auth Service signs RSA/RS256 JWTs. Resource services validate:

- signature
- issuer
- audience
- expiration
- not-before
- roles

Roles:

| Role | Purpose |
|---|---|
| `OWNER` | Highest administrative access |
| `ADMIN` | Administrative operations within peer-account restrictions |
| `DEVELOPER` | Feature management outside PROD; PROD mutations are forbidden |
| `VIEWER` | Read-oriented access where permitted |

Resource services also consult Auth for current account status and role, so a disabled account or role change is not authorized solely by an older token.

Passwords are BCrypt hashed. Password validation respects BCrypt's **72 UTF-8 byte** input limit; oversized passwords are rejected rather than truncated.

The initial owner can be provisioned through explicitly enabled bootstrap configuration for a fresh Auth database.

### SDK-Key Lifecycle

Runtime SDK credentials are separate from user JWTs.

An `OWNER` or `ADMIN` can create a key for a specific environment.

Security properties:

- raw SDK credential is returned once at creation time
- only a SHA-256 hash and display metadata are persisted
- list operations do not return recoverable secrets
- keys can be revoked
- SDK keys authorize runtime evaluation only
- credentials are sent via `X-Feature-Flag-Key`, never in the URL

Rotation is performed by creating a replacement credential, updating the consuming application, and revoking the old key.

### Invitations and Internal Service Authentication

Invitation tokens are random, and Auth persists only their hashes. Acceptance validates invitation state and expiry and uses database locking to protect concurrent acceptance.

Internal service calls use dedicated credentials separate from JWTs and SDK keys:

- `AUTH_INTERNAL_SERVICE_KEY` — resource services -> Auth authoritative account lookup; defaults to `AUTH_RECIPIENTS_SERVICE_KEY`
- `AUTH_RECIPIENTS_SERVICE_KEY` — Flag -> Auth recipient lookup
- `NOTIFICATION_INTERNAL_SERVICE_KEY` — Auth -> Notification invitation delivery

Real service credentials must remain outside Git.

### Login Rate Limiting

Failed login attempts are tracked per normalized account identifier using a bounded process-local store.

Default behavior includes:

- five failures
- five-minute window
- `429 Too Many Requests`
- `Retry-After`
- successful login resets current failure state

This is an application-level safeguard, not a distributed perimeter rate limiter.

---

## Kafka, Outbox, and Consumer Reliability

Feature lifecycle and notification events are written to the Flag Service outbox in the same transaction as the domain mutation.

The publisher:

- runs after commit
- waits for Kafka acknowledgement
- uses bounded retries/backoff
- records successful publication
- marks exhausted work `DEAD`
- removes old `PUBLISHED` rows in bounded retention batches
- preserves pending/dead work for diagnosis

### Topics

| Topic | Purpose | Failure Destination |
|---|---|---|
| `feature-flag-events` | Lifecycle events consumed by Audit and Analytics | Audit / Analytics DLTs |
| `feature-flag-evaluations` | Best-effort evaluation telemetry | Analytics DLT |
| `notification-events` | Durable notification work | Notification DLT |
| `feature-flag-events-audit-dlt` | Audit terminal failures | — |
| `feature-flag-events-analytics-dlt` | Analytics terminal failures | — |
| `notification-events-dlt` | Notification terminal failures | — |

Consumers distinguish invalid payloads from transient infrastructure failures. Retryable failures receive bounded retry/backoff; exhausted failures go to the configured DLT. Failed DLT publication is surfaced instead of being silently acknowledged.

### Evaluation Telemetry

Evaluation telemetry is intentionally **best effort** rather than transactional.

The request computes the flag decision, admits telemetry to a bounded background worker, and returns the result independently of Kafka availability. Telemetry can therefore be dropped during queue saturation, shutdown, process failure, or sustained Kafka outages. Metrics expose admission/drop/publish-failure behavior.

Lifecycle correctness does not depend on evaluation telemetry.

---

## Audit, Analytics, and Notifications

### Audit

Audit Service stores feature lifecycle events with event identity, environment, actor/source information, before/after state, occurrence time, and correlation metadata. Consumption is idempotent and queries are paginated.

The Audit Service records feature-flag lifecycle activity; it is not presented as a complete organization-wide security audit product.

### Analytics

Analytics aggregates events by:

```text
(flag_key, environment, event_type)
```

MySQL performs atomic increments using:

```sql
INSERT ...
ON DUPLICATE KEY UPDATE count = count + 1
```

The aggregate update and processed-event marker participate in the same database transaction.

Analytics processes both lifecycle events and evaluation telemetry. Evaluation values represent aggregate event counts, not unique-user analytics.

### Notifications

Kafka ingestion persists notification work before delivery. Workers use:

- durable database state
- leases
- claim tokens
- bounded attempts
- retry scheduling

This lets retryable work be reclaimed while preventing stale workers from updating a newer claim.

SMTP remains an external side effect, so the project does not claim exactly-once email delivery.

Invitation email uses a separate synchronous internal path so raw invitation URLs are not stored as durable background payloads.

---

## API and Error Contracts

Paginated endpoints use zero-based `page` and bounded `size` parameters and return a page object containing:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Application errors use Spring `ProblemDetail` and `application/problem+json`.

The frontend normalizes bounded error fields and correlation references before displaying them. Additional ProblemDetail fields may be serialized as extensions or nested properties; clients should use the shared error parser rather than render raw server objects.

Common semantics:

| Status | Meaning |
|---:|---|
| `400` | Invalid request / validation failure |
| `401` | Unauthenticated or invalid internal credential |
| `403` | Authenticated but forbidden |
| `404` | Resource not found |
| `409` | Domain or optimistic-concurrency conflict |
| `429` | Login rate limit |
| `5xx` | Sanitized unexpected/infrastructure failure |

Validation details are bounded, and rejected secret values are not echoed.

---

## Observability

HTTP and asynchronous processing use `X-Correlation-ID`.

Correlation information is propagated through:

- HTTP requests
- MDC logging
- ProblemDetail responses
- outbox processing
- Kafka headers

This is request/event correlation rather than a distributed tracing deployment.

Business services expose Actuator health and metrics and Prometheus-compatible endpoints according to profile configuration. Metrics cover authentication, flag evaluation, telemetry admission/failures, outbox state, Kafka consumers, and notification delivery.

The Flag Service does not treat Redis/Kafka availability as equivalent to database readiness so cache fallback and durable outbox behavior can continue during partial infrastructure outages.

---

## Data and Schema Management

Flyway owns schema evolution for:

- Auth
- Flag
- Audit
- Analytics
- Notification

Runtime profiles use Hibernate schema validation rather than automatic production table mutation.

Historical versioned migrations are treated as immutable schema history.

Native MySQL integration tests cover semantics that H2 cannot fully prove, including migrations, constraints, atomic counters, locking, transaction rollback, outbox behavior, and notification claim logic.

---


## Operational Recovery

The platform keeps terminal failure states visible rather than hiding them.

### Outbox

Inspect Flag Service `outbox_events` when lifecycle or notification publishing is delayed.

- `PENDING` rows retry automatically
- `PUBLISHED` rows can be removed by retention cleanup
- `DEAD` rows require diagnosis before a deliberate requeue

Preserve event IDs when manually requeuing so consumer idempotency continues to protect downstream effects.

### Kafka DLTs

After correcting the original failure:

1. inspect the appropriate DLT
2. identify the failed event
3. preserve its event identity
4. deliberately replay it into the original topic

There is no automatic DLT replay endpoint.

### Notification Delivery

Notification leases expire so retryable work can be reclaimed. Since SMTP is external, inspect persisted delivery state before manually retrying terminal failures.

### Evaluation Telemetry

Dropped best-effort evaluation telemetry cannot be recreated from lifecycle events. Analytics should therefore be interpreted as operational aggregate telemetry rather than an exactly-once billing/event ledger.

## Design Decisions and Tradeoffs

### Separate Control and Runtime Credentials

Human users authenticate with JWTs while applications evaluate through environment-bound SDK keys. This prevents runtime credentials from becoming management credentials and avoids coupling client applications to user sessions.

### Transactional Outbox for Lifecycle Reliability

Lifecycle events are durably recorded in the same transaction as the flag mutation. This avoids a state change succeeding without any durable event record, at the cost of background publishing, retention, retry handling, and operator recovery paths.

### Best-Effort Evaluation Telemetry

Per-evaluation telemetry does not add a durable database write to every evaluation. The tradeoff is possible telemetry loss during saturation or infrastructure outages.

### Redis as an Optimization, Not a Source of Truth

MySQL remains the source of truth. Redis reduces repeated configuration reads, while bounded failure handling allows evaluation to continue through the database when Redis is unavailable.

### Idempotent Consumers

Kafka is treated as at least once. Consumers use persisted event identities rather than claiming distributed exactly-once semantics.

### Atomic Analytics Updates

Counters are incremented directly in MySQL, avoiding application-level read/modify/write races between concurrent consumers.

### Eureka-Based Discovery

Gateway and internal clients use Eureka for discovery. This demonstrates discovery-based routing and decouples callers from fixed service locations, while adding another runtime dependency to a relatively small local topology.

### Independent Maven Services

Each Java service builds independently. This preserves service boundaries but means shared conventions such as dependencies, security settings, and error contracts must remain aligned through tests and coordinated maintenance.
