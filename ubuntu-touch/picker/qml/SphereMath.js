// Liftoff (BOS Launcher) - Ubuntu Touch reframe: lens / globe math.
//
// A line-for-line re-expression of the prototype's `magAtF`, `txAtF`, `render` and
// `renderGlobe` (docs/spherelauncher13.html), which feature-sphere/SphereGeometry.kt also mirrors.
// Plain JS, no Qt imports, so QML (`import "SphereMath.js" as SphereMath`) and node load the same
// file. Held to fixtures/sphere-vectors.json by ubuntu-touch/picker/tests/spheremath.test.js.
// SPDX-License-Identifier: see repository LICENSE

var P = { persp: 600, radius: 550, maxRot: 51, depth: 1.60, minScale: 1.00, arc: 0, tilt: 12,
          k: 0.18, focus: 1.85, size: 0.92, pull: 330, latch: 0.40, bead: 50, stretch: 82, tension: 18 };
var STEP_DEG = 12;
var DEG = Math.PI / 180;
var STEP_R = STEP_DEG * Math.PI / 180;
var RANGE = 6;            // prototype `range=6`; Kotlin: floor(82/12) coerced to 2..7
var GOLDEN = Math.PI * (3 - Math.sqrt(5));

function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
function smooth01(t) { return t * t * (3 - 2 * t); }

function magAtF(o, foc) {
    var fall = Math.max(0, 1 - Math.abs(o) / (RANGE * 0.9));
    return 1 + (foc - 1) * smooth01(fall);
}

function txAtF(o, foc) {
    var sgn = Math.sign(o), A = Math.abs(o), N = 8, sum = 0;
    for (var k = 0; k < N; k++) {
        var t = A * (k + 0.5) / N;
        sum += Math.cos(Math.min(t * STEP_R, 1.466)) * magAtF(t, foc);
    }
    return sgn * P.radius * STEP_R * (A / N) * sum;
}

// rows: 1 or 2. Returns null when the card is off-surface (prototype: display:none).
function cardLayout(i, pos, rows) {
    var U = rows === 2 ? 0.66 : 1;
    var foc = rows === 2 ? (1 + (P.focus - 1) * 0.45) : P.focus;
    var row = rows === 2 ? (i & 1) : 0, col = rows === 2 ? (i >> 1) : i;
    var o = col - pos, ao = Math.abs(o), s = Math.sign(o);
    if (ao > RANGE + 0.8) return null;
    var phi = clamp(o * STEP_DEG, -84, 84) * DEG, m = magAtF(o, foc);
    var sc = (1 + (P.minScale - 1) * (Math.min(ao, RANGE) / RANGE)) * m * U;
    var tx = txAtF(o, foc) * U, tz = -P.radius * (1 - Math.cos(phi)) * P.depth;
    var ty = P.arc * (1 - Math.cos(phi));
    if (rows === 2) ty += (row ? 1 : -1) * (66 * sc + 5);
    var rotY = -s * P.maxRot * smooth01(Math.min(ao, 1));
    var rotX = P.arc > 0 ? P.tilt * (1 - Math.cos(phi)) : 0;
    return { tx: tx, ty: ty, tz: tz, rotY: rotY, rotX: rotX, scale: sc,
             z: Math.round(2000 + tz), focus: ao < 0.5, row: row, col: col, offset: o };
}

