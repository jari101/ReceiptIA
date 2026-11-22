// Texturas desenhadas em código (canvas): azulejos, recibo, post-it, calendário, armários.
// Nada é descarregado da Internet e tudo usa um gerador aleatório com semente,
// por isso o vídeo sai sempre igual.
import * as THREE from 'three';

/** Gerador aleatório com semente (mulberry32): os mesmos números em cada render. */
export function rng(seed) {
  return () => {
    seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function makeCanvas(w, h) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  return c;
}

export function toTexture(canvas, { flipY = true, srgb = true, repeat = null } = {}) {
  const tex = new THREE.CanvasTexture(canvas);
  if (srgb) tex.colorSpace = THREE.SRGBColorSpace;
  tex.flipY = flipY; tex.anisotropy = 8;
  if (repeat) { tex.wrapS = tex.wrapT = THREE.RepeatWrapping; tex.repeat.set(repeat[0], repeat[1]); }
  return tex;
}

export function canvasTex(w, h, draw, flipY = true) {
  const c = makeCanvas(w, h);
  draw(c.getContext('2d'), w, h);
  return toTexture(c, { flipY });
}

export function roundRect(g, x, y, w, h, r) {
  g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r);
  g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath();
}

/** Grão/ruído fino por cima de uma superfície (papel, pedra...). */
function grain(g, w, h, rnd, count, alpha, dark = '#000', light = '#fff') {
  for (let i = 0; i < count; i++) {
    g.globalAlpha = alpha * rnd();
    g.fillStyle = rnd() < 0.5 ? dark : light;
    g.fillRect(rnd() * w, rnd() * h, 1 + rnd() * 1.5, 1 + rnd() * 1.5);
  }
  g.globalAlpha = 1;
}

/** "Letra à mão": cada letra com tamanho, inclinação e linha de base ligeiramente diferentes. */
export function handwrite(g, text, x, y, size, rnd, color = '#24408e') {
  let cx = x;
  g.fillStyle = color;
  for (const ch of text) {
    const s = size * (0.93 + rnd() * 0.14);
    g.font = `italic ${s.toFixed(1)}px "Bitstream Charter", "Caladea", serif`;
    g.save();
    g.translate(cx, y + (rnd() - 0.5) * size * 0.09);
    g.rotate(-0.05 + (rnd() - 0.5) * 0.09);
    g.globalAlpha = 0.82 + rnd() * 0.18;
    g.fillText(ch, 0, 0);
    g.restore();
    cx += g.measureText(ch).width * (0.98 + rnd() * 0.06);
  }
  g.globalAlpha = 1;
  return cx;
}

