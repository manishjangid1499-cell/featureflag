import fs from 'node:fs';
import path from 'node:path';
import { randomBytes } from 'node:crypto';
import { root, dir, state, settings, base, mailApi, results, insist, record, decodeMail,
  processRun, ownedContainer, serviceAction, waitUntil, api, login, browserSession } from './helpers.mjs';

let browser;
try {
  await waitUntil(async () => (await fetch(base, { signal: AbortSignal.timeout(4000) })).ok, 'frontend readiness', 180000);
  record('Compose frontend', { origin: base });
  await waitUntil(async () => (await api('/auth/invitations/validate?token=readiness')).status === 200, 'Gateway discovery', 180000);
  const owner = await login(settings.BOOTSTRAP_OWNER_EMAIL, settings.BOOTSTRAP_OWNER_PASSWORD);
  record('Login through Nginx and Gateway');
  const claims = JSON.parse(Buffer.from(owner.split('.')[1], 'base64url').toString());
  insist(claims.iss === settings.JWT_ISSUER && claims.aud.includes(settings.JWT_AUDIENCE) && claims.exp > claims.iat, 'JWT claim contract');
  record('JWT issuer, audience and lifetime');
  const flags = await api('/flags', { token: owner, expected: 200 });
  insist(Array.isArray(flags.data.content), 'Flag paging contract'); record('Flags API');
  await api('/audit', { token: owner, expected: 200 }); record('Audit API');
  for (const origin of [base, 'http://localhost:5173']) {
    const preflight = await api('/auth/login', { method: 'OPTIONS', expected: 200,
      headers: { Origin: origin, 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'content-type' } });
    // Same-origin requests do not require or necessarily receive an ACAO header.
    if (origin !== base) insist(preflight.headers.get('access-control-allow-origin') === origin, 'Allowed-origin response');
  }
  await api('/auth/login', { method: 'OPTIONS', expected: 403,
    headers: { Origin: 'https://untrusted.example', 'Access-Control-Request-Method': 'POST' } });
  record('Allowed Compose/dev origins and rejected untrusted origin');
  await api('/flags', { token: 'invalid.jwt.signature', expected: 401 }); record('Invalid JWT rejected');
  const oversized = 'é'.repeat(37);
  const invalidPassword = await api('/auth/login', { method: 'POST', body: { email: settings.BOOTSTRAP_OWNER_EMAIL, password: oversized }, expected: 400 });
  insist(!JSON.stringify(invalidPassword.data).includes(oversized), 'Password reflected in validation response');
  record('Password byte validation without disclosure');

  browser = await browserSession();
  await browser.call('Page.navigate', { url: base + '/login' });
  await waitUntil(() => browser.evaluate('Boolean(document.querySelector("input[type=email]"))'), 'login form');
  await browser.evaluate(`(() => {
    const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    const email = document.querySelector('input[type=email]'), password = document.querySelector('input[type=password]');
    set.call(email, ${JSON.stringify(settings.BOOTSTRAP_OWNER_EMAIL)}); email.dispatchEvent(new Event('input', {bubbles:true}));
    set.call(password, ${JSON.stringify(settings.BOOTSTRAP_OWNER_PASSWORD)}); password.dispatchEvent(new Event('input', {bubbles:true}));
    return true;
  })()`);
  await browser.evaluate("document.querySelector('button[type=submit]').click(); true");
  await waitUntil(() => browser.evaluate("location.pathname === '/dashboard' && Boolean(localStorage.getItem('authUser'))"), 'browser login');
  await waitUntil(() => browser.requests.some(r => r.path === '/flags' && r.status === 200), 'dashboard flags');
  const profile = (await api('/auth/profile', { token: owner, expected: 200 })).data;
  insist(profile.name === settings.BOOTSTRAP_OWNER_NAME, 'Profile name differs from the stored owner');
  await waitUntil(() => browser.evaluate('document.body.innerText.includes(' + JSON.stringify('Welcome back, ' + profile.name) + ')'), 'profile display');
  record('Browser dashboard displays the stored profile name');
  await browser.call('Page.reload');
  await waitUntil(() => browser.evaluate("location.pathname === '/dashboard' && Boolean(document.querySelector('.logout-button')) && Boolean(localStorage.getItem('authUser'))"), 'auth after refresh');
  record('Refresh retains valid authentication');
  for (const route of ['/flags', '/audit', '/analytics', '/notifications']) {
    const start = browser.requests.length;
    await browser.evaluate(`document.querySelector('a[href="${route}"]').click(); true`);
    await waitUntil(() => browser.requests.slice(start).some(r => r.path === (route === '/notifications' ? '/api/notifications' : route) && r.status === 200), 'browser page fetch');
  }
  record('Browser flags, audit, analytics and notifications load through ingress');

  const suffix = randomBytes(4).toString('hex');
  const key = `Smoke-${suffix}`;
  const initial = { flagKey: key, name: 'Smoke acceptance flag', description: 'Disposable test fixture',
    environment: 'DEV', enabled: true, rolloutPercentage: 37, targetUsers: ['sdk-subject'] };
  let flag = (await api('/flags', { method: 'POST', token: owner, body: initial, expected: 201 })).data;
  insist(flag.flagKey === key.toLowerCase() && typeof flag.version === 'number', 'Canonical key/version');
  record('Create flag', { version: flag.version });
  const oldVersion = flag.version;
  const update = { ...initial, flagKey: key.toUpperCase(), description: 'Updated fixture', expectedVersion: flag.version };
  flag = (await api(`/flags/${flag.id}`, { method: 'PUT', token: owner, body: update, expected: 200 })).data;
  insist(flag.version > oldVersion, 'Version did not advance'); record('Update flag');
  await api(`/flags/${flag.id}`, { method: 'PUT', token: owner, body: update, expected: 409 });
  record('Stale browser form rejected by API');
  const route = alias => `/flags/${alias}/evaluate?environment=DEV&userId=rollout-subject`;
  const before = [];
  for (const alias of [key, key.toUpperCase(), key.toLowerCase(), key]) before.push((await api(route(alias), { token: owner, expected: 200 })).data);
  insist(before.every(result => result.enabled === before[0].enabled && result.flagKey === flag.flagKey), 'Unstable alias/cohort identity');
  record('Deterministic evaluation and case aliases');
  const redisId = await ownedContainer('redis');
  const keys = (await processRun('docker', ['exec', redisId, 'redis-cli', '--scan', '--pattern', `flag:config:v2:*${key.toLowerCase()}*`])).trim().split(/\r?\n/).filter(Boolean);
  insist(keys.length === 1, 'Expected one canonical Redis configuration');
  record('Redis-backed repeat evaluation', { effectiveCacheEntries: keys.length });
  flag = (await api(`/flags/${flag.id}/toggle`, { method: 'PATCH', token: owner, expected: 200 })).data;
  insist(!flag.enabled, 'Toggle did not disable');
  for (const alias of [key.toUpperCase(), key, key.toLowerCase()]) insist(!(await api(route(alias), { token: owner, expected: 200 })).data.enabled, 'Stale cache alias after toggle');
  record('Toggle invalidates all case aliases');
  flag = (await api(`/flags/${flag.id}/toggle`, { method: 'PATCH', token: owner, expected: 200 })).data;

  await waitUntil(async () => {
    const rows = (await api(`/audit/${flag.flagKey}`, { token: owner, expected: 200 })).data.content;
    return rows.length >= 4;
  }, 'lifecycle audit'); record('Lifecycle events reach Audit');
  await waitUntil(async () => {
    const rows = (await api(`/analytics/${flag.flagKey}`, { token: owner, expected: 200 })).data.content;
    return ['FLAG_CREATED', 'FLAG_UPDATED', 'FLAG_TOGGLED'].every(type => rows.some(row => row.eventType === type && row.count > 0))
      && rows.some(row => row.eventType.startsWith('EVALUATION_') && row.count > 0);
  }, 'lifecycle analytics'); record('Lifecycle events reach Analytics');
  await waitUntil(async () => {
    const rows = (await api('/api/notifications?size=100', { token: owner, expected: 200 })).data.content;
    return rows.some(row => row.message.includes(flag.flagKey) && row.status === 'SENT');
  }, 'notification delivery'); record('Notification claimed and delivered to local SMTP');
  const maxKey = `max-${suffix}-` + 'a'.repeat(255 - (`max-${suffix}-`).length - 12) + '-unique-tail';
  insist(maxKey.length === 255, 'Fixture maximum-key length');
  const maxFlag = (await api('/flags', { method: 'POST', token: owner, body: { ...initial, flagKey: maxKey }, expected: 201 })).data;
  await waitUntil(async () => {
    const rows = (await api('/api/notifications?size=100', { token: owner, expected: 200 })).data.content;
    return rows.some(row => row.message.includes(maxKey) && row.subject.length <= 255 && row.subject.endsWith('unique-tail') && row.status === 'SENT');
  }, 'maximum-key notification contract'); record('Maximum-length flag persists and delivers bounded subject');

  const sdk = (await api('/sdk-keys', { method: 'POST', token: owner, body: { name: 'Acceptance SDK', environment: 'DEV' }, expected: 201 })).data;
  record('SDK key creation');
  const classpath = [path.join(root, 'sdk/java-sdk/target/classes'),
    fs.readFileSync(path.join(dir, 'sdk-classpath.txt'), 'utf8').trim()].join(path.delimiter);
  async function javaSdk(expected) {
    const output = await processRun('java', ['-cp', classpath, path.join(root, 'scripts/e2e/SDKSmoke.java')], undefined,
      { ...process.env, SMOKE_BASE_URL: base, SMOKE_SDK_KEY: sdk.rawKey, SMOKE_FLAG_KEY: key.toUpperCase(), SMOKE_EXPECTED: String(expected) });
    insist(output.trim() === `SDK_RESULT=${expected}`, 'Java SDK execution');
  }
  await javaSdk(true); record('Actual Java SDK evaluation with case alias');
  await api(`/sdk-keys/${sdk.id}/revoke`, { method: 'POST', token: owner, expected: 200 });
  await api(`/runtime/v1/flags/${key}/evaluate?subject=sdk-subject`, { headers: { 'X-Feature-Flag-Key': sdk.rawKey }, expected: 401 });
  await javaSdk(false); record('Revoked SDK key rejected; Java SDK uses default');

  async function createMember(role, label) {
    const email = `${label}-${suffix}@example.test`, password = randomBytes(24).toString('hex');
    await api('/members/invite', { method: 'POST', token: owner, body: { name: label, email, role }, expected: 201 });
    const token = await waitUntil(async () => {
      const inbox = await (await fetch(mailApi + '/messages')).json();
      for (const message of inbox.toReversed()) {
        const decoded = decodeMail(message);
        if (!decoded.includes(email)) continue;
        const match = decoded.match(/accept-invitation\?token=([A-Za-z0-9_-]+)/);
        if (match) return match[1];
      }
      return false;
    }, 'local invitation capture');
    await api('/auth/invitations/accept', { method: 'POST', body: { token, password, confirmPassword: password }, expected: 200 });
    const jwt = await login(email, password);
    const members = (await api('/members?size=100', { token: owner, expected: 200 })).data.content;
    const member = members.find(m => m.email === email); insist(member, 'Invited member missing');
    return { ...member, token: jwt };
  }
  const admin = await createMember('ADMIN', 'admin-a'), peer = await createMember('ADMIN', 'admin-b');
  const lower = await createMember('DEVELOPER', 'developer');
  await api(`/members/${peer.id}/role?role=VIEWER`, { method: 'PATCH', token: admin.token, expected: 403 });
  await api(`/members/${peer.id}/status`, { method: 'PATCH', token: admin.token, body: { enabled: false }, expected: 403 });
  await api(`/members/${peer.id}`, { method: 'DELETE', token: admin.token, expected: 403 });
  record('Peer ADMIN protected across role/status/delete');
  await api(`/members/${peer.id}/role?role=VIEWER`, { method: 'PATCH', token: owner, expected: 200 });
  await api(`/members/${lower.id}/role?role=VIEWER`, { method: 'PATCH', token: admin.token, expected: 200 });
  await api(`/members/${peer.id}/status`, { method: 'PATCH', token: lower.token, body: { enabled: false }, expected: 403 });
  record('Lower-role administration allowed; forbidden member action is 403');
  const race = await Promise.all([
    api(`/members/${lower.id}/role?role=DEVELOPER`, { method: 'PATCH', token: owner }),
    api(`/members/${lower.id}/status`, { method: 'PATCH', token: owner, body: { enabled: false } })
  ]);
  insist(race.every(r => r.status === 200 || r.status === 409), 'Unexpected member concurrency response');
  if (race[1].status === 409) await api(`/members/${lower.id}/status`, { method: 'PATCH', token: owner, body: { enabled: false }, expected: 200 });
  insist(!(await api(`/members/${lower.id}`, { token: owner, expected: 200 })).data.enabled, 'Concurrent change restored disabled state');
  record('Concurrent member mutations retain disabled state', { statuses: race.map(r => r.status) });

  await serviceAction('redis', 'stop');
  try {
    const fallback = await api(`/flags/${key.toUpperCase()}/evaluate?environment=DEV&userId=sdk-subject`, { token: owner, expected: 200 });
    insist(fallback.data.enabled && fallback.elapsedMs < 3000, 'Redis fallback failed/budget exceeded');
    record('Redis unavailable uses MySQL', { observedMs: fallback.elapsedMs, assertionBudgetMs: 3000 });
  } finally { await serviceAction('redis', 'start'); }
  await waitUntil(async () => (await processRun('docker', ['exec', redisId, 'redis-cli', 'ping'])).trim() === 'PONG', 'Redis recovery');

  // Clear producer metadata by restarting only our Flag container while our Kafka is stopped.
  // This exercises the real unavailable-metadata path rather than relying on producer buffer fill.
  await serviceAction('kafka', 'stop');
  try {
    await serviceAction('flag-service', 'restart');
    await waitUntil(async () => (await api(route(key), { token: owner })).status === 200, 'Flag ready without Kafka', 180000);
    const outageTimes = [];
    const jobs = Array.from({ length: 10 }, async () => {
      for (let i = 0; i < 40; i++) {
        const result = await api(route(key), { token: owner, expected: 200 });
        insist(result.data.enabled === before[0].enabled, 'Telemetry outage changed decision');
        outageTimes.push(result.elapsedMs);
      }
    });
    await Promise.all(jobs);
    const flagId = await ownedContainer('flag-service');
    const metric = JSON.parse(await processRun('docker', ['exec', '-i', flagId, 'curl', '--silent', '--show-error', '--max-time', '5', '--config', '-',
      'http://localhost:8082/actuator/metrics/feature.flag.telemetry.admission?tag=outcome:dropped'], `header = "Authorization: Bearer ${owner}"\n`));
    const dropped = metric.measurements?.find(m => m.statistic === 'COUNT')?.value;
    insist(dropped > 0, 'No telemetry drop observed under saturation');
    outageTimes.sort((a,b) => a - b);
    insist(outageTimes[Math.floor(outageTimes.length * .95)] < 3000, 'Outage evaluation latency budget exceeded');
    record('Kafka unavailable: bounded admission, drops and unchanged decisions', { evaluations: outageTimes.length,
      observedP95Ms: outageTimes[Math.floor(outageTimes.length * .95)], observedMaxMs: outageTimes.at(-1), telemetryDropped: dropped });
    await api(`/flags/${flag.id}/toggle`, { method: 'PATCH', token: owner, expected: 200 });
    record('Lifecycle mutation commits during Kafka outage');
  } finally { await serviceAction('kafka', 'start'); }
  await waitUntil(async () => {
    const rows = (await api(`/audit/${flag.flagKey}`, { token: owner, expected: 200 })).data.content;
    return rows.length >= 5;
  }, 'outbox drains after Kafka recovery', 180000);
  record('Outbox delivers outage mutation after Kafka recovery');

  await api('/flags/' + maxFlag.id, { method: 'DELETE', token: owner, expected: 200 });
  await api('/flags/' + flag.id, { method: 'DELETE', token: owner, expected: 200 });
  await api('/flags/id/' + flag.id, { token: owner, expected: 404 });
  await waitUntil(async () => (await api('/audit/' + flag.flagKey, { token: owner, expected: 200 })).data.content
    .some(row => row.eventType === 'FLAG_DELETED'), 'delete audit');
  await waitUntil(async () => (await api('/analytics/' + flag.flagKey, { token: owner, expected: 200 })).data.content
    .some(row => row.eventType === 'FLAG_DELETED' && row.count === 1), 'delete analytics');
  record('Delete flag and consume its lifecycle event');
  await browser.evaluate("document.querySelector('.logout-button').click(); true");
  await waitUntil(() => browser.evaluate("location.pathname === '/login' && !localStorage.getItem('authUser')"), 'browser logout');
  record('Logout clears authentication');
  fs.writeFileSync(path.join(dir, 'e2e-summary.json'), JSON.stringify({ status: 'PASS', checks: results.length, project: state.project, runAt: new Date().toISOString() }, null, 2));
  console.log(JSON.stringify({ status: 'PASS', checks: results.length, credentialsPrinted: false }));
} catch (error) {
  const safe = error.message.replace(/token=[^ ]+/gi, 'token=[redacted]');
  fs.writeFileSync(path.join(dir, 'e2e-summary.json'), JSON.stringify({ status: 'FAIL', checks: results.length, error: safe }, null, 2));
  console.error(JSON.stringify({ status: 'FAIL', afterChecks: results.length, error: safe })); process.exitCode = 1;
} finally {
  if (browser) { try { await browser.call('Browser.close'); } catch { } browser.ws.close(); browser.child.kill(); }
}
