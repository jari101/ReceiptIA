// Cena 3D do vídeo ReceiptAI (30 s). Tudo é calculado a partir do tempo t (em segundos),
// por isso cada frame é sempre igual: o render.cjs chama window.renderFrame(t) 900 vezes.
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';

const W = 1920, H = 1080;
const ROOT = '/';

// ---------- Curvas de animação (skill animate) ----------
function cubicBezier(x1, y1, x2, y2) {
  const cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx;
  const cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
  const sx = (t) => ((ax * t + bx) * t + cx) * t;
  const sy = (t) => ((ay * t + by) * t + cy) * t;
  const dx = (t) => (3 * ax * t + 2 * bx) * t + cx;
  return (x) => {
    if (x <= 0) return 0; if (x >= 1) return 1;
    let t = x;
    for (let i = 0; i < 8; i++) { const e = sx(t) - x; const d = dx(t); if (Math.abs(e) < 1e-6 || Math.abs(d) < 1e-6) break; t -= e / d; }
    return sy(Math.min(1, Math.max(0, t)));
  };
}
const easeOut = cubicBezier(0.23, 1, 0.32, 1);      // entradas
const easeInOut = cubicBezier(0.77, 0, 0.175, 1);   // movimentos no ecrã / câmara
const clamp01 = (v) => Math.min(1, Math.max(0, v));
const prog = (t, a, b) => clamp01((t - a) / (b - a));
const lerp = (a, b, k) => a + (b - a) * k;
const v3 = (x, y, z) => new THREE.Vector3(x, y, z);

// ---------- Renderer, cena, câmara ----------
const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(1);
renderer.setSize(W, H);
renderer.toneMapping = THREE.NeutralToneMapping;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
document.body.insertBefore(renderer.domElement, document.getElementById('caption'));

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(35, W / H, 0.01, 20);

scene.add(new THREE.HemisphereLight(0xffffff, 0xb9c8d0, 1.35));
const key = new THREE.DirectionalLight(0xffffff, 2.4);
key.position.set(0.7, 1.1, 1.3);
key.castShadow = true;
key.shadow.mapSize.set(2048, 2048);
key.shadow.camera.left = -0.5; key.shadow.camera.right = 0.7; key.shadow.camera.top = 0.5; key.shadow.camera.bottom = -0.5;
key.shadow.camera.near = 0.5; key.shadow.camera.far = 3;
key.shadow.radius = 6; key.shadow.bias = -0.0004;
scene.add(key);
const fill = new THREE.DirectionalLight(0xe8f7ef, 0.7);
fill.position.set(-1, 0.3, 0.9);
scene.add(fill);

// ---------- Porta do frigorífico ----------
const door = new THREE.Mesh(
  new RoundedBoxGeometry(1.15, 1.6, 0.05, 6, 0.03),
  new THREE.MeshStandardMaterial({ color: 0xe9eef1, roughness: 0.5, metalness: 0.05 }),
);
door.position.set(0.12, -0.3, -0.03);
door.receiveShadow = true;
scene.add(door);
const handle = new THREE.Mesh(
  new RoundedBoxGeometry(0.022, 0.55, 0.03, 4, 0.01),
  new THREE.MeshStandardMaterial({ color: 0xc3ccd3, roughness: 0.25, metalness: 0.7 }),
);
handle.position.set(-0.41, -0.12, 0.012);
handle.castShadow = true;
scene.add(handle);

// ---------- Texturas auxiliares (canvas) ----------
function canvasTex(w, h, draw, flipY = true) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  draw(c.getContext('2d'), w, h);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace; tex.flipY = flipY; tex.anisotropy = 8;
  return tex;
}
function roundRect(g, x, y, w, h, r) {
  g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r);
  g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath();
}
function loadImage(src) {
  return new Promise((ok, fail) => { const i = new Image(); i.onload = () => ok(i); i.onerror = fail; i.src = src; });
}

