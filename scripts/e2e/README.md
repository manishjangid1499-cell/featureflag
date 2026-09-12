# Disposable end-to-end smoke tests

These checks exercise the supported Compose deployment with fresh databases,
generated credentials, real Kafka and Redis, a Chromium browser, and the Java SDK.
SMTP messages stay in memory in a local mail sink. No external email is sent.

Requirements: Java 21, Maven, Node 24, Docker Compose, and an installed Chromium
browser. Docker Desktop provides the default `host.docker.internal` connection to
the host mail sink. For another Docker engine, explicitly configure
`SMOKE_SMTP_HOST` and `SMOKE_SMTP_BIND` to a host interface reachable from its
containers. Keep the test mail sink on a trusted local network.

Run from the repository root. In PowerShell:

```powershell
$env:BROWSER_EXECUTABLE = '<path to your Chromium browser executable>'
$env:SMOKE_WORK_DIR = node scripts/e2e/prepare.mjs
mvn -f sdk/java-sdk/pom.xml package dependency:build-classpath "-Dmdep.outputFile=$env:SMOKE_WORK_DIR/sdk-classpath.txt"
node scripts/e2e/compose.mjs config
node scripts/e2e/compose.mjs build
```

In a second terminal, set `SMOKE_WORK_DIR` to the directory returned by preparation
and run `node scripts/e2e/smtp.mjs`. Keep that process running while testing:

```powershell
node scripts/e2e/compose.mjs up
node scripts/e2e/smoke.mjs
node scripts/e2e/invitations.mjs
node scripts/e2e/compose.mjs down
```

On POSIX shells, use `export BROWSER_EXECUTABLE=/path/to/chromium` and
`export SMOKE_WORK_DIR="$(node scripts/e2e/prepare.mjs)"`, then the same commands
with `$SMOKE_WORK_DIR` for the classpath output. Preparation creates a new temporary
directory each time; it never writes credentials or reports into the checkout.

Defaults are frontend port 3100, Kafka host port 39092, SMTP port 2525, and mail
inspection port 2526. Set `SMOKE_FRONTEND_PORT`, `SMOKE_KAFKA_PORT`,
`SMOKE_SMTP_PORT`, and `SMOKE_MAIL_API_PORT` before preparation to use other ports.

The main suite covers login, profile display, dashboard/page loading, flag CRUD,
deterministic evaluation, version conflicts, event delivery, notifications, SDK
revocation, role policies, Redis fallback, Kafka outage recovery, and logout.
The invitation suite covers SMTP acceptance/failure, browser feedback, resend,
link replacement, browser acceptance, member login, and credential-safe logging.
Failures exit nonzero; result files contain assertions and timings, not credentials.

The outage checks stop only containers whose Compose labels match the generated
test project. `down` removes only that project's containers, network, and volumes.
Stop the mail-sink process afterward and delete the generated temporary directory
when its results are no longer needed. Private keys and passwords in that directory
are disposable test credentials and must never be committed.
