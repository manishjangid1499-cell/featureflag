import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const root = fileURLToPath(new URL('../', import.meta.url));
const result = spawnSync('docker', [
  'compose', '-f', 'docker-compose.kafka-local.yml', 'config', '--format', 'json',
], { cwd: root, encoding: 'utf8', windowsHide: true });
assert.equal(result.status, 0, 'Local Kafka configuration must resolve without application secrets');
const { services } = JSON.parse(result.stdout);

test('local Kafka starts independently of the application and databases', () => {
  assert.deepEqual(Object.keys(services).sort(), ['kafka', 'kafka-init']);
  assert.equal(services.kafka.ports.length, 1);
  assert.equal(services.kafka.ports[0].host_ip, '127.0.0.1');
  assert.equal(String(services.kafka.ports[0].published), '9092');
  assert.equal(services.kafka.ports[0].target, 9092);
  assert.equal(services.kafka.volumes[0].type, 'volume');
});

test('advertised host listener matches all three local JVM bootstrap defaults', () => {
  assert.equal(services.kafka.environment.KAFKA_ADVERTISED_LISTENERS,
    'INTERNAL://kafka:29092,EXTERNAL://localhost:9092');
  for (const service of ['flag', 'audit', 'analytics']) {
    const local = readFileSync(new URL(
      `../${service}-service/${service}-service/src/main/resources/application-local.yml`,
      import.meta.url,
    ), 'utf8');
    const bootstrap = local.match(/^\s+bootstrap-servers:\s*(.+)$/m)?.[1].trim();
    assert.ok(bootstrap === 'localhost:9092'
      || bootstrap === '${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}', service);
  }
});

test('topic initialization waits for Kafka and preserves existing topics', () => {
  assert.equal(services['kafka-init'].depends_on.kafka.condition, 'service_healthy');
  assert.equal(String(services.kafka.environment.KAFKA_AUTO_CREATE_TOPICS_ENABLE), 'false');
  const command = services['kafka-init'].command.join(' ');
  assert.match(command, /--create --if-not-exists/);
  for (const topic of [
    'feature-flag-events', 'feature-flag-evaluations', 'notification-events',
    'feature-flag-events-audit-dlt', 'feature-flag-events-analytics-dlt', 'notification-events-dlt',
  ]) {
    assert.ok(command.split(/[\s;]+/).includes(topic), topic);
  }
});
