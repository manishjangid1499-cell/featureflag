import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const root = fileURLToPath(new URL('../../', import.meta.url));
if (!process.env.SMOKE_WORK_DIR) throw new Error('Set SMOKE_WORK_DIR to the directory returned by prepare.mjs');
const dir = fs.realpathSync(process.env.SMOKE_WORK_DIR);
const relative = path.relative(root, dir);
if (relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative)) {
  throw new Error('Smoke state must stay outside the repository');
}
const state = JSON.parse(fs.readFileSync(path.join(dir, 'state.json')));
if (!/^ff-smoke-[a-f0-9]{12}$/.test(state.project) || path.resolve(state.root) !== path.resolve(root)) {
  throw new Error('Smoke state does not belong to this checkout');
}
const settings = Object.fromEntries(fs.readFileSync(state.envFile, 'utf8').trim().split(/\r?\n/)
  .map(line => { const i = line.indexOf('='); return [line.slice(0, i), line.slice(i + 1)]; }));
const base = settings.FRONTEND_BASE_URL;
const mailApi = 'http://127.0.0.1:' + state.mailApiPort;
const results = [];
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
function insist(value, label) { if (!value) throw new Error(label); }
function decodeMail(message) {
  if (!/Content-Transfer-Encoding:\s*quoted-printable/i.test(message)) return message;
  return message.replace(/=\r?\n/g, '').replace(/=([a-f0-9]{2})/gi,
    (_, hex) => String.fromCharCode(parseInt(hex, 16)));
}
function record(name, details = {}) {
  const result = { name, status: 'PASS', ...details }; results.push(result);
  fs.writeFileSync(path.join(dir, path.basename(process.argv[1], '.mjs') + '-results.json'), JSON.stringify(results, null, 2));
  console.log(JSON.stringify(result));
}
async function processRun(command, args, input, env = process.env, includeStderr = false) {
  return await new Promise((resolve, reject) => {
    const child = spawn(command, args, { windowsHide: true, cwd: root, env, stdio: ['pipe', 'pipe', 'pipe'] });
    let stdout = '', stderr = '';
    child.stdout.on('data', value => { stdout += value; });
    child.stderr.on('data', value => { stderr += value; });
    child.on('error', () => reject(new Error('Failed to start smoke command')));
    child.on('close', code => code === 0 ? resolve(stdout + (includeStderr ? stderr : '')) : reject(new Error(`Smoke command failed (${command}, exit ${code})`)));
    child.stdin.end(input);
  });
}
const composeArgs = ['compose', '--env-file', state.envFile, '-p', state.project,
  '-f', path.join(root, 'docker-compose.yml'), '-f', state.override];
async function ownedContainer(service) {
  const id = (await processRun('docker', [...composeArgs, 'ps', '-a', '-q', service])).trim();
  insist(/^[a-f0-9]{12,64}$/.test(id), 'Expected exactly one isolated container');
  const project = (await processRun('docker', ['inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', id])).trim();
  insist(project === state.project, 'Container does not belong to smoke project');
  return id;
}
async function serviceAction(service, action) {
  const id = await ownedContainer(service);
  await processRun('docker', [action, id]);
}
async function waitUntil(fn, label, timeout = 90000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try { const result = await fn(); if (result) return result; } catch { }
    await pause(750);
  }
  throw new Error(`Timed out: ${label}`);
}
async function api(route, { method = 'GET', token, body, headers = {}, expected } = {}) {
  const start = performance.now();
  const response = await fetch(base + route, {
    method, signal: AbortSignal.timeout(15000), headers: { Origin: base,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...headers },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {})
  }).catch(() => { throw new Error(`HTTP timeout/network failure: ${method} ${route.split('?')[0]}`); });
  const raw = await response.text();
  let data; try { data = JSON.parse(raw); } catch { data = raw; }
  if (expected !== undefined) insist(response.status === expected, `HTTP contract: ${method} ${route.split('?')[0]} expected ${expected}, got ${response.status}`);
  return { status: response.status, data, elapsedMs: Math.round(performance.now() - start), headers: response.headers };
}
async function login(email, password) {
  const result = await api('/auth/login', { method: 'POST', body: { email, password }, expected: 200 });
  insist(typeof result.data.token === 'string', 'Login token absent'); return result.data.token;
}
async function browserSession() {
  const chrome = process.env.BROWSER_EXECUTABLE;
  insist(chrome && fs.existsSync(chrome), 'Set BROWSER_EXECUTABLE to an installed Chromium browser');
  const child = spawn(chrome, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=0',
    `--user-data-dir=${path.join(dir, 'browser-' + path.basename(process.argv[1], '.mjs'))}`, 'about:blank'], { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] });
  let diagnostics = '';
  child.stderr.on('data', chunk => { diagnostics += chunk; });
  let tab;
  try {
    // Use the child process endpoint so the test cannot attach to an existing browser.
    const endpoint = await waitUntil(() => diagnostics.match(/DevTools listening on (ws:\/\/127\.0\.0\.1:\d+\/devtools\/browser\/[^\s]+)/)?.[1], 'owned browser', 30000);
    const authority = new URL(endpoint).host;
    tab = await (await fetch(`http://${authority}/json/new?about:blank`, { method: 'PUT' })).json();
  } catch (error) { child.kill(); throw error; }
  const ws = new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise(resolve => ws.addEventListener('open', resolve, { once: true }));
  let next = 1;
  const waiting = new Map(), requests = [];
  ws.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    if (message.id && waiting.has(message.id)) {
      const { resolve, reject, timer } = waiting.get(message.id); clearTimeout(timer); waiting.delete(message.id);
      if (message.error) reject(new Error('Browser protocol command failed')); else resolve(message.result);
    } else if (message.method === 'Network.responseReceived') {
      const response = message.params.response;
      if (response.url.startsWith(base)) requests.push({ path: new URL(response.url).pathname, status: response.status });
    }
  });
  function call(method, params = {}) {
    return new Promise((resolve, reject) => {
      const id = next++; const timer = setTimeout(() => { waiting.delete(id); reject(new Error(`Browser timeout: ${method}`)); }, 20000);
      waiting.set(id, { resolve, reject, timer }); ws.send(JSON.stringify({ id, method, params }));
    });
  }
  async function evaluate(expression) {
    const result = await call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    insist(!result.exceptionDetails, 'Browser expression failed'); return result.result.value;
  }
  await call('Page.enable'); await call('Runtime.enable'); await call('Network.enable');
  return { child, call, evaluate, requests, ws };
}

export { root, dir, state, settings, base, mailApi, results, insist, record, decodeMail,
  processRun, composeArgs, ownedContainer, serviceAction, waitUntil, api, login, browserSession };