// Recibo de papel (com texto) — substitui a malha "recibo" do GLB, que não tem coordenadas UV.
const receiptLines = [
  ['Leite meio gordo 1L', '0,89'], ['Iogurte natural x2', '2,98'], ['Bananas 0,850 kg', '1,01'],
  ['Pão de forma', '1,49'], ['Ovos classe M', '2,29'], ['Atum em lata', '3,87'], ['Queijo flamengo', '2,29'],
];
const receiptTex = canvasTex(400, 600, (g, w, h) => {
  g.fillStyle = '#fbfbf8'; g.fillRect(0, 0, w, h);
  g.fillStyle = '#12304A'; g.font = '700 30px "JetBrains Mono"'; g.textAlign = 'center';
  g.fillText('MERCADO DO BAIRRO', w / 2, 56);
  g.font = '400 20px "JetBrains Mono"'; g.fillStyle = '#546777'; g.fillText('19-11-2026  18:42', w / 2, 90);
  g.textAlign = 'left'; g.font = '500 21px "JetBrains Mono"'; g.fillStyle = '#1b2a36';
  receiptLines.forEach(([n, p], i) => { const y = 140 + i * 44; g.fillText(n.toUpperCase().slice(0, 20), 24, y); g.textAlign = 'right'; g.fillText(p, w - 24, y); g.textAlign = 'left'; });
  g.strokeStyle = '#8a99a6'; g.setLineDash([6, 6]); g.beginPath(); g.moveTo(24, 470); g.lineTo(w - 24, 470); g.stroke();
  g.font = '700 28px "JetBrains Mono"'; g.fillStyle = '#12304A'; g.fillText('TOTAL', 24, 515); g.textAlign = 'right'; g.fillText('14,82 €', w - 24, 515);
});
const receipt = new THREE.Mesh(
  new THREE.PlaneGeometry(0.076, 0.114),
  new THREE.MeshStandardMaterial({ map: receiptTex, roughness: 0.9, side: THREE.DoubleSide, transparent: true }),
);
receipt.castShadow = true;

// Brilho dos LED enquanto lê
const glowTex = canvasTex(256, 256, (g, w, h) => {
  const gr = g.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, w / 2);
  gr.addColorStop(0, 'rgba(210,255,230,1)'); gr.addColorStop(0.35, 'rgba(90,220,150,0.55)'); gr.addColorStop(1, 'rgba(47,191,115,0)');
  g.fillStyle = gr; g.fillRect(0, 0, w, h);
});
const glow = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTex, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false }));
glow.scale.set(0.09, 0.035, 1);
const ledLight = new THREE.PointLight(0xc8ffe0, 0, 0.15, 2);

// ---------- Telemóvel ----------
const PHONE = { w: 0.077, h: 0.162, d: 0.009 };
const phone = new THREE.Group();
const phoneBody = new THREE.Mesh(
  new RoundedBoxGeometry(PHONE.w, PHONE.h, PHONE.d, 6, 0.0095),
  new THREE.MeshStandardMaterial({ color: 0x0c1824, roughness: 0.35, metalness: 0.4 }),
);
// Sem sombra: o telemóvel está na mão, longe da porta.
phone.add(phoneBody);
const screenGeo = new THREE.PlaneGeometry(0.0712, 0.1545);
const screenA = new THREE.Mesh(screenGeo, new THREE.MeshBasicMaterial({ transparent: true, toneMapped: false }));
const screenB = new THREE.Mesh(screenGeo, new THREE.MeshBasicMaterial({ transparent: true, toneMapped: false, opacity: 0 }));
screenA.position.z = PHONE.d / 2 + 0.0002;
screenB.position.z = PHONE.d / 2 + 0.0004;
phone.add(screenA, screenB);
scene.add(phone);

// Ecrãs reais da app (capturas Roborazzi), com cantos arredondados.
const SCREENS = ['1_inicio_vazio', '1_inicio', '2_estatisticas_mes', '3_registos', '4_receitas'];
const screenTex = {};
async function loadScreens() {
  for (const name of SCREENS) {
    const img = await loadImage(`${ROOT}docs/screenshots/${name}.png`);
    screenTex[name] = canvasTex(img.width, img.height, (g, w, h) => {
      roundRect(g, 0, 0, w, h, 64); g.clip(); g.drawImage(img, 0, 0, w, h);
    });
  }
}
// [tempo de início, ecrã]
const screenTimeline = [[0, '1_inicio_vazio'], [16.7, '1_inicio'], [19.7, '2_estatisticas_mes'], [23.1, '3_registos'], [26.3, '4_receitas']];

