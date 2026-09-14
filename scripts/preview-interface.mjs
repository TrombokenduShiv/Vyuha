// Local preview for the standalone intro and the existing dashboard build.
// Run `npm run build` in apps/judge-dashboard, then `node scripts/preview-interface.mjs`.
import { createServer } from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, sep, extname } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dashboard = resolve(root, 'apps/judge-dashboard/dist');
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.woff2': 'font/woff2' };
const server = createServer((request, response) => {
  if (!['GET', 'HEAD'].includes(request.method)) { response.writeHead(405, { Allow: 'GET, HEAD' }); response.end(); return; }
  let pathname;
  try { pathname = decodeURIComponent(new URL(request.url, 'http://127.0.0.1').pathname); }
  catch { response.writeHead(400); response.end('Invalid URL'); return; }
  if (pathname === '/dashboard') { response.writeHead(308, { Location: '/dashboard/' }); response.end(); return; }
  let file;
  if (pathname === '/' || pathname === '/index.html') file = resolve(root, 'index.html');
  else if (pathname.startsWith('/dashboard/')) {
    file = resolve(dashboard, pathname.slice('/dashboard/'.length) || 'index.html');
    if (!file.startsWith(dashboard + sep)) { response.writeHead(403); response.end(); return; }
  } else { response.writeHead(404); response.end(); return; }
  if (!existsSync(file) || !statSync(file).isFile()) { response.writeHead(404); response.end('Not found. Build apps/judge-dashboard before previewing.'); return; }
  response.writeHead(200, { 'Content-Type': mime[extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  if (request.method === 'HEAD') response.end();
  else createReadStream(file).on('error', () => response.destroy()).pipe(response);
});
server.on('error', error => { console.error(error.message); process.exitCode = 1; });
server.listen(4173, '127.0.0.1', () => console.log('VYUHA interface: http://127.0.0.1:4173/'));

