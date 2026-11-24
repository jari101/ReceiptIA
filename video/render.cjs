// Gera o vídeo: abre scene.html no Chromium (Playwright), desenha 900 frames (30 s × 30 fps)
// e envia-os para o ffmpeg. Uso:  node render.cjs            -> ReceiptAI_demo_30s.mp4
//                                 node render.cjs --preview  -> algumas imagens em preview/
const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '..'); // raiz do repositório (precisa de docs/ e video/)
const FPS = 30, SECONDS = 30;
const OUT = path.join(__dirname, 'ReceiptAI_demo_30s.mp4');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.png': 'image/png', '.glb': 'model/gltf-binary', '.json': 'application/json' };

function serve() {
  const server = http.createServer((req, res) => {
    const rel = decodeURIComponent(new URL(req.url, 'http://x').pathname);
    const file = path.join(ROOT, path.normalize(rel));
    if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
    fs.createReadStream(file).pipe(res);
  });
  return new Promise((ok) => server.listen(0, '127.0.0.1', () => ok(server)));
}

(async () => {
  const preview = process.argv.includes('--preview');
  const server = await serve();
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  page.on('pageerror', (e) => console.error('Erro na página:', e.message));
  page.on('console', (m) => { if (m.type() === 'error') console.error('Consola:', m.text()); });
  await page.goto(`http://127.0.0.1:${server.address().port}/video/scene.html`);
  await page.waitForFunction(() => window.sceneReady !== undefined);
  await page.evaluate(() => window.sceneReady);

  if (preview) {
    const dir = path.join(__dirname, 'preview');
    fs.mkdirSync(dir, { recursive: true });
    for (const t of [1.9, 3.4, 9.8, 15.0, 21.0]) {
      await page.evaluate((x) => window.renderFrame(x), t);
      await page.screenshot({ path: path.join(dir, `t${String(t).replace('.', '_')}.png`) });
      console.log('preview', t);
    }
    // Grandes planos para verificar detalhes que no vídeo passam depressa.
    const closeups = {
      laranjas: [[1.05, -0.3, 0.05], [1.05, -0.47, -0.42]],
      dobradica: [[0.95, 0.62, 0.45], [0.66, 0.5, -0.05]],
    };
    for (const [name, [pos, tgt]] of Object.entries(closeups)) {
      await page.evaluate(([p, q]) => window.debugLook(p, q), [pos, tgt]);
      await page.screenshot({ path: path.join(dir, `close_${name}.png`) });
      console.log('preview', name);
    }
  } else {
    const ff = spawn('ffmpeg', ['-y', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-',
      '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', OUT],
    { stdio: ['pipe', 'ignore', 'inherit'] });
    const total = FPS * SECONDS;
    for (let f = 0; f < total; f++) {
      await page.evaluate((x) => window.renderFrame(x), f / FPS);
      const png = await page.screenshot({ type: 'png' });
      if (!ff.stdin.write(png)) await new Promise((r) => ff.stdin.once('drain', r));
      if (f % 60 === 0) console.log(`frame ${f}/${total}`);
    }
    ff.stdin.end();
    await new Promise((r) => ff.on('close', r));
    console.log('Vídeo pronto:', OUT);
  }
  await browser.close();
  server.close();
})().catch((e) => { console.error(e); process.exit(1); });