// ---------- Cartões de produtos que voam do robô para o telemóvel ----------
const cardItems = [['Leite meio gordo', '0,89 €'], ['Iogurte natural ×2', '2,98 €'], ['Bananas 0,85 kg', '1,01 €'],
  ['Pão de forma', '1,49 €'], ['Ovos classe M', '2,29 €'], ['Atum em lata', '3,87 €']];
const cards = [];
function makeCards() {
  cardItems.forEach(([name, price]) => {
    const tex = canvasTex(720, 132, (g, w, h) => {
      roundRect(g, 4, 4, w - 8, h - 8, 34); g.fillStyle = '#ffffff'; g.fill();
      g.lineWidth = 4; g.strokeStyle = '#cfe0d8'; g.stroke();
      g.fillStyle = '#1E9E5A'; g.beginPath(); g.arc(58, h / 2, 16, 0, Math.PI * 2); g.fill();
      g.fillStyle = '#12304A'; g.font = '600 46px Inter'; g.textBaseline = 'middle'; g.fillText(name, 96, h / 2 + 2);
      g.fillStyle = '#1E9E5A'; g.font = '800 46px Inter'; g.textAlign = 'right'; g.fillText(price, w - 40, h / 2 + 2);
    });
    const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthTest: false }));
    s.renderOrder = 10;
    s.visible = false;
    scene.add(s);
    cards.push(s);
  });
}

// ---------- Robô (modelo 3D do projeto) ----------
let robot, faceMat;
const faceTex = {};
async function loadRobot() {
  const gltf = await new GLTFLoader().loadAsync(`${ROOT}docs/projeto/Modelo%203D/ReceiptAI_montado.glb`);
  robot = gltf.scene;
  robot.traverse((o) => {
    if (!o.isMesh) return;
    o.castShadow = true; o.receiveShadow = true;
    if (o.name === 'recibo') o.visible = false; // usamos o nosso recibo com texto
    if (o.material && o.material.name === 'ecra') faceMat = o.material;
  });
  robot.add(receipt, glow, ledLight);
  receipt.position.set(0, 0.067, 0.023);
  glow.position.set(0, 0.07, 0.03);
  ledLight.position.set(0, 0.05, 0.035);
  scene.add(robot);

  // As 3 expressões do ecrã-rosto (recortes de 960×640 da imagem do projeto).
  const img = await loadImage(`${ROOT}docs/projeto/Imagens/ReceiptAI_ecra_expressoes.png`);
  [['espera', 20], ['a_ler', 1020], ['lido', 2020]].forEach(([k, x]) => {
    faceTex[k] = canvasTex(960, 640, (g) => { g.fillStyle = '#e8f7ef'; g.fillRect(0, 0, 960, 640); g.drawImage(img, x, 60, 960, 640, 0, 0, 960, 640); }, false);
  });
}

// ---------- Câmara: posições-chave ----------
const camKeys = [
  { t: 0.0, pos: v3(0.62, 0.24, 1.7), tgt: v3(0.06, -0.06, 0) },
  { t: 4.8, pos: v3(-0.03, 0.06, 0.46), tgt: v3(-0.06, 0.03, 0.02) },
  { t: 8.6, pos: v3(-0.17, 0.16, 0.34), tgt: v3(-0.045, 0.03, 0.02) },
  { t: 11.9, pos: v3(0.05, 0.05, 0.39), tgt: v3(-0.055, 0.0, 0.02) },
  { t: 14.0, pos: v3(0.15, 0.045, 0.74), tgt: v3(0.16, -0.005, 0.05) },
  { t: 17.3, pos: v3(0.16, 0.04, 0.68), tgt: v3(0.17, -0.005, 0.05) },
  { t: 19.2, pos: v3(0.245, -0.005, 0.45), tgt: v3(0.245, -0.01, 0.10) },
  { t: 30.0, pos: v3(0.25, -0.005, 0.41), tgt: v3(0.25, -0.01, 0.10) },
];
function cameraAt(t) {
  let i = camKeys.length - 2;
  for (let k = 0; k < camKeys.length - 1; k++) if (t < camKeys[k + 1].t) { i = k; break; }
  const a = camKeys[i], b = camKeys[i + 1];
  const raw = prog(t, a.t, b.t);
  const k = i === camKeys.length - 2 ? raw : easeInOut(raw); // último troço: empurrão lento e constante
  camera.position.lerpVectors(a.pos, b.pos, k);
  camera.lookAt(new THREE.Vector3().lerpVectors(a.tgt, b.tgt, k));
}