// ---------------------------------------------------------------------------
// Azulejo português (padrão azul e branco). 4×4 azulejos por textura.
// ---------------------------------------------------------------------------
export function azulejoTextures() {
  const N = 4, T = 256, S = N * T;
  const color = makeCanvas(S, S), bump = makeCanvas(S, S);
  const g = color.getContext('2d'), b = bump.getContext('2d');
  const rnd = rng(7);
  const blue = '#22519c', deep = '#163a78';

  b.fillStyle = '#fff'; b.fillRect(0, 0, S, S);
  for (let ty = 0; ty < N; ty++) {
    for (let tx = 0; tx < N; tx++) {
      const x0 = tx * T, y0 = ty * T;
      // Vidrado branco, cada azulejo com um tom ligeiramente diferente (são pintados à mão).
      const tint = Math.round(240 + rnd() * 10);
      g.fillStyle = `rgb(${tint - 2},${tint},${Math.min(255, tint + 4)})`;
      g.fillRect(x0, y0, T, T);
      const gr = g.createRadialGradient(x0 + T * 0.4, y0 + T * 0.35, 10, x0 + T / 2, y0 + T / 2, T * 0.75);
      gr.addColorStop(0, 'rgba(255,255,255,0.35)'); gr.addColorStop(1, 'rgba(180,190,200,0.12)');
      g.fillStyle = gr; g.fillRect(x0, y0, T, T);

      g.save();
      g.beginPath(); g.rect(x0, y0, T, T); g.clip();
      g.translate(x0 + T / 2, y0 + T / 2);
      const wobble = () => (rnd() - 0.5) * 2.2;
      g.globalAlpha = 0.9;
      // Quartos de círculo nos cantos: juntos formam círculos entre 4 azulejos.
      for (const [cx, cy] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
        g.fillStyle = blue;
        g.beginPath(); g.arc(cx * T / 2 + wobble(), cy * T / 2 + wobble(), T * 0.3, 0, Math.PI * 2); g.fill();
        g.fillStyle = `rgb(${tint},${tint},${tint + 3})`;
        g.beginPath(); g.arc(cx * T / 2, cy * T / 2, T * 0.2, 0, Math.PI * 2); g.fill();
        g.fillStyle = deep;
        g.beginPath(); g.arc(cx * T / 2, cy * T / 2, T * 0.09, 0, Math.PI * 2); g.fill();
      }
      // Flor central de 4 pétalas.
      for (let k = 0; k < 4; k++) {
        g.save(); g.rotate(k * Math.PI / 2 + Math.PI / 4);
        g.fillStyle = blue;
        g.beginPath(); g.ellipse(0, -T * 0.17, T * 0.075, T * 0.15, 0, 0, Math.PI * 2); g.fill();
        g.fillStyle = `rgb(${tint},${tint},${tint + 3})`;
        g.beginPath(); g.ellipse(0, -T * 0.17, T * 0.03, T * 0.08, 0, 0, Math.PI * 2); g.fill();
        // Folhinhas em direção aos lados.
        g.rotate(Math.PI / 4);
        g.fillStyle = deep;
        g.beginPath(); g.ellipse(0, -T * 0.3, T * 0.025, T * 0.06, 0, 0, Math.PI * 2); g.fill();
        g.restore();
      }
      g.fillStyle = deep; g.beginPath(); g.arc(0, 0, T * 0.06, 0, Math.PI * 2); g.fill();
      g.fillStyle = '#f2f4f8'; g.beginPath(); g.arc(0, 0, T * 0.025, 0, Math.PI * 2); g.fill();
      g.restore();
      g.globalAlpha = 1;

      // Juntas (cinzento claro) e relevo: juntas mais fundas, bordos do azulejo boleados.
      g.fillStyle = '#cfd4d6';
      g.fillRect(x0, y0, T, 3); g.fillRect(x0, y0, 3, T);
      b.fillStyle = '#5a5a5a'; b.fillRect(x0, y0, T, 4); b.fillRect(x0, y0, 4, T);
      b.fillStyle = '#c8c8c8'; b.fillRect(x0 + 4, y0 + 4, T - 4, 4); b.fillRect(x0 + 4, y0 + 4, 4, T - 4);
    }
  }
  grain(g, S, S, rnd, 9000, 0.05, '#7a8da8', '#ffffff');
  return { map: toTexture(color, { repeat: [1, 1] }), bump: toTexture(bump, { srgb: false, repeat: [1, 1] }) };
}

// ---------------------------------------------------------------------------
// Frentes dos armários (portas brancas com juntas e puxadores).
// ---------------------------------------------------------------------------
export function cabinetTexture(doors, w = 1024, h = 512, handleAt = 'top') {
  return canvasTex(w, h, (g) => {
    g.fillStyle = '#f3f4f5'; g.fillRect(0, 0, w, h);
    const dw = w / doors;
    for (let i = 0; i < doors; i++) {
      const x = i * dw;
      const gr = g.createLinearGradient(x, 0, x + dw, h);
      gr.addColorStop(0, '#f7f8f9'); gr.addColorStop(1, '#e6e9eb');
      g.fillStyle = gr; g.fillRect(x + 4, 4, dw - 8, h - 8);
      g.fillStyle = '#9aa3aa';
      const hx = i % 2 === 0 ? x + dw - 40 : x + 28;
      if (handleAt === 'top') g.fillRect(hx, 40, 12, 90); else g.fillRect(hx, h - 130, 12, 90);
    }
    g.fillStyle = '#b8c0c6';
    for (let i = 0; i <= doors; i++) g.fillRect(i * dw - 2, 0, 4, h);
  });
}

