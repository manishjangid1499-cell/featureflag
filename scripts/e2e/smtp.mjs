import net from 'node:net';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
if (!process.env.SMOKE_WORK_DIR) throw new Error('Set SMOKE_WORK_DIR before starting the mail sink');
const state = JSON.parse(fs.readFileSync(path.join(process.env.SMOKE_WORK_DIR, 'state.json')));
const inbox = [];
let rejectDelivery = false;
const smtp = net.createServer(socket => {
  let pending = '', data = false, message = '';
  socket.setEncoding('utf8');
  socket.write('220 fixture.local SMTP ready\r\n');
  socket.on('error', () => {});
  socket.on('data', chunk => {
    pending += chunk;
    while (pending.includes('\n')) {
      const split = pending.indexOf('\n');
      const line = pending.slice(0, split).replace(/\r$/, '');
      pending = pending.slice(split + 1);
      if (data) {
        if (line === '.') {
          if (rejectDelivery) {
            message = ''; data = false; socket.write('451 simulated SMTP failure\r\n'); continue;
          }
          inbox.push(message); if (inbox.length > 100) inbox.shift();
          message = ''; data = false; socket.write('250 accepted into test memory\r\n');
        } else {
          message += line.replace(/^\.\./, '.') + '\r\n';
          if (message.length > 1048576) socket.destroy();
        }
      } else if (/^(EHLO|HELO) /i.test(line)) socket.write('250-fixture.local\r\n250-8BITMIME\r\n250 SIZE 1048576\r\n');
      else if (/^DATA$/i.test(line)) { data = true; socket.write('354 finish with dot\r\n'); }
      else if (/^QUIT$/i.test(line)) socket.end('221 bye\r\n');
      else socket.write('250 OK\r\n');
    }
  });
});
const control = http.createServer((req, res) => {
  if (!req.headers.origin && req.method === 'POST' && ['/fail', '/recover'].includes(req.url)) {
    rejectDelivery = req.url === '/fail'; res.end('ok'); return;
  }
  if (req.headers.origin || req.url !== '/messages') { res.writeHead(403); res.end(); return; }
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify(inbox));
});
smtp.listen(state.smtpPort, process.env.SMOKE_SMTP_BIND || '127.0.0.1');
control.listen(state.mailApiPort, '127.0.0.1', () => console.log('Local SMTP fixture ready; no forwarding or disk persistence'));