// ---------- Legendas ----------
const captions = [
  { a: 5.0, b: 9.2, n: '1', title: 'Insere o recibo no robô', sub: 'Fica preso ao frigorífico com ímanes' },
  { a: 9.4, b: 13.0, n: '2', title: 'A IA lê cada produto', sub: 'Nome, quantidade, preço e data' },
  { a: 13.4, b: 19.0, n: '3', title: 'Tudo vai para a app', sub: 'A tua base de dados pessoal no telemóvel' },
  { a: 19.5, b: 22.9, n: '', title: 'Quanto gastaste', sub: 'Por semana, mês ou ano · o que compras mais' },
  { a: 23.1, b: 26.1, n: '', title: 'Todas as compras', sub: 'Toca numa compra para ver os produtos' },
  { a: 26.3, b: 28.3, n: '', title: 'Receitas com o que tens', sub: 'E avisos antes de a comida estragar' },
];
const capEl = document.getElementById('caption');
const capNum = capEl.querySelector('.num'), capTitle = capEl.querySelector('.title'), capSub = capEl.querySelector('.sub');
let lastCap = null;
function captionAt(t) {
  const c = captions.find((x) => t >= x.a && t < x.b);
  if (!c) { capEl.style.opacity = 0; return; }
  if (c !== lastCap) {
    capNum.textContent = c.n; capNum.classList.toggle('empty', !c.n);
    capTitle.textContent = c.title; capSub.textContent = c.sub; lastCap = c;
  }
  const inK = easeOut(prog(t, c.a, c.a + 0.45));
  const outK = prog(t, c.b - 0.3, c.b);
  capEl.style.opacity = (inK * (1 - outK)).toFixed(3);
  capEl.style.transform = `translateY(${(1 - inK) * 24}px) scale(${0.97 + 0.03 * inK})`;
}

// ---------- Abertura / fecho ----------
const intro = document.getElementById('intro'), outro = document.getElementById('outro');
const mascot = document.getElementById('mascot');
document.querySelectorAll('.m').forEach((m) => m.appendChild(mascot.content.cloneNode(true)));
function cardsAt(t) {
  // Abertura: logótipo 0–1,9 s, depois desaparece e revela a cena.
  const inK = easeOut(prog(t, 0.15, 0.9));
  const outK = easeOut(prog(t, 1.9, 2.7));
  intro.style.opacity = (Math.min(1, prog(t, 0, 0.01) + 1) * (1 - outK)).toFixed(3);
  intro.querySelector('.brand').style.transform = `translateY(${(1 - inK) * 30}px) scale(${0.95 + 0.05 * inK})`;
  intro.querySelector('.brand').style.opacity = inK;
  intro.querySelector('.tag').style.opacity = easeOut(prog(t, 0.45, 1.2));
  // Fecho: 28,4 s em diante.
  const o = easeOut(prog(t, 28.4, 29.0));
  outro.style.opacity = o.toFixed(3);
  outro.querySelector('.brand').style.transform = `translateY(${(1 - o) * 30}px) scale(${0.95 + 0.05 * o})`;
  outro.querySelector('.slogan').style.opacity = easeOut(prog(t, 28.7, 29.3));
}