/** Bancada de pedra clara com pequenas pintas. */
export function stoneTexture() {
  return canvasTex(1024, 512, (g, w, h) => {
    g.fillStyle = '#e7e2da'; g.fillRect(0, 0, w, h);
    const rnd = rng(21);
    grain(g, w, h, rnd, 26000, 0.25, '#9b9388', '#ffffff');
    g.globalAlpha = 0.08; g.strokeStyle = '#a9a196'; g.lineWidth = 2;
    for (let i = 0; i < 9; i++) {
      g.beginPath(); let x = rnd() * w, y = rnd() * h; g.moveTo(x, y);
      for (let k = 0; k < 6; k++) { x += (rnd() - 0.3) * 160; y += (rnd() - 0.5) * 60; g.lineTo(x, y); }
      g.stroke();
    }
    g.globalAlpha = 1;
  });
}

// ---------------------------------------------------------------------------
// Post-it com a lista de compras feita "de memória" (o problema que o ReceiptAI resolve).
// ---------------------------------------------------------------------------
export function postItTexture() {
  return canvasTex(512, 512, (g, w, h) => {
    const rnd = rng(33);
    const gr = g.createLinearGradient(0, 0, 0, h);
    gr.addColorStop(0, '#ffe46b'); gr.addColorStop(0.18, '#ffec88'); gr.addColorStop(1, '#f9df6a');
    g.fillStyle = gr; g.fillRect(0, 0, w, h);
    g.fillStyle = 'rgba(0,0,0,0.04)'; g.fillRect(0, 0, w, 60); // faixa da cola
    grain(g, w, h, rnd, 5000, 0.08, '#b89a2a', '#fff8c8');
    handwrite(g, 'Comprar:', 40, 105, 56, rnd);
    handwrite(g, '- leite?', 58, 185, 50, rnd);
    handwrite(g, '- ovos (temos?)', 58, 260, 50, rnd);
    handwrite(g, '- pão', 58, 335, 50, rnd);
    const end = handwrite(g, '- iogurtes', 58, 410, 50, rnd);
    // Riscado: alguém já tinha comprado...
    g.strokeStyle = '#24408e'; g.lineWidth = 4; g.globalAlpha = 0.8;
    g.beginPath(); g.moveTo(52, 398); g.lineTo(end + 6, 392); g.stroke();
    g.globalAlpha = 1;
    handwrite(g, '???', 360, 470, 46, rnd, '#b0303a');
  });
}

// ---------------------------------------------------------------------------
// Calendário de novembro de 2026 (preso com um íman). 1/11/2026 é um domingo.
// ---------------------------------------------------------------------------
export function calendarTexture() {
  return canvasTex(512, 700, (g, w, h) => {
    const rnd = rng(44);
    g.fillStyle = '#fbfbf8'; g.fillRect(0, 0, w, h);
    grain(g, w, h, rnd, 4000, 0.05, '#999', '#fff');
    g.fillStyle = '#c0392b'; g.fillRect(0, 70, w, 90);
    g.fillStyle = '#fff'; g.font = '700 54px Inter'; g.textAlign = 'center';
    g.fillText('NOVEMBRO 2026', w / 2, 135);
    const days = ['S', 'T', 'Q', 'Q', 'S', 'S', 'D'];
    const cw = (w - 40) / 7;
    g.font = '700 26px Inter'; g.fillStyle = '#555';
    days.forEach((d, i) => g.fillText(d, 20 + cw * (i + 0.5), 205));
    g.font = '500 30px Inter';
    let day = 1, row = 0, col = 6; // começa ao domingo
    while (day <= 30) {
      const x = 20 + cw * (col + 0.5), y = 260 + row * 72;
      g.fillStyle = col === 6 ? '#c0392b' : '#2b2b2b';
      g.fillText(String(day), x, y);
      if (day === 19) { // dia de hoje marcado a caneta
        g.strokeStyle = '#24408e'; g.lineWidth = 4; g.beginPath();
        g.ellipse(x + 1, y - 10, 30, 24, -0.2, 0, Math.PI * 2); g.stroke();
      }
      day++; col++; if (col > 6) { col = 0; row++; }
    }
    g.strokeStyle = '#e2e2e2'; g.lineWidth = 2;
    for (let r = 0; r <= 6; r++) { g.beginPath(); g.moveTo(20, 222 + r * 72); g.lineTo(w - 20, 222 + r * 72); g.stroke(); }
    handwrite(g, 'Jantar avós - sáb', 40, 685, 34, rnd);
  });
}

