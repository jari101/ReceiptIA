// Cena 3D do vídeo ReceiptAI (30 s). Tudo é calculado a partir do tempo t (em segundos),
// por isso cada frame é sempre igual: o render.cjs chama window.renderFrame(t) 900 vezes.
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import {
  canvasTex, roundRect, rng, azulejoTextures, cabinetTexture, stoneTexture, postItTexture, calendarTexture,
  receiptTextures,
} from './textures.js';

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

// Reflexos realistas (porta lacada, puxador de metal, azulejos vidrados): ambiente "sala" do three.js.
const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
scene.environmentIntensity = 0.5;

scene.add(new THREE.HemisphereLight(0xffffff, 0xb9c8d0, 0.95));
const key = new THREE.DirectionalLight(0xfff6ec, 2.3);
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

// ---------- Cozinha: parede de azulejos, frigorífico, bancada e armários ----------
const FLOOR = -1.42;       // o robô (y = 0) fica a ~1,45 m do chão
const WALL_Z = -0.72;      // parede atrás do frigorífico
function box(w, h, d, mat, x, y, z, r = 0.004) {
  const m = new THREE.Mesh(new RoundedBoxGeometry(w, h, d, 3, Math.min(r, w / 2, h / 2, d / 2)), mat);
  m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true; scene.add(m);
  return m;
}
function buildKitchen() {
  // Parede com azulejo português (azulejos de 15 cm).
  const az = azulejoTextures();
  const wallW = 6, wallH = 2.8;
  az.map.repeat.set(wallW / 0.6, wallH / 0.6); az.bump.repeat.copy(az.map.repeat);
  const wall = new THREE.Mesh(
    new THREE.PlaneGeometry(wallW, wallH),
    new THREE.MeshPhysicalMaterial({ map: az.map, bumpMap: az.bump, bumpScale: 3, roughness: 0.32, clearcoat: 0.6, clearcoatRoughness: 0.18 }),
  );
  wall.position.set(0.6, FLOOR + wallH / 2, WALL_Z);
  wall.receiveShadow = true;
  scene.add(wall);

  // Frigorífico branco lacado: corpo, porta, junta de borracha, gaveta do congelador.
  // Rugosidade com dedadas perto do puxador: o verniz brilha menos onde se toca.
  const smudge = (base, mark) => canvasTex(512, 512, (g, w, h) => {
    g.fillStyle = `rgb(${base},${base},${base})`; g.fillRect(0, 0, w, h);
    const r = rng(77);
    for (let i = 0; i < 26; i++) {
      const x = w * (0.02 + r() * 0.22), y = h * (0.3 + r() * 0.4), rad = 8 + r() * 22;
      const gr = g.createRadialGradient(x, y, 0, x, y, rad);
      gr.addColorStop(0, `rgba(${mark},${mark},${mark},0.55)`); gr.addColorStop(1, `rgba(${mark},${mark},${mark},0)`);
      g.fillStyle = gr; g.fillRect(x - rad, y - rad, rad * 2, rad * 2);
    }
    for (let i = 0; i < 6; i++) { // marcas largas e suaves (pano da limpeza)
      const x = w * r(), y = h * r(), rad = 60 + r() * 90;
      const gr = g.createRadialGradient(x, y, 0, x, y, rad);
      gr.addColorStop(0, `rgba(${mark},${mark},${mark},0.12)`); gr.addColorStop(1, `rgba(${mark},${mark},${mark},0)`);
      g.fillStyle = gr; g.fillRect(x - rad, y - rad, rad * 2, rad * 2);
    }
  });
  const roughMap = smudge(70, 150), coatMap = smudge(16, 90);
  roughMap.colorSpace = coatMap.colorSpace = THREE.NoColorSpace;
  // Queda de luz na porta: mais clara em cima (onde bate a luz), um pouco mais escura nos bordos e em baixo.
  const falloff = canvasTex(512, 1024, (g, w, h) => {
    g.fillStyle = '#d9dee2'; g.fillRect(0, 0, w, h);
    const gr = g.createRadialGradient(w * 0.62, h * 0.3, 20, w * 0.55, h * 0.42, h * 0.75);
    gr.addColorStop(0, '#ffffff'); gr.addColorStop(0.45, '#f6f8f9'); gr.addColorStop(1, '#dfe4e8');
    g.fillStyle = gr; g.fillRect(0, 0, w, h);
    const edge = g.createLinearGradient(0, 0, w, 0);
    edge.addColorStop(0, 'rgba(150,160,170,0.18)'); edge.addColorStop(0.08, 'rgba(150,160,170,0)');
    edge.addColorStop(0.92, 'rgba(150,160,170,0)'); edge.addColorStop(1, 'rgba(150,160,170,0.18)');
    g.fillStyle = edge; g.fillRect(0, 0, w, h);
  });
  const lacquer = new THREE.MeshPhysicalMaterial({
    color: 0xf7f8f9, map: falloff, roughness: 1, roughnessMap: roughMap, clearcoat: 1, clearcoatRoughness: 1,
    clearcoatRoughnessMap: coatMap, envMapIntensity: 2.4,
  });
  const bodyMat = new THREE.MeshStandardMaterial({ color: 0xe3e7ea, roughness: 0.45 });
  const rubber = new THREE.MeshStandardMaterial({ color: 0x3a3f44, roughness: 0.8 });
  const metal = new THREE.MeshStandardMaterial({ color: 0xd2d7db, metalness: 1, roughness: 0.3 });
  const top = 0.55;
  box(1.19, top - FLOOR, 0.6, bodyMat, 0.12, (top + FLOOR) / 2, WALL_Z + 0.32, 0.02);
  const door = box(1.15, 1.56, 0.05, lacquer, 0.12, -0.28, -0.031, 0.025);
  door.castShadow = false;
  box(1.162, 1.572, 0.006, rubber, 0.12, -0.28, -0.058, 0.02);            // junta
  box(1.15, 0.31, 0.05, lacquer, 0.12, -1.245, -0.031, 0.02);             // gaveta
  box(1.162, 0.322, 0.006, rubber, 0.12, -1.245, -0.058, 0.02);
  // Puxadores de alumínio com apoios.
  box(0.024, 0.62, 0.022, metal, -0.405, -0.15, 0.036, 0.01);
  for (const y of [-0.43, 0.13]) box(0.016, 0.022, 0.034, metal, -0.405, y, 0.012, 0.004);
  box(0.6, 0.022, 0.022, metal, 0.12, -1.13, 0.036, 0.01);
  for (const x of [-0.15, 0.39]) box(0.022, 0.016, 0.034, metal, x, -1.13, 0.012, 0.004);

  // Bancada à direita do frigorífico: armários em baixo e em cima.
  const right = 0.12 + 0.595 + 0.02, len = 2.1, cx = right + len / 2;
  const stone = new THREE.MeshStandardMaterial({ map: stoneTexture(), roughness: 0.4 });
  box(len, 0.04, 0.64, stone, cx, -0.55, WALL_Z + 0.32, 0.006);
  const lower = new THREE.MeshStandardMaterial({ map: cabinetTexture(4, 1024, 400, 'top'), roughness: 0.5 });
  box(len, 0.80, 0.58, lower, cx, (FLOOR + 0.03 - 0.57) / 2 + 0.0, WALL_Z + 0.29, 0.004);
  const upper = new THREE.MeshStandardMaterial({ map: cabinetTexture(4, 1024, 360, 'bottom'), roughness: 0.5 });
  box(len, 0.72, 0.34, upper, cx, 0.62, WALL_Z + 0.17, 0.004);
  // Vaso com planta e taça com laranjas em cima da bancada.
  const pot = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.045, 0.12, 24), new THREE.MeshStandardMaterial({ color: 0xc46a45, roughness: 0.7 }));
  pot.position.set(right + 0.3, -0.47, WALL_Z + 0.22); pot.castShadow = true; scene.add(pot);
  const leaf = new THREE.MeshStandardMaterial({ color: 0x3f8f4f, roughness: 0.6 });
  [[0, 0.1, 0, 0.05], [0.04, 0.14, 0.02, 0.04], [-0.04, 0.13, -0.01, 0.045], [0.01, 0.18, -0.02, 0.035]].forEach(([x, y, z, r]) => {
    const l = new THREE.Mesh(new THREE.SphereGeometry(r, 16, 12), leaf);
    l.scale.set(1, 1.3, 1); l.position.set(pot.position.x + x, pot.position.y + y, pot.position.z + z); scene.add(l);
  });
  const bowl = new THREE.Mesh(new THREE.SphereGeometry(0.11, 32, 12, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2),
    new THREE.MeshPhysicalMaterial({ color: 0xffffff, roughness: 0.25, clearcoat: 1, side: THREE.DoubleSide }));
  bowl.position.set(right + 0.75, -0.43, WALL_Z + 0.3); scene.add(bowl);
  const orange = new THREE.MeshStandardMaterial({ color: 0xf29a2e, roughness: 0.55 });
  [[0, 0], [0.06, 0.02], [-0.05, 0.03], [0.01, -0.06], [0.0, 0.0, 0.05]].forEach(([x, z, y = 0]) => {
    const o = new THREE.Mesh(new THREE.SphereGeometry(0.038, 20, 14), orange);
    o.position.set(bowl.position.x + x, -0.49 + y + 0.03, bowl.position.z + z); o.castShadow = true; scene.add(o);
  });

  // Coisas presas na porta: post-it (lista de compras "de memória"), calendário com íman, ímanes soltos.
  const postGeo = new THREE.PlaneGeometry(0.076, 0.076, 1, 10);
  const pp = postGeo.attributes.position;
  for (let i = 0; i < pp.count; i++) { const v = (0.038 - pp.getY(i)) / 0.076; pp.setZ(i, 0.011 * Math.pow(Math.max(0, v - 0.35) / 0.65, 2)); }
  postGeo.computeVertexNormals();
  const postIt = new THREE.Mesh(postGeo, new THREE.MeshStandardMaterial({ map: postItTexture(), roughness: 0.75, side: THREE.DoubleSide }));
  postIt.position.set(-0.24, 0.12, -0.0045); postIt.rotation.z = 0.06; postIt.castShadow = true; postIt.receiveShadow = true;
  scene.add(postIt);
  const calGeo = new THREE.PlaneGeometry(0.1, 0.137, 1, 12);
  const cp = calGeo.attributes.position;
  for (let i = 0; i < cp.count; i++) { const v = (0.0685 - cp.getY(i)) / 0.137; cp.setZ(i, 0.004 * Math.sin(v * Math.PI) + 0.006 * v * v); }
  calGeo.computeVertexNormals();
  const cal = new THREE.Mesh(calGeo, new THREE.MeshStandardMaterial({ map: calendarTexture(), roughness: 0.8, side: THREE.DoubleSide }));
  cal.position.set(0.165, 0.118, -0.0045); cal.rotation.z = -0.035; cal.castShadow = true; cal.receiveShadow = true;
  scene.add(cal);
  const magnet = (color, x, y) => {
    const m = new THREE.Mesh(new THREE.CylinderGeometry(0.011, 0.011, 0.008, 32),
      new THREE.MeshPhysicalMaterial({ color, roughness: 0.3, clearcoat: 1, clearcoatRoughness: 0.1 }));
    m.rotation.x = Math.PI / 2; m.position.set(x, y, 0.0); m.castShadow = true; scene.add(m);
  };
  magnet(0xd94b4b, 0.167, 0.182);
  magnet(0x1e9e5a, -0.2, 0.03);
  magnet(0x2a5aa8, -0.3, 0.06);
}

