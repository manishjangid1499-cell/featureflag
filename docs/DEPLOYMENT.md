# Feature Flag Platform - Production Deployment Guide

This runbook documents the production deployment configuration for the **Feature Flag Platform** on an Oracle Cloud Infrastructure (OCI) Virtual Machine.

---

## 1. Target Environment & Specifications

- **Cloud Provider**: Oracle Cloud Infrastructure (OCI) - Ampere A1 Compute
- **Processor & Core Count**: 1 OCPU (1 physical ARM64 Neoverse-N1 core, 1 hardware thread without SMT; `nproc` reports `1`)
- **Memory**: 7 GB nominal. Note: `cat /proc/meminfo` reports `MemTotal: ~6,800 - 6,950 MiB` because the Linux kernel, bootloader, and ACPI reserve early physical memory.
- **Operating System**: Ubuntu 24.04 LTS (`aarch64`)
- **Public IPv4**: `168.138.115.124`
- **Public Domain**: `https://featureflags.duckdns.org`
- **Database**: Fresh MySQL 8.4 installation (no AWS or local historical data migration)
- **Deployment Paths**:
  - Git repository checkout: `/home/ubuntu/feature-flag-platform`
  - Private secrets directory: `/home/ubuntu/secrets`
  - ACME challenge webroot: `/home/ubuntu/feature-flag-platform/acme-challenge`
  - Let's Encrypt certificates: `/etc/letsencrypt`
- **User Permissions**: The `ubuntu` host account has passwordless sudo (`sudo -n`), but Docker group membership is not configured. All Docker operations must be invoked with `sudo -n docker`.
- **Isolation Warning**: **LedgerGuard** runs on a separate dedicated VM in the same VCN subnet. **Do not modify or disrupt LedgerGuard.** When reviewing or adjusting OCI Subnet Security Lists, note that any subnet-level rule changes apply to LedgerGuard's VM as well.

---

## 2. Architecture & Reverse Proxy Design

The deployment implements a secure, two-tier reverse proxy architecture:

```
[ Public Client ]
       |
       |  TCP 80 (HTTP -> 301 HTTPS) / TCP 443 (TLS Termination)
       v
+------------------------------------------------------------------------------+
| edge-proxy (Nginx container: ports 80, 443 exposed publicly)                 |
|   - Serves /.well-known/acme-challenge/ for zero-downtime Certbot renewals   |
|   - Terminates TLS (TLSv1.2, TLSv1.3, HSTS)                                  |
|   - Overwrites untrusted forwarding headers: X-Forwarded-Proto https,        |
|     X-Forwarded-Port 443, X-Real-IP $remote_addr, Forwarded ""               |
|   - Token-protected logs: excludes query strings and Referer                 |
+------------------------------------------------------------------------------+
       |
       |  Internal Docker Bridge (HTTP :8080)
       v
+------------------------------------------------------------------------------+
| frontend (Internal Nginx container: port 8080 internal only)                 |
|   - Uses dedicated production config (docker/nginx/prod-frontend.conf)       |
|   - Serves Single Page Application (SPA) static files                        |
|   - Maps incoming edge headers ($http_x_forwarded_proto -> $forwarded_proto) |
|   - Preserves all hybrid SPA/API routes (/flags, /sdk-keys, /auth, etc.)     |
|   - Local config (Frontend/feature-flag-ui/nginx.conf) remains independent   |
+------------------------------------------------------------------------------+
       |
       |  Internal Docker Bridge (HTTP :8080)
       v
+------------------------------------------------------------------------------+
| api-gateway (Spring Cloud Gateway: port 8080 internal only)                  |
|   - Service discovery routing via Eureka                                     |
|   - Global CORS enforcement (origin: https://featureflags.duckdns.org)       |
+------------------------------------------------------------------------------+
       |
       +--> auth-service (:8081)
       +--> flag-service (:8082)
       +--> audit-service (:8084)
       +--> analytics-service (:8085)
       +--> notification-service (:8086)
       |
  [ Internal Infrastructure (No Public Ports) ]
  MySQL 8.4 (:3306) | Redis 8.10 (:6379) | Kafka KRaft 4.3 (:29092) | Eureka (:8761)
```