// Full-plane 4x4 (row-major, QML Matrix4x4 order) for CSS
//   perspective(persp) at the stage centre; translate3d; rotateY; rotateX; scale
// applied about the card centre (cx, cy) in stage coordinates. Applying it to a homogeneous
// point and dividing by w reproduces CSS `perspective` projection, w = 1 - z/persp.
function mul(a, b) {
    var r = [];
    for (var i = 0; i < 4; i++) for (var j = 0; j < 4; j++) {
        var s = 0;
        for (var k = 0; k < 4; k++) s += a[i * 4 + k] * b[k * 4 + j];
        r.push(s);
    }
    return r;
}
function cardMatrix(L, cx, cy, stageW, stageH, persp) {
    var d = persp || P.persp, ry = L.rotY * DEG, rx = L.rotX * DEG;
    var T = function (x, y, z) { return [1,0,0,x, 0,1,0,y, 0,0,1,z, 0,0,0,1]; };
    var S = [L.scale,0,0,0, 0,L.scale,0,0, 0,0,L.scale,0, 0,0,0,1];
    var RY = [Math.cos(ry),0,Math.sin(ry),0, 0,1,0,0, -Math.sin(ry),0,Math.cos(ry),0, 0,0,0,1];
    var RX = [1,0,0,0, 0,Math.cos(rx),-Math.sin(rx),0, 0,Math.sin(rx),Math.cos(rx),0, 0,0,0,1];
    var PR = [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,-1 / d,1];
    var po = [stageW / 2, stageH / 2];                  // perspective-origin
    // element space (origin at card centre) -> stage
    var m = mul(T(cx - po[0], cy - po[1], 0), T(L.tx, L.ty, L.tz));
    m = mul(m, RY); m = mul(m, RX); m = mul(m, S);
    m = mul(PR, m);                                       // perspective about the stage centre
    m = mul(T(po[0], po[1], 0), m);
    return mul(m, T(-cx, -cy, 0));                        // item-local -> about its centre
}
// Same transform for a QML Item whose top-left sits at (x, y) in the stage: QML applies
// Item.transform in item-local coordinates and then adds (x, y), so wrap the stage-space matrix.
function cardMatrixLocal(L, x, y, w, h, stageW, stageH, persp) {
    var m = cardMatrix(L, x + w / 2, y + h / 2, stageW, stageH, persp);
    var T = function (a, b) { return [1,0,0,a, 0,1,0,b, 0,0,1,0, 0,0,0,1]; };
    return mul(T(-x, -y), mul(m, T(x, y)));
}
function project(m, x, y) {
    var X = m[0]*x + m[1]*y + m[3], Y = m[4]*x + m[5]*y + m[7], W = m[12]*x + m[13]*y + m[15];
    return [X / W, Y / W];
}

function globeItems(N) {
    var out = [];
    for (var i = 0; i < N; i++) {
        var y = 1 - (i / (N - 1)) * 2, r = Math.sqrt(Math.max(0, 1 - y * y)), th = i * GOLDEN;
        out.push({ x: Math.cos(th) * r, y: y, z: Math.sin(th) * r });
    }
    return out;
}

// One globe item -> screen. zoom in [0,1]. Returns {sx, sy, pz, sc, op, visible}.
function globeProject(g, yaw, pitch, W, H, zoom) {
    var az = smooth01(zoom);
    var cyy = Math.cos(yaw), syy = Math.sin(yaw), cpp = Math.cos(pitch), spp = Math.sin(pitch);
    var cx = W / 2, cyc = H * 0.46, spread = Math.min(W * 0.32, H * 0.42) * (0.7 + 0.3 * az);
    var x1 = g.x * cyy + g.z * syy, z1 = -g.x * syy + g.z * cyy, y1 = g.y;
    var y2 = y1 * cpp - z1 * spp, z2 = y1 * spp + z1 * cpp, pz = z2;
    var sx = cx + x1 * spread, sy = cyc - y2 * spread;
    var ang = Math.acos(clamp(pz, -1, 1));
    var lensFall = smooth01(Math.max(0, 1 - ang / 0.95));
    var sc = (0.42 + 0.58 * ((pz + 1) / 2)) * (1 + (P.focus - 1) * lensFall);
    var op = clamp((pz + 0.25) / 0.55, 0, 1);
    return { sx: sx, sy: sy, pz: pz, sc: sc, op: op, visible: op >= 0.02 };
}

if (typeof module !== "undefined" && module.exports) {
    module.exports = { P: P, STEP_DEG: STEP_DEG, RANGE: RANGE, clamp: clamp, smooth01: smooth01,
        magAtF: magAtF, txAtF: txAtF, cardLayout: cardLayout, cardMatrix: cardMatrix, cardMatrixLocal: cardMatrixLocal, project: project,
        globeItems: globeItems, globeProject: globeProject };
}