// ---------- Frame ----------
const tmp = new THREE.Vector3();
function renderFrame(t) {
  cameraAt(t);

  // Recibo: aparece por cima, entra devagar e depois é puxado pelos rolos a velocidade constante.
  const appear = easeOut(prog(t, 5.0, 6.3));
  const pull = prog(t, 7.4, 11.1); // linear: o motor puxa sempre à mesma velocidade
  receipt.position.y = 0.067 + lerp(0.08, 0.02, appear) - 0.092 * pull;
  receipt.material.opacity = appear;
  receipt.visible = t > 5.0 && t < 11.2;

  // Rosto: espera -> a ler -> lido
  const face = t < 7.4 ? 'espera' : t < 11.2 ? 'a_ler' : 'lido';
  if (faceMat && faceMat.map !== faceTex[face]) { faceMat.map = faceTex[face]; faceMat.emissiveMap = faceTex[face]; faceMat.needsUpdate = true; }

  // LED a brilhar durante a leitura
  const reading = prog(t, 7.2, 7.6) * (1 - prog(t, 11.0, 11.5));
  const pulse = 0.75 + 0.25 * Math.sin(t * 9);
  glow.material.opacity = reading * pulse * 0.55;
  ledLight.intensity = reading * pulse * 0.002; // luz física: a 3 cm, pouco já é muito

  // "Recibo lido!": pequeno salto de alegria (momento raro -> pode ter "delight")
  const pop = prog(t, 11.2, 11.8);
  const s = 1 + 0.04 * Math.sin(Math.PI * pop) * (1 - pop * 0.5);
  if (robot) robot.scale.setScalar(s);

  // Telemóvel: sobe a partir de baixo (12,6 s) e depois vira-se para a câmara (17,4–19 s).
  const enter = easeOut(prog(t, 12.3, 13.4));
  const turn = easeInOut(prog(t, 17.4, 19.1));
  phone.position.set(0.30, lerp(-0.32, -0.01, enter) + 0.0018 * Math.sin(t * 1.6), 0.10);
  phone.rotation.set(lerp(0.12, 0.0, turn), lerp(-0.55, -0.06, turn), lerp(0.06, 0, turn));
  phone.visible = t > 12.2;

  // Ecrã do telemóvel: troca entre capturas com um fade curto (ease-out 350 ms).
  let i = 0;
  for (let k = 0; k < screenTimeline.length; k++) if (t >= screenTimeline[k][0]) i = k;
  const cur = screenTimeline[i], prev = screenTimeline[Math.max(0, i - 1)];
  const mix = i === 0 ? 1 : easeOut(prog(t, cur[0], cur[0] + 0.35));
  screenA.material.map = screenTex[prev[1]]; screenB.material.map = screenTex[cur[1]];
  screenB.material.opacity = mix;

  // Cartões a voar (14–17 s), com 300 ms entre cada um.
  const from = robot ? robot.localToWorld(tmp.set(0, -0.002, 0.05)).clone() : v3(0, 0, 0.05);
  const to = phone.localToWorld(new THREE.Vector3(0, 0.02, 0.006));
  cards.forEach((c, k) => {
    const a = 14.0 + k * 0.3, d = 1.5;
    const p = prog(t, a, a + d);
    c.visible = t > a && p < 1;
    if (!c.visible) return;
    const e = easeInOut(p);
    const ctrl = v3((from.x + to.x) / 2, 0.11 + k * 0.006, 0.17);
    const q = 1 - e;
    c.position.set(
      q * q * from.x + 2 * q * e * ctrl.x + e * e * to.x,
      q * q * from.y + 2 * q * e * ctrl.y + e * e * to.y,
      q * q * from.z + 2 * q * e * ctrl.z + e * e * to.z,
    );
    const grow = easeOut(prog(p, 0, 0.18));
    const shrink = prog(p, 0.78, 1);
    const sc = grow * (1 - 0.55 * shrink);
    c.scale.set(0.064 * sc, 0.0117 * sc, 1);
    c.material.opacity = 1 - shrink;
  });

  captionAt(t);
  cardsAt(t);
  renderer.render(scene, camera);
}

window.renderFrame = renderFrame;
window.sceneReady = (async () => {
  await document.fonts.ready;
  await Promise.all([document.fonts.load('600 46px Inter'), document.fonts.load('800 46px Inter'), document.fonts.load('500 21px "JetBrains Mono"')]);
  makeCards();
  await Promise.all([loadRobot(), loadScreens()]);
  renderFrame(0);
  return true;
})();