### Security & Proxy Trust Guarantees:
1. **Strictly 2 Public Ports**: Only host ports `80` and `443` are published. MySQL, Redis, Kafka, Eureka, API Gateway, all Spring microservices, and the frontend Nginx have **no published host ports**.
2. **Untrusted Header Overwriting**: `edge-proxy` overwrites all client-supplied forwarding headers:
   - `proxy_set_header X-Forwarded-Proto https;`
   - `proxy_set_header X-Forwarded-Port 443;`
   - `proxy_set_header X-Real-IP $remote_addr;`
   - `proxy_set_header X-Forwarded-For $remote_addr;` (discards any incoming `X-Forwarded-For`)
   - `proxy_set_header Forwarded "";` (erases RFC 7239 `Forwarded` headers)
3. **Independent Local vs Production Frontend**:
   - The production container mounts `docker/nginx/prod-frontend.conf`, which trusts the sanitized headers passed by `edge-proxy`.
   - The repository's base `Frontend/feature-flag-ui/nginx.conf` remains untouched and uses `$scheme` / `$remote_addr`, preventing header spoofing during local development.
4. **Token Protection in Access Logs**:
   Both `edge-proxy` and `prod-frontend` use the `no_query` log format:
   ```nginx
   log_format no_query '$remote_addr - $remote_user [$time_local] '
                       '"$request_method $uri $server_protocol" $status $body_bytes_sent '
                       '"$http_user_agent"';
   ```
   Using `$uri` instead of `$request_uri` and omitting `$http_referer` ensures sensitive URL tokens (e.g., password reset and invitation tokens) are never persisted in web server logs.
5. **Hybrid SPA / API Routing Preserved**: Exact collection paths (`/flags`, `/analytics`, `/audit`, `/members`, `/sdk-keys`) distinguish browser HTML navigation from Axios JSON calls via `$spa_navigation` mapping, redirecting browser requests to `/index.html` while proxying API calls to `api-gateway`.

---

## 3. Memory Budgets & Startup Strategy

### Memory Budget Breakdown:
The VM's usable user-space `MemTotal` is approximately 6,800 - 6,950 MiB. Container memory limits and JVM heaps are provisional operating budgets:

| Component | Container Limit | Java Heap / Engine Configuration | Primary Function |
|---|---|---|---|
| **edge-proxy** | `64 MiB` | Event-driven C | Public TLS termination & ACME |
| **frontend** | `64 MiB` | Event-driven C | Static React SPA & route proxy |
| **mysql** | `800 MiB` | `innodb_buffer_pool_size=256M`, max conn: 100 | Relational database (5 schemas) |
| **redis** | `128 MiB` | `maxmemory 96mb`, `allkeys-lru` | Flag evaluation cache |
| **kafka** | `800 MiB` | `KAFKA_HEAP_OPTS: "-Xms384m -Xmx384m"` | KRaft broker & metadata controller |
| **eureka-server** | `384 MiB` | `-Xms192m -Xmx192m -XX:MaxMetaspaceSize=96m` | Discovery registry |
| **auth-service** | `550 MiB` | `-Xms256m -Xmx256m -XX:MaxMetaspaceSize=128m` | User auth, RBAC, tokens |
| **flag-service** | `650 MiB` | `-Xms320m -Xmx320m -XX:MaxMetaspaceSize=192m` | Flag evaluation & rules |
| **audit-service** | `600 MiB` | `-Xms200m -Xmx200m -XX:MaxMetaspaceSize=192m` | Audit event consumer |
| **analytics-service** | `600 MiB` | `-Xms200m -Xmx200m -XX:MaxMetaspaceSize=192m` | Analytics event consumer |
| **notification-service** | `600 MiB` | `-Xms200m -Xmx200m -XX:MaxMetaspaceSize=192m` | Notification consumer & SMTP |
| **api-gateway** | `550 MiB` | `-Xms256m -Xmx256m -XX:MaxMetaspaceSize=128m` | Netty/WebFlux reverse proxy |
| **Steady-State Container Cap** | **5,634 MiB** | **~2,008 MiB JVM Heaps** | **12 steady-state containers** |
| **kafka-init** (one-shot) | `256 MiB` | `KAFKA_HEAP_OPTS: "-Xms64m -Xmx64m"` | Topic provisioning (exits 0) |
| **Peak Cap During Startup** | **5,890 MiB** | — | **Steady-state + kafka-init** |
| **Provisional OS / Native Headroom** | **~910 - 1,060 MiB** | Host OS and system services; JVM native memory is included in container caps | **Safety margin** |

