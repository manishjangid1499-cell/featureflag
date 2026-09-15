# Deployment Guide

This document describes the production-style deployment of the Feature Flag Platform on a single AWS EC2 Ubuntu server using Docker Compose.

> This guide intentionally uses placeholders for credentials, IP addresses, tokens, private keys, and other secrets. Never commit real production secrets to Git.

## Production Deployment Overview

The deployed platform uses:

- AWS EC2
- Ubuntu 24.04 LTS
- Docker Engine
- Docker Compose
- Nginx
- DuckDNS
- Let's Encrypt
- Certbot
- systemd timers

The public application is available at:

https://featureflag-mj.duckdns.org

## Deployment Architecture

```text
Internet
   |
   v
DuckDNS hostname
   |
   v
HTTPS :443
   |
   v
Frontend / Nginx
   |
   v
API Gateway
   |
   +--> Auth Service
   +--> Flag Service
   +--> Audit Service
   +--> Analytics Service
   +--> Notification Service

Internal infrastructure:
MySQL + Redis + Kafka + Eureka
```


## AWS EC2 and Network Configuration

The current portfolio deployment runs on a single EC2 instance in the AWS Mumbai region.

Current server profile:

- Ubuntu Server 24.04 LTS
- x86_64 architecture
- 2 vCPU
- 8 GiB memory class
- 40 GiB encrypted gp3 root volume
- Docker Engine and Docker Compose installed directly on the VM
- 4 GiB swap configured as a build/runtime safety margin

The EC2 security group exposes only the required public entry points:

| Purpose | Protocol | Port | Source |
|---|---|---:|---|
| SSH administration | TCP | `22` | Administrator public IP only |
| HTTP | TCP | `80` | Public internet |
| HTTPS | TCP | `443` | Public internet |

Application and infrastructure ports are not exposed publicly.

Examples of private/internal ports include:

| Component | Port |
|---|---:|
| API Gateway | `8080` |
| Auth Service | `8081` |
| Flag Service | `8082` |
| Audit Service | `8084` |
| Analytics Service | `8085` |
| Notification Service | `8086` |
| Eureka | `8761` |
| MySQL | `3306` |
| Redis | `6379` |

Kafka exposes its external listener only on host loopback (`127.0.0.1`) rather than on the public network interface.

The EC2 public IPv4 address is intentionally not documented here because an automatically assigned address can change after a Stop -> Start cycle.

## Docker Compose Runtime

The complete application stack is deployed with Docker Compose.

Before starting the stack, validate the resolved configuration:

```bash
docker compose config
```

Build the application images:

```bash
docker compose build
```

Start the stack:

```bash
docker compose up -d
```

Check container status:

```bash
docker compose ps
```

Long-running containers use the `restart: unless-stopped` policy so they can automatically return after a normal VM reboot or EC2 Stop -> Start cycle once Docker starts.

## Secrets and Environment Configuration

Production secrets are supplied outside the Git repository.

A production environment file should contain deployment-specific values such as:

- database usernames and passwords
- JWT issuer, audience, and key ID
- internal service credentials
- SMTP credentials
- frontend base URL
- allowed CORS origin
- bootstrap configuration

The production `.env` file must never be committed.

JWT signing material is also stored outside the repository and mounted into containers as read-only files.

Example placeholders:

```text
AUTH_JWT_PRIVATE_KEY_FILE=/secure/path/jwt-private.pem
JWT_PUBLIC_KEY_FILE=/secure/path/jwt-public.pem
```

Never commit:

- `.env` production files
- private JWT keys
- SMTP passwords
- database passwords
- internal service keys
- DuckDNS tokens
- TLS private keys
- EC2 SSH private keys

## Persistent Data

Docker named volumes preserve state independently of individual container lifecycles.

The Compose deployment persists:

| Component | Docker volume | Purpose |
|---|---|---|
| MySQL | `mysql-data` | Application databases and durable relational data |
| Kafka | `kafka-data` | Broker log data and topic state |

Redis is used as a cache and is intentionally recoverable from the source-of-truth services and databases.

Normal EC2 Stop -> Start operations preserve the EBS-backed Docker data. Terminating the instance is different and should not be used as a normal shutdown procedure.

## DNS and HTTPS

The production deployment uses DuckDNS to provide a stable hostname even when the EC2 instance receives a different automatically assigned public IPv4 address after a Stop -> Start cycle.

Production hostname:

https://featureflag-mj.duckdns.org