// ---------- Texturas auxiliares ----------
function loadImage(src) {
  return new Promise((ok, fail) => { const i = new Image(); i.onload = () => ok(i); i.onerror = fail; i.src = src; });
}

// ---------- Recibo em papel térmico (3D, curvo e dobrado) ----------
// Tem 8 × 20 cm como um talão verdadeiro. Entra pela ranhura de cima, passa pelos rolos
// e sai por baixo do robô (ranhura de saída), por isso a malha é deformada em cada frame.
const RW = 0.08, RL = 0.2, SEG_X = 10, SEG_Y = 90;
const SLOT_TOP = 0.064, SLOT_BOTTOM = -0.064, PAPER_Z = 0.023;
const receipt = new THREE.Group();
let receiptGeo, receiptBase, receiptFront, receiptBack;
function buildReceipt() {
  const tex = receiptTextures();
  receiptGeo = new THREE.PlaneGeometry(RW, RL, SEG_X, SEG_Y);
  receiptBase = receiptGeo.attributes.position.array.slice();
  const common = { roughness: 0.88, bumpMap: tex.bump, bumpScale: 1.2, alphaTest: 0.5, transparent: true };
  receiptFront = new THREE.Mesh(receiptGeo, new THREE.MeshStandardMaterial({ ...common, map: tex.front, side: THREE.FrontSide }));
  receiptBack = new THREE.Mesh(receiptGeo, new THREE.MeshStandardMaterial({ ...common, map: tex.back, side: THREE.BackSide }));
  receiptFront.castShadow = true; receiptBack.castShadow = true;
  receipt.add(receiptFront, receiptBack);
}
/** Deforma o papel: dentro do robô fica plano; fora enrola um pouco e mostra o vinco da dobra. */
function shapeReceipt(centerY, fall) {
  const pos = receiptGeo.attributes.position;
  for (let i = 0; i < pos.count; i++) {
    const x = receiptBase[i * 3], ly = receiptBase[i * 3 + 1];
    const Y = centerY + ly;                    // altura no referencial do robô
    const v = (RL / 2 - ly) / RL;             // 0 = topo do papel, 1 = fundo
    let z = 0;
    let out = 0;                               // distância fora do robô
    if (Y > SLOT_TOP) { out = Y - SLOT_TOP; z = 0.75 * out * out; }
    else if (Y < SLOT_BOTTOM) { out = SLOT_BOTTOM - Y; z = 0.35 * out * out; }
    const free = Math.min(1, out / 0.02);     // perto da ranhura o papel ainda está preso
    z += free * (2.2 * x * x);                                     // papel ligeiramente arqueado
    z += free * 0.0035 * Math.max(0, 1 - Math.abs(v - 0.42) / 0.035); // vinco da dobra
    z += free * 0.0012 * Math.sin(v * Math.PI * 3.2 + 0.6);           // ondulação do papel
    pos.setXYZ(i, x + fall.x * free, ly, z);
  }
  pos.needsUpdate = true;
  receiptGeo.computeVertexNormals();
}

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
  receipt.position.set(0, 0.3, PAPER_Z);
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
  { t: 0.0, pos: v3(1.35, 0.12, 3.1), tgt: v3(0.42, -0.38, -0.3) },
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
  const outK = easeOut(prog(t, 1.5, 2.2));
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

  // Recibo: desce pela mão até à ranhura (5–6,3 s), os rolos puxam-no a velocidade constante
  // (7,4–11,1 s) e ele sai por baixo e cai (11,1–11,9 s).
  const appear = easeOut(prog(t, 5.0, 6.3));
  const pull = prog(t, 7.4, 11.1);                       // linear: o motor puxa sempre à mesma velocidade
  const insertedBottom = SLOT_TOP - 0.02;                // 2 cm já dentro da ranhura
  let bottomEdge = lerp(SLOT_TOP + 0.09, insertedBottom, appear);
  const travel = (insertedBottom + RL) - (SLOT_BOTTOM - 0.006); // até o topo sair por baixo
  bottomEdge -= travel * pull;
  const ft = Math.max(0, t - 11.1);
  bottomEdge -= 0.5 * 2.2 * ft * ft;                     // queda (gravidade, mais lenta para se ver)
  receipt.position.y = bottomEdge + RL / 2;
  receipt.rotation.z = 0.35 * ft * ft;
  const fade = 1 - prog(t, 11.45, 11.9);
  receiptFront.material.opacity = receiptBack.material.opacity = Math.min(prog(t, 5.0, 5.3), fade);
  receipt.visible = t > 5.0 && t < 11.9;
  if (receipt.visible) shapeReceipt(receipt.position.y, { x: 0 });

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
  await Promise.all([document.fonts.load('600 46px Inter'), document.fonts.load('800 46px Inter'),
    document.fonts.load('700 54px Inter'), document.fonts.load('400 25px "DejaVu Sans Mono"'),
    document.fonts.load('italic 50px "Bitstream Charter"')]);
  buildKitchen();
  buildReceipt();
  makeCards();
  await Promise.all([loadRobot(), loadScreens()]);
  renderFrame(0);
  return true;
})();