> **Important Operational Note**: These container limits are **provisional budgets**, not an absolute guarantee against OOM kills. In Java 21, JVM native memory (Metaspace, JIT compiler, thread stacks, Netty direct buffers) operates outside the heap. Under production load, monitor actual RSS memory with `sudo -n docker stats` and tune limits as necessary.

### Kafka CLI Heap Constraints:
Kafka CLI tools (`kafka-topics.sh`) default to 512 MiB or 1 GiB heap if unconstrained, which triggers immediate OOM kills within bounded containers. Both `kafka-init` and `kafka` healthchecks explicitly set:
```bash
KAFKA_HEAP_OPTS="-Xms64m -Xmx64m"
```

### Sequential Image Building on Single-Core VM:
The VM has **1 CPU core** (`nproc=1`). Running parallel Maven builds (`docker compose build`) saturates the single core, causes extreme context-switching latency, and exhausts memory during Temurin compilation.
**Docker runtime limits do not constrain Docker build containers.** Therefore, builds must be run **sequentially**, with immediate failure if any build step fails:

```bash
set -euo pipefail
cd /home/ubuntu/feature-flag-platform

for svc in eureka-server auth-service flag-service audit-service analytics-service notification-service api-gateway frontend; do
  echo "=== Building $svc ==="
  sudo -n docker compose -p featureflag --env-file /home/ubuntu/secrets/.env -f docker-compose.prod.yml build "$svc"
done
```

---

## 4. Step-by-Step Production Deployment

### Step 1: VM Host & Network Inspection

1. **Verify Existing Docker Engine**:
   Docker Engine and the Compose plugin are already installed and verified on this VM. Do not reinstall or reconfigure them. Confirm with:
   ```bash
   sudo -n docker info
   sudo -n docker compose version
   ```

2. **OCI Subnet Security List**:
   In the Oracle Cloud Console, check the subnet security list for `168.138.115.124` to ensure TCP ingress rules exist for:
   - Port `22` (SSH - restricted to your administrator IP)
   - Port `80` (HTTP - `0.0.0.0/0`)
   - Port `443` (HTTPS - `0.0.0.0/0`)
   > **Caution**: **LedgerGuard** resides in this same subnet. Do not modify or delete existing subnet rules that LedgerGuard relies upon.

3. **Inspect Host Firewall Rules**:
   Inspect existing firewall rules on the host before making changes:
   ```bash
   sudo -n iptables -S
   sudo -n ufw status verbose
   ```
   > **Important**: Do not blindly enable or reset UFW. Docker automatically manages published container ports directly in iptables (`DOCKER` chain), bypassing standard UFW `INPUT` rules. Blindly toggling UFW can sever administrative SSH access or disrupt existing packet forwarding.

4. **Ensure Certbot is Installed**:
   ```bash
   if ! command -v certbot >/dev/null 2>&1; then
     sudo -n apt-get update && sudo -n apt-get install -y certbot
   fi
   ```

---

### Step 2: DuckDNS Domain Verification & Updater

Verify DNS resolution:
```bash
dig +short featureflags.duckdns.org
# Must return: 168.138.115.124
```

