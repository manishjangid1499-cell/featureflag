import { spawn } from 'node:child_process';
import { composeArgs, root } from './helpers.mjs';

const actions = {
  config: ['config', '--quiet'],
  build: ['build'],
  up: ['up', '-d', '--wait', '--wait-timeout', '300'],
  ps: ['ps', '-a'],
  down: ['down', '--volumes'],
};
const action = actions[process.argv[2]];
if (!action) throw new Error('Use config, build, up, ps, or down');
// Every action uses the generated project name, including disposal of its test volumes.
const child = spawn('docker', [...composeArgs, ...action], {
  cwd: root, windowsHide: true, stdio: 'inherit',
  env: { ...process.env, COMPOSE_PARALLEL_LIMIT: '2' },
});
child.on('error', () => { console.error('Could not start Docker Compose'); process.exitCode = 1; });
child.on('close', code => { process.exitCode = code ?? 1; });
