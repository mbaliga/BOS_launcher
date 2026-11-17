// node --test ubuntu-touch/picker/tests/   (no dependencies)
"use strict";
const test = require("node:test"), assert = require("node:assert/strict");
const path = require("path");
const M = require("../qml/SphereMath.js");
const V = require(path.resolve(__dirname, "../../../fixtures/sphere-vectors.json"));
const close = (a, b, tol, what) => assert.ok(Math.abs(a - b) <= tol, `${what}: ${a} vs ${b}`);

test("fixture parameters match the module", () => {
  assert.equal(V.meta.range, M.RANGE);
  assert.equal(V.meta.STEP_DEG, M.STEP_DEG);
  assert.deepEqual(V.meta.P, M.P);
});

test("lens magnification and spacing match the prototype (raw, 1e-9)", () => {
  assert.ok(V.lens.length > 0);
  for (const v of V.lens) {
    close(M.magAtF(v.o, v.foc), v.mag, 1e-9, `mag o=${v.o} foc=${v.foc}`);
    close(M.txAtF(v.o, v.foc), v.tx, 1e-9, `tx o=${v.o} foc=${v.foc}`);
  }
});

test("card layout matches prototype render() (to its toFixed rounding)", () => {
  let visible = 0;
  for (const v of V.cards) {
    const L = M.cardLayout(v.i, v.pos, v.rows);
    assert.equal(L === null, v.hidden, `hidden i=${v.i} pos=${v.pos} rows=${v.rows}`);
    if (v.hidden) continue; visible++;
    const t = `i=${v.i} pos=${v.pos} rows=${v.rows}`;
    close(L.tx, v.tx, 0.006, "tx " + t); close(L.ty, v.ty, 0.006, "ty " + t); close(L.tz, v.tz, 0.006, "tz " + t);
    close(L.rotY, v.rotY, 0.006, "rotY " + t); close(L.rotX, v.rotX, 0.006, "rotX " + t);
    close(L.scale, v.scale, 0.0006, "scale " + t);
    assert.equal(L.z, v.zIndex, "zIndex " + t);
  }
  assert.ok(visible > 50);
});

test("globe items and projection match prototype renderGlobe()", () => {
  const items = M.globeItems(V.meta.globeCount);
  for (const v of V.globe) {
    const g = items[v.i];
    close(g.x, v.x, 1e-12, "gx"); close(g.y, v.y, 1e-12, "gy"); close(g.z, v.z, 1e-12, "gz");
    const p = M.globeProject(g, v.yaw, v.pitch, v.W, v.H, v.zoom);
    close(p.sx, v.sx, 1e-9, "sx"); close(p.sy, v.sy, 1e-9, "sy"); close(p.pz, v.pz, 1e-9, "pz"); close(p.sc, v.sc, 1e-9, "sc");
    assert.equal(!p.visible, v.hidden, `hidden i=${v.i}`);
    if (!v.hidden) close(p.op, v.opacity, 0.0006, "opacity");
  }
});

// The 4x4 handed to QML's Matrix4x4 must equal a direct CSS-semantics projection of the same
// card corners: p = persp/(persp - z) applied to scale -> rotX -> rotY -> translate about centre.
test("cardMatrix equals direct perspective projection", () => {
  const W = 400, H = 300, cx = 200, cy = 150, d = M.P.persp, DEG = Math.PI / 180;
  for (const pos of [0, 1.3, 4.5]) for (const rows of [1, 2]) for (let i = 0; i < 12; i++) {
    const L = M.cardLayout(i, pos, rows); if (!L) continue;
    const m = M.cardMatrix(L, cx, cy, W, H, d);
    for (const [px, py] of [[cx - 40, cy - 40], [cx + 40, cy - 40], [cx + 40, cy + 40], [cx, cy]]) {
      let x = (px - cx) * L.scale, y = (py - cy) * L.scale, z = 0;
      const rx = L.rotX * DEG; let y1 = y * Math.cos(rx) - z * Math.sin(rx), z1 = y * Math.sin(rx) + z * Math.cos(rx); y = y1; z = z1;
      const ry = L.rotY * DEG; const x2 = x * Math.cos(ry) + z * Math.sin(ry), z2 = -x * Math.sin(ry) + z * Math.cos(ry); x = x2; z = z2;
      x += L.tx; y += L.ty; z += L.tz;
      const k = d / (d - z), ex = W / 2 + (cx - W / 2 + x) * k, ey = H / 2 + (cy - H / 2 + y) * k;
      const [gx, gy] = M.project(m, px, py);
      close(gx, ex, 1e-6, `mx i=${i}`); close(gy, ey, 1e-6, `my i=${i}`);
    }
  }
});

test("pinned landmarks (guards against regenerating vectors from broken code)", () => {
  assert.equal(M.magAtF(0, 1.85), 1.85);
  assert.equal(M.txAtF(0, 1.85), 0);
  assert.ok(Math.abs(M.txAtF(1, 1.85) + M.txAtF(-1, 1.85)) < 1e-12);
  close(M.smooth01(0.5), 0.5, 1e-15, "smooth01");
  const c = M.cardLayout(7, 7, 1); assert.equal(c.scale, 1.85); assert.equal(c.rotY, -0); assert.ok(c.focus);
});