Set up the automated DuckDNS updater script (using standard TLS verification—**do not use `curl -k`**):
```bash
sudo -n mkdir -p /opt/duckdns
sudo -n bash -c 'cat << "EOF" > /opt/duckdns/duck.sh
#!/bin/sh
set -eu
curl -s -S "https://www.duckdns.org/update?domains=featureflags&token=YOUR_DUCKDNS_TOKEN&ip=" > /opt/duckdns/duck.log
EOF'
sudo -n chmod 700 /opt/duckdns/duck.sh

# Add to root crontab (every 10 minutes)
(sudo -n crontab -l 2>/dev/null; echo "*/10 * * * * /opt/duckdns/duck.sh >/dev/null 2>&1") | sudo -n crontab -
```

---

### Step 3: Secrets & Environment Preparation

Store all secrets **outside the Git checkout** in `/home/ubuntu/secrets`:

1. **Create Directory and RSA Keypair**:
   ```bash
   mkdir -p /home/ubuntu/secrets /home/ubuntu/feature-flag-platform/acme-challenge
   chmod 700 /home/ubuntu/secrets
   chmod 755 /home/ubuntu/feature-flag-platform/acme-challenge

   # Generate dedicated 2048-bit RSA keypair
   openssl genpkey -algorithm RSA -out /home/ubuntu/secrets/jwt-private.pem -pkeyopt rsa_keygen_bits:2048
   openssl rsa -pubout -in /home/ubuntu/secrets/jwt-private.pem -out /home/ubuntu/secrets/jwt-public.pem

   # Permissions: Narrowly scope read access for the private key to container UID/GID 10001 (appuser)
   sudo -n chown 10001:10001 /home/ubuntu/secrets/jwt-private.pem
   sudo -n chmod 600 /home/ubuntu/secrets/jwt-private.pem

   # Public key is non-secret and can be read by containers
   sudo -n chmod 644 /home/ubuntu/secrets/jwt-public.pem
   ```

2. **Configure Production Environment (`/home/ubuntu/secrets/.env`)**:
   ```bash
   cp /home/ubuntu/feature-flag-platform/.env.production.example /home/ubuntu/secrets/.env
   chmod 600 /home/ubuntu/secrets/.env
   ```
   Edit `/home/ubuntu/secrets/.env` and supply production values:
   - Generate strong passwords for `MYSQL_ROOT_PASSWORD`, `AUTH_DB_PASSWORD`, `FLAG_DB_PASSWORD`, `AUDIT_DB_PASSWORD`, `ANALYTICS_DB_PASSWORD`, `NOTIFICATION_DB_PASSWORD`.
   - Generate 32+ character random strings for `AUTH_RECIPIENTS_SERVICE_KEY`, `AUTH_INTERNAL_SERVICE_KEY`, `NOTIFICATION_INTERNAL_SERVICE_KEY`.
   - Set SMTP credentials for `NOTIFICATION_MAIL_USERNAME` and `NOTIFICATION_MAIL_PASSWORD`.
   - Set key paths:
     `AUTH_JWT_PRIVATE_KEY_FILE=/home/ubuntu/secrets/jwt-private.pem`
     `JWT_PUBLIC_KEY_FILE=/home/ubuntu/secrets/jwt-public.pem`
   - Enable initial owner bootstrap:
     ```env
     BOOTSTRAP_OWNER_ENABLED=true
     BOOTSTRAP_OWNER_NAME="System Administrator"
     BOOTSTRAP_OWNER_EMAIL="admin@featureflags.duckdns.org"
     BOOTSTRAP_OWNER_PASSWORD="YourInitialStrongPassword123!"
     ```

3. **Validate Configuration Without Printing Secrets**:
   ```bash
   cd /home/ubuntu/feature-flag-platform
   sudo -n docker compose -p featureflag --env-file /home/ubuntu/secrets/.env -f docker-compose.prod.yml config -q
   ```

---

### Step 4: Initial Standalone Certificate Issuance

Before launching the Docker edge proxy, host port 80 is unoccupied. Issue the initial certificate using Certbot standalone mode:

```bash
sudo -n certbot certonly --standalone \
  -d featureflags.duckdns.org \
  --agree-tos \
  --register-unsafely-without-email
```

