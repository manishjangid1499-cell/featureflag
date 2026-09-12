import { generateKeyPairSync, randomBytes } from 'node:crypto';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const dir = mkdtempSync(join(tmpdir(), 'feature-flag-smoke-'));
const secrets = join(dir, 'secrets');
mkdirSync(secrets, { mode: 0o700 });
const suffix = randomBytes(6).toString('hex');
const project = 'ff-smoke-' + suffix;
const randomSecret = () => randomBytes(24).toString('hex');
function port(name, fallback) {
  const value = Number(process.env[name] || fallback);
  if (!Number.isInteger(value) || value < 1024 || value > 65535) throw new Error('Invalid ' + name);
  return value;
}
const frontendPort = port('SMOKE_FRONTEND_PORT', 3100);
const smtpPort = port('SMOKE_SMTP_PORT', 2525);
const mailApiPort = port('SMOKE_MAIL_API_PORT', 2526);
const keys = generateKeyPairSync('rsa', { modulusLength: 2048,
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  publicKeyEncoding: { type: 'spki', format: 'pem' } });
// The private host directory limits access; mounted files must be readable by container UID 10001.
writeFileSync(join(secrets, 'private.pem'), keys.privateKey, { mode: 0o644 });
writeFileSync(join(secrets, 'public.pem'), keys.publicKey, { mode: 0o644 });
const env = {
  MYSQL_ROOT_PASSWORD: randomSecret(),
  AUTH_JWT_PRIVATE_KEY_FILE: join(secrets, 'private.pem').replaceAll('\\', '/'),
  JWT_PUBLIC_KEY_FILE: join(secrets, 'public.pem').replaceAll('\\', '/'),
  JWT_ISSUER: 'feature-flag-auth', JWT_AUDIENCE: 'feature-flag-api',
  JWT_KEY_ID: 'smoke-' + suffix, JWT_ACCESS_TOKEN_TTL: '15m',
  AUTH_RECIPIENTS_SERVICE_KEY: randomSecret(), NOTIFICATION_INTERNAL_SERVICE_KEY: randomSecret(),
  FRONTEND_BASE_URL: 'http://localhost:' + frontendPort, FRONTEND_PORT: String(frontendPort),
  KAFKA_EXTERNAL_PORT: String(port('SMOKE_KAFKA_PORT', 39092)),
  NOTIFICATION_MAIL_USERNAME: 'mailer@example.test', NOTIFICATION_MAIL_PASSWORD: randomSecret(),
  NOTIFICATION_MAIL_CONNECTION_TIMEOUT_MS: '2000', NOTIFICATION_MAIL_READ_TIMEOUT_MS: '2000',
  NOTIFICATION_MAIL_WRITE_TIMEOUT_MS: '2000', BOOTSTRAP_OWNER_ENABLED: 'true',
  BOOTSTRAP_OWNER_NAME: 'Example Owner', BOOTSTRAP_OWNER_EMAIL: 'owner@example.test',
  BOOTSTRAP_OWNER_PASSWORD: randomSecret(),
};
for (const service of ['AUTH', 'FLAG', 'AUDIT', 'ANALYTICS', 'NOTIFICATION']) {
  env[service + '_DB_USERNAME'] = service.toLowerCase() + '_smoke';
  env[service + '_DB_PASSWORD'] = randomSecret();
}
const envFile = join(dir, '.env');
writeFileSync(envFile, Object.entries(env).map(([key, value]) => key + '=' + value).join('\n') + '\n', { mode: 0o600 });
const services = {};
for (const name of ['auth-service', 'flag-service', 'audit-service', 'analytics-service',
  'notification-service', 'api-gateway', 'eureka-server', 'frontend']) {
  services[name] = { image: (name === 'frontend' ? 'feature-flag-frontend' : name) + ':' + project };
}
// SMTP is captured locally; these checks never contact an external mail provider.
services['notification-service'].environment = {
  SPRING_APPLICATION_JSON: JSON.stringify({ spring: { mail: {
    host: process.env.SMOKE_SMTP_HOST || 'host.docker.internal', port: smtpPort,
    properties: { 'mail.smtp.auth': false, 'mail.smtp.starttls.enable': false },
  } } }),
};
const override = join(dir, 'compose.json');
writeFileSync(override, JSON.stringify({ services }, null, 2));
writeFileSync(join(dir, 'state.json'), JSON.stringify({ project, envFile, override, root, smtpPort, mailApiPort }, null, 2));
console.log(dir);
