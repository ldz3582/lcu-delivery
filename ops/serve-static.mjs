// 本地静态托管：托管前端构建产物，并把 /api、/uploads 代理到后端。
// 用法: node serve-static.mjs <port> <rootDir> [backendBase]
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const port = Number(process.argv[2] || 5176);
const root = path.resolve(process.argv[3] || '.');
const backend = process.argv[4] || 'http://127.0.0.1:8082';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.wav': 'audio/wav',
  '.mp3': 'audio/mpeg',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
};

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');

  if (url.pathname.startsWith('/api') || url.pathname.startsWith('/uploads')) {
    const target = new URL(url.pathname + url.search, backend);
    const headers = { ...req.headers, host: target.host };
    const upstream = http.request(
      {
        hostname: target.hostname,
        port: target.port,
        path: target.pathname + target.search,
        method: req.method,
        headers,
      },
      (up) => {
        res.writeHead(up.statusCode || 502, up.headers);
        up.pipe(res);
      },
    );
    upstream.on('error', () => {
      res.writeHead(502, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ code: 502, msg: 'backend unreachable' }));
    });
    req.pipe(upstream);
    return;
  }

  let filePath = path.join(root, decodeURIComponent(url.pathname));
  if (url.pathname === '/' || !path.extname(filePath)) filePath = path.join(root, 'index.html');
  fs.readFile(filePath, (err, data) => {
    if (err) {
      fs.readFile(path.join(root, 'index.html'), (err2, html) => {
        if (err2) {
          res.writeHead(404);
          res.end('not found');
          return;
        }
        res.writeHead(200, { 'Content-Type': MIME['.html'] });
        res.end(html);
      });
      return;
    }
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(filePath).toLowerCase()] || 'application/octet-stream',
    });
    res.end(data);
  });
});

server.listen(port, '127.0.0.1', () =>
  console.log(`serving ${root} on http://127.0.0.1:${port}  (api -> ${backend})`),
);