Keep TLS private keys root-readable (`chmod 600 /etc/letsencrypt/archive/featureflags.duckdns.org/privkey*.pem`). The entire `/etc/letsencrypt` directory will be mounted read-only into `edge-proxy`, preserving `live` -> `archive` symlinks.

---

### Step 5: Sequential Build & Stack Launch

1. **Fail-Fast Sequential Build**:
   ```bash
   set -euo pipefail
   cd /home/ubuntu/feature-flag-platform

   for svc in eureka-server auth-service flag-service audit-service analytics-service notification-service api-gateway frontend; do
     echo "=== Building $svc ==="
     sudo -n docker compose -p featureflag --env-file /home/ubuntu/secrets/.env -f docker-compose.prod.yml build "$svc"
   done
   ```

2. **Launch with Readiness Wait (600s Timeout)**:
   Launch using `--no-build` since images were built explicitly above. Allow up to 600 seconds for initial database initialization, KRaft metadata initialization, and 6 Spring Boot services to reach healthy status on the single CPU core:
   ```bash
   sudo -n docker compose -p featureflag --env-file /home/ubuntu/secrets/.env -f docker-compose.prod.yml up -d --no-build --wait --wait-timeout 600
   ```

3. **Verify Container Health**:
   ```bash
   sudo -n docker compose -p featureflag --env-file /home/ubuntu/secrets/.env -f docker-compose.prod.yml ps
   ```
   All 12 long-running services must report `healthy`. `kafka-init` must report `Exited (0)`.

---

### Step 6: Verify HTTP ACME Challenge Path Externally

Before updating renewal configuration, verify externally that `edge-proxy` correctly serves files from the ACME webroot over HTTP port 80:

```bash
mkdir -p /home/ubuntu/feature-flag-platform/acme-challenge/.well-known/acme-challenge
chmod 755 /home/ubuntu/feature-flag-platform/acme-challenge /home/ubuntu/feature-flag-platform/acme-challenge/.well-known /home/ubuntu/feature-flag-platform/acme-challenge/.well-known/acme-challenge
echo "test-challenge-ok" > /home/ubuntu/feature-flag-platform/acme-challenge/.well-known/acme-challenge/test-token

# Execute from an external machine or curl against public domain:
curl -fsS http://featureflags.duckdns.org/.well-known/acme-challenge/test-token
# Expected output: test-challenge-ok

rm -f /home/ubuntu/feature-flag-platform/acme-challenge/.well-known/acme-challenge/test-token
```

---

### Step 7: Persist Webroot Renewal & Test Renewal Hook

Now that the HTTP challenge path is verified:

1. **Persist Webroot Renewal in Certbot**:
   Use `certbot reconfigure` (supported in Certbot 2.6.0+, standard in Ubuntu 24.04) to switch the renewal authenticator from standalone to webroot without re-requesting certificates:
   ```bash
   if sudo -n certbot --help all 2>/dev/null | grep -q 'reconfigure'; then
     sudo -n certbot reconfigure \
       --cert-name featureflags.duckdns.org \
       --webroot \
       -w /home/ubuntu/feature-flag-platform/acme-challenge
   else
     echo "Error: 'certbot reconfigure' is not supported by the installed Certbot version." >&2
     echo "Inspect the installed version with 'certbot --version' and upgrade Certbot before proceeding." >&2
     exit 1
   fi
   ```
   Verify that `/etc/letsencrypt/renewal/featureflags.duckdns.org.conf` now specifies `authenticator = webroot` and `webroot_path = /home/ubuntu/feature-flag-platform/acme-challenge`.

2. **Verify Single Renewal Scheduler (Certbot systemd Timer)**:
   Ubuntu 24.04 provides `certbot.timer` by default. Verify it is active:
   ```bash
   systemctl list-timers | grep certbot
   ```
   Do not configure a separate cron job.