A systemd timer periodically updates the DuckDNS record with the server's current public IP address.

The DuckDNS authentication token is stored outside the Git repository in a root-readable configuration file. The token must never be committed or written into this documentation.

## TLS Certificates

HTTPS is provided with a Let's Encrypt certificate managed by Certbot.

The production Nginx configuration serves:

- HTTP on host port `80`
- HTTPS on host port `443`

The frontend Nginx container receives the certificate and private key through read-only host mounts.

TLS configuration enables modern protocol versions:

```nginx
ssl_protocols TLSv1.2 TLSv1.3;
```

Certificate files and TLS private keys remain outside the Git repository.

## Automatic Certificate Renewal

Certbot renewal is automated by its systemd timer.

Because the certificate is obtained using Certbot's standalone HTTP challenge, renewal hooks coordinate access to port 80:

1. A pre-renewal hook stops the frontend container.
2. Certbot performs the standalone HTTP challenge and renews the certificate when required.
3. A deploy hook copies the renewed certificate files to the directory mounted by the frontend container.
4. A post-renewal hook starts the frontend container again.

The copied TLS files are given restrictive permissions and are mounted into Nginx as read-only files.

The renewal flow should be tested with:

```bash
sudo certbot renew --dry-run --run-deploy-hooks
```

A successful dry run confirms that certificate renewal, deployment hooks, and frontend recovery work together.

## Dynamic DNS Recovery

The DuckDNS updater runs periodically through a systemd timer. After an EC2 Stop -> Start operation, the updater detects the server's current public IP and refreshes the DNS record.

This allows the application hostname to remain stable even when the underlying EC2 public IPv4 address changes.

## Operations and Health Checks

After deployment or an EC2 restart, verify the platform in layers.

Check Docker:

```bash
sudo systemctl status docker --no-pager
```

Check the application stack:

```bash
docker compose ps
```

Long-running application containers should report a running or healthy state. The Kafka initialization container is expected to complete successfully and exit.

Check the public HTTPS endpoint:

```bash
curl -I https://featureflag-mj.duckdns.org
```

A successful deployment should return an HTTP `200` response from the public application.

Check the DuckDNS updater:

```bash
sudo systemctl status featureflag-duckdns.timer --no-pager
sudo journalctl -u featureflag-duckdns.service -n 20 --no-pager
```

Check Certbot renewal scheduling:

```bash
systemctl list-timers | grep certbot
```

## Safe EC2 Stop and Start

To reduce cloud usage when the portfolio application is not needed, the EC2 instance can be stopped from the AWS console.

For a normal shutdown:

1. Stop the EC2 instance from the AWS console.
2. Do not terminate the instance.
3. Do not run `docker compose down --volumes` as part of a normal shutdown.

After starting the instance again:

1. Wait for the EC2 instance and Docker daemon to start.
2. Docker restores containers configured with `restart: unless-stopped`.
3. The DuckDNS systemd timer updates the hostname if the public IPv4 address changed.
4. Verify the stack with `docker compose ps`.
5. Verify the public HTTPS endpoint.

The EBS volume, Docker volumes, environment configuration, JWT keys, and TLS files remain on the instance across a normal Stop -> Start cycle.

## Troubleshooting

### HTTPS works but SSH fails

If the application is reachable over HTTPS but SSH times out, verify that the EC2 security group still allows port 22 from the administrator's current public IP address.

Do not expose SSH to the entire public internet as a troubleshooting shortcut.

### Containers do not recover after restart

Check Docker first:

```bash
sudo systemctl status docker --no-pager
```

Then inspect the Compose stack:

```bash
docker compose ps
docker compose logs --tail=100
```

### Hostname points to an old IP

Check the DuckDNS timer and updater logs:

```bash
sudo systemctl status featureflag-duckdns.timer --no-pager
sudo journalctl -u featureflag-duckdns.service -n 50 --no-pager
```

### Certificate renewal problems

Test the complete renewal workflow without replacing the live certificate:

```bash
sudo certbot renew --dry-run --run-deploy-hooks
```

After testing, verify that the frontend container is running and the HTTPS endpoint is reachable.

## Security Notes

The production deployment follows these operational security rules:

- only ports 80 and 443 are publicly exposed for the application
- SSH access is restricted to the administrator's public IP
- backend services and infrastructure remain private
- production secrets stay outside Git
- JWT private keys are never committed
- TLS private keys are never committed
- containers consume mounted key material as read-only files
- certificate renewal and DNS updates are automated