// ---------------------------------------------------------------------------
// Recibo em papel térmico: frente impressa (com tinta gasta) e verso sem impressão.
// O contorno tem os dentes do corte; fora do papel é transparente (alphaTest).
// ---------------------------------------------------------------------------
export const RECEIPT_W = 640, RECEIPT_H = 1600;

function paperShape(g, w, h, rnd) {
  const tooth = 18;
  g.beginPath();
  g.moveTo(0, 12);
  for (let x = 0; x <= w; x += tooth) g.lineTo(x + tooth / 2, 2 + rnd() * 6), g.lineTo(Math.min(w, x + tooth), 12 + rnd() * 4);
  g.lineTo(w, h - 12);
  for (let x = w; x >= 0; x -= tooth) g.lineTo(x - tooth / 2, h - 2 - rnd() * 6), g.lineTo(Math.max(0, x - tooth), h - 12 - rnd() * 4);
  g.closePath();
}

function paperBase(g, w, h, rnd) {
  paperShape(g, w, h, rnd);
  g.save(); g.clip();
  const gr = g.createLinearGradient(0, 0, w, 0);
  gr.addColorStop(0, '#e9e6df'); gr.addColorStop(0.12, '#f4f2ec'); gr.addColorStop(0.88, '#f4f2ec'); gr.addColorStop(1, '#e7e4dc');
  g.fillStyle = gr; g.fillRect(0, 0, w, h);
  // Fibras do papel.
  g.lineWidth = 1;
  for (let i = 0; i < 2600; i++) {
    g.globalAlpha = 0.04 + rnd() * 0.05;
    g.strokeStyle = rnd() < 0.5 ? '#b9b3a6' : '#ffffff';
    const x = rnd() * w, y = rnd() * h, a = rnd() * Math.PI, l = 3 + rnd() * 9;
    g.beginPath(); g.moveTo(x, y); g.lineTo(x + Math.cos(a) * l, y + Math.sin(a) * l); g.stroke();
  }
  grain(g, w, h, rnd, 9000, 0.06, '#9c968a', '#ffffff');
  // Vinco da dobra (a 42% do comprimento).
  const fy = h * 0.42;
  const fold = g.createLinearGradient(0, fy - 22, 0, fy + 22);
  fold.addColorStop(0, 'rgba(0,0,0,0)'); fold.addColorStop(0.45, 'rgba(120,110,95,0.16)');
  fold.addColorStop(0.55, 'rgba(255,255,255,0.35)'); fold.addColorStop(1, 'rgba(0,0,0,0)');
  g.globalAlpha = 1; g.fillStyle = fold; g.fillRect(0, fy - 22, w, 44);
  g.restore();
}

/** Código QR a fingir (decorativo, com os 3 quadrados de posição). */
function fakeQr(g, x, y, size, rnd) {
  const n = 25, m = size / n;
  for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) if (rnd() < 0.48) g.fillRect(x + c * m, y + r * m, m, m);
  for (const [fx, fy] of [[0, 0], [n - 7, 0], [0, n - 7]]) {
    g.clearRect(x + fx * m - m, y + fy * m - m, 9 * m, 9 * m);
    g.fillRect(x + fx * m, y + fy * m, 7 * m, 7 * m);
    g.clearRect(x + (fx + 1) * m, y + (fy + 1) * m, 5 * m, 5 * m);
    g.fillRect(x + (fx + 2) * m, y + (fy + 2) * m, 3 * m, 3 * m);
  }
}