3. **Deploy Hook for Reloading Nginx**:
   Create `/etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh`:
   ```bash
   sudo -n bash -c 'cat << "EOF" > /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
   #!/bin/bash
   set -euo pipefail

   COMPOSE_CMD="/usr/bin/docker compose -p featureflag --env-file /home/ubuntu/secrets/.env -f /home/ubuntu/feature-flag-platform/docker-compose.prod.yml"

   # Validate Nginx syntax before reloading
   $COMPOSE_CMD exec -T edge-proxy nginx -t

   # Reload Nginx without recreating containers
   $COMPOSE_CMD exec -T edge-proxy nginx -s reload
   EOF'
   sudo -n chmod 700 /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
   ```

   **How renewed certificates become visible without recreating containers**:
   Because `/etc/letsencrypt` is a directory bind mount, new certificate files written to `/etc/letsencrypt/archive` and updated symlinks in `/etc/letsencrypt/live` are immediately visible to the running container. `nginx -s reload` instructs the existing master process to re-read files and perform a zero-downtime worker rollover.

4. **Test Renewal Dry-Run**:
   ```bash
   sudo -n certbot renew --dry-run --run-deploy-hooks
   ```

---

### Step 8: Disable Bootstrap Owner

Once the initial OWNER account is seeded:

1. Confirm creation in Auth Service logs:
   ```bash
   sudo -n docker compose -p featureflag --env-file /home/ubuntu/secrets/.env -f /home/ubuntu/feature-flag-platform/docker-compose.prod.yml logs auth-service | grep -i "bootstrap"
   ```

2. Edit `/home/ubuntu/secrets/.env` to disable bootstrap:
   ```env
   BOOTSTRAP_OWNER_ENABLED=false
   BOOTSTRAP_OWNER_PASSWORD=
   ```

3. Restart `auth-service`:
   ```bash
   sudo -n docker compose -p featureflag --env-file /home/ubuntu/secrets/.env -f /home/ubuntu/feature-flag-platform/docker-compose.prod.yml up -d --no-deps --no-build auth-service
   ```

---

## 5. Verification Checklist

Execute these checks from an external client:

1. **HTTP to HTTPS Redirection**:
   ```bash
   curl -I http://featureflags.duckdns.org/
   # Expected: HTTP/1.1 301 Moved Permanently
   # Location: https://featureflags.duckdns.org/
   ```

2. **HTTPS Edge Connectivity & Security Headers**:
   ```bash
   curl -I https://featureflags.duckdns.org/
   # Expected: HTTP/1.1 200 OK
   # Strict-Transport-Security: max-age=63072000; includeSubDomains
   # X-Content-Type-Options: nosniff
   # X-Frame-Options: DENY
   ```

3. **Disabled Endpoints**:
   ```bash
   curl -I https://featureflags.duckdns.org/auth/v3/api-docs
   # Expected: HTTP/1.1 404 Not Found
   ```

4. **Web UI Verification**:
   - Navigate to `https://featureflags.duckdns.org`.
   - Sign in with the bootstrap OWNER credentials.
   - Verify top-right account menu displays the user's name and role (`OWNER`).
   - Create a feature flag and preview evaluation.
   - Generate an SDK key and verify one-time secret revelation.
   - Inspect Audit Logs for creation events.

---

## 6. Backup & Recovery Procedures

### 1. Hardened Fail-Fast Database Logical Backup Script
Create `/home/ubuntu/backup-mysql.sh`:

```bash
#!/bin/bash
set -euo pipefail
umask 077

BACKUP_DIR="/home/ubuntu/backups"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/mysql_backup_${TIMESTAMP}.sql.gz"
TEMP_FILE="${BACKUP_FILE}.tmp"
CHECKSUM_FILE="${BACKUP_FILE}.sha256"

mkdir -p "$BACKUP_DIR"

# Clean up partial temporary file on failure or interruption
trap 'rm -f "$TEMP_FILE"' EXIT

echo "=== Starting logical backup at $(date -u) ==="

# Obtain password inside the container via MYSQL_PWD; do not place password in command arguments
sudo -n docker compose -p featureflag \
  --env-file /home/ubuntu/secrets/.env \
  -f /home/ubuntu/feature-flag-platform/docker-compose.prod.yml \
  exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; export MYSQL_PWD; exec mysqldump -u root \
    --all-databases \
    --single-transaction \
    --quick \
    --routines \
    --triggers \
    --events' | gzip -9 > "$TEMP_FILE"

# Validate that output file is not empty
if [ ! -s "$TEMP_FILE" ]; then
  echo "Error: Backup file is empty!" >&2
  exit 1
fi

# Check gzip archive integrity before publishing
gzip -t "$TEMP_FILE"

# Atomic rename, cancel trap, and generate checksum
mv "$TEMP_FILE" "$BACKUP_FILE"
trap - EXIT
sha256sum "$BACKUP_FILE" > "$CHECKSUM_FILE"

echo "=== Backup completed successfully: $BACKUP_FILE ==="
```
```bash
chmod 700 /home/ubuntu/backup-mysql.sh
```

### 2. Database Restore Procedure
**Prerequisites & Behavioral Impact**:
- **Stop Application Writers**: Application services writing to MySQL must be stopped before restore to prevent partial writes, foreign key locks, or state inconsistencies.
- **Account & Grant Overwrites**: Restoring with `--all-databases` includes the `mysql` system schema, which restores user accounts, passwords, and permissions as they existed when the backup was taken. These restored credentials must match the credentials configured in `/home/ubuntu/secrets/.env` for services to reconnect after restore.

**Restore Execution**:
```bash
# 1. Stop services that write to MySQL
sudo -n docker compose -p featureflag \
  --env-file /home/ubuntu/secrets/.env \
  -f /home/ubuntu/feature-flag-platform/docker-compose.prod.yml \
  stop auth-service flag-service audit-service analytics-service notification-service

# 2. Restore inside a parenthesized fail-fast subshell, resuming application services only upon success
(
  set -euo pipefail
  BACKUP_ARCHIVE="/home/ubuntu/backups/mysql_backup_YYYYMMDD_HHMMSS.sql.gz"

  sha256sum -c "${BACKUP_ARCHIVE}.sha256"
  gzip -t "$BACKUP_ARCHIVE"

  gunzip < "$BACKUP_ARCHIVE" | \
    sudo -n docker compose -p featureflag \
      --env-file /home/ubuntu/secrets/.env \
      -f /home/ubuntu/feature-flag-platform/docker-compose.prod.yml \
      exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; export MYSQL_PWD; exec mysql -u root'

  # 3. Resume application services only after successful checksum verification, decompression, and restore
  sudo -n docker compose -p featureflag \
    --env-file /home/ubuntu/secrets/.env \
    -f /home/ubuntu/feature-flag-platform/docker-compose.prod.yml \
    up -d --no-build --wait --wait-timeout 180
)
```

### 3. Kafka State & Snapshot Consistency
> **Important**: **Do not copy `/var/lib/kafka/data` while Kafka is running.**
> Live file copies of active KRaft segment files and metadata partitions result in corrupt or unrecoverable broker logs. To create a consistent snapshot of Kafka state, Kafka must be stopped cleanly first:
```bash
sudo -n docker compose -p featureflag --env-file /home/ubuntu/secrets/.env -f /home/ubuntu/feature-flag-platform/docker-compose.prod.yml stop kafka
# Snapshot kafka-data volume while stopped
sudo -n docker compose -p featureflag --env-file /home/ubuntu/secrets/.env -f /home/ubuntu/feature-flag-platform/docker-compose.prod.yml start kafka
```

### 4. Safe Reboot & Restart Behavior
Docker's `restart: unless-stopped` policy behaves as follows:
- **Unexpected VM Crash / System Reboot**: If the VM reboots while containers were running, Docker daemon automatically restores all running containers.
- **Explicit `docker compose stop`**: If containers were stopped explicitly by an administrator (`sudo -n docker compose stop`), Docker records their state as explicitly stopped and **will not restart them on reboot**.
- **Resuming after intentional maintenance**:
  ```bash
  sudo -n docker compose -p featureflag --env-file /home/ubuntu/secrets/.env -f /home/ubuntu/feature-flag-platform/docker-compose.prod.yml up -d --no-build --wait --wait-timeout 180
  ```