export function receiptTextures() {
  const w = RECEIPT_W, h = RECEIPT_H;
  // 1) Tinta: escrita num canvas à parte para a podermos "gastar".
  const ink = makeCanvas(w, h), k = ink.getContext('2d');
  const rnd = rng(1234);
  const COLS = 32, left = 54, cw = (w - 2 * left) / COLS;
  k.fillStyle = '#262626';
  const mono = (wgt, px) => `${wgt} ${px}px "DejaVu Sans Mono", monospace`;
  let y = 96;
  const center = (s, wgt = 400, px = 25, lh = 36) => { k.font = mono(wgt, px); k.textAlign = 'center'; k.fillText(s, w / 2, y); y += lh; };
  const line = (l, r = '', wgt = 400, px = 25, lh = 36) => {
    k.font = mono(wgt, px); k.textAlign = 'left'; k.fillText(l, left, y);
    if (r) { k.textAlign = 'right'; k.fillText(r, w - left, y); }
    y += lh;
  };
  const dash = () => { k.font = mono(400, 25); k.textAlign = 'left'; k.fillText('-'.repeat(COLS), left, y); y += 34; };

  center('MERCADO DO BAIRRO', 700, 36, 46);
  center('Rua das Flores, 12', 400, 23, 32);
  center('1000-100 Lisboa', 400, 23, 32);
  center('NIF 500 000 000', 400, 23, 32);
  center('Tel. 210 000 000', 400, 23, 44);
  center('FATURA SIMPLIFICADA', 700, 25, 34);
  center('FS 2026/1234', 400, 24, 40);
  line('19-11-2026 18:42', 'Caixa 03', 400, 24, 38);
  dash();
  line('LEITE MEIO GORDO 1L', 'C  0,89');
  line('IOGURTE NATURAL 4X125G');
  line('  2 X 1,49', 'C  2,98');
  line('BANANA');
  line('  0,850 kg X 1,19 €/kg', 'C  1,01');
  line('PAO DE FORMA INTEGRAL', 'C  1,49');
  line('OVOS CLASSE M 12UN', 'C  2,59');
  line('  DESC. PROMOCAO', '-0,30');
  line('ATUM EM LATA 3X80G', 'C  3,87');
  line('QUEIJO FLAMENGO FATIAS', 'C  2,29', 400, 25, 40);
  dash();
  line('TOTAL', '€ 14,82', 700, 34, 50);
  line('MULTIBANCO', '14,82');
  line('Artigos: 8', '', 400, 23, 44);
  line('IVA   Taxa   Base    Valor', '', 400, 22, 32);
  line('C     6%     13,98   0,84', '', 400, 22, 44);
  dash();
  center('ATCUD: JFX7K2-1234', 400, 22, 30);
  y += 10;
  fakeQr(k, w / 2 - 95, y, 190, rnd); y += 230;
  center('Processado por programa', 400, 21, 28);
  center('certificado n.º 0000/AT', 400, 21, 44);
  center('Obrigado e volte sempre!', 700, 25, 36);
  void cw;

  // Impressão térmica gasta: pontinhos em falta e duas faixas mais claras (cabeça de impressão).
  k.globalCompositeOperation = 'destination-out';
  for (let i = 0; i < 16000; i++) {
    k.globalAlpha = 0.15 + rnd() * 0.4;
    k.fillRect(rnd() * w, rnd() * h, 1 + rnd() * 2, 1 + rnd() * 2);
  }
  k.globalAlpha = 0.28; k.fillRect(w * 0.31, 0, 7, h);
  k.globalAlpha = 0.18; k.fillRect(w * 0.73, 0, 4, h);
  k.globalAlpha = 1; k.globalCompositeOperation = 'source-over';

  // 2) Frente: papel + tinta.
  const front = makeCanvas(w, h), f = front.getContext('2d');
  const rf = rng(99);
  paperBase(f, w, h, rf);
  f.save(); paperShape(f, w, h, rng(99)); f.clip();
  f.globalAlpha = 0.9; f.drawImage(ink, 0, 0); f.globalAlpha = 1;
  f.restore();

  // 3) Verso: só papel (mesmo contorno).
  const back = makeCanvas(w, h), bk = back.getContext('2d');
  paperBase(bk, w, h, rng(99));

  // 4) Relevo das fibras (bump) para a luz "agarrar" no papel.
  const bump = makeCanvas(256, 640), bb = bump.getContext('2d');
  bb.fillStyle = '#808080'; bb.fillRect(0, 0, 256, 640);
  grain(bb, 256, 640, rng(5), 30000, 0.5, '#606060', '#a0a0a0');

  return {
    front: toTexture(front),
    back: toTexture(back),
    bump: toTexture(bump, { srgb: false }),
  };
}
