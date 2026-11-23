/*
 * Liftoff Studio option cards: the small drawings on the choice cards (the app draws a card for every pick of two to nine options; so does Studio).
 * card(key, optionId, values) returns the inner SVG of a 64 by 44 card, or null when the field has no drawing (the page then shows a text card).
 * Colours come from CSS classes (f-ink, f-sub, f-acc, s-ink ...) so the drawings follow light and dark.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory(require('./preview.js'));
  else root.StudioCards = factory(root.StudioPreview);
})(typeof self !== 'undefined' ? self : this, function (P) {
  'use strict';
  var h = P.helpers, f1 = h.f1;
  function R(x, y, w, hh, rx, cls, extra) { return '<rect x="' + x + '" y="' + y + '" width="' + w + '" height="' + hh + '" rx="' + (rx || 0) + '" class="' + cls + '"' + (extra ? ' ' + extra : '') + '/>'; }
  function C(cx, cy, r, cls, extra) { return '<circle cx="' + cx + '" cy="' + cy + '" r="' + r + '" class="' + cls + '"' + (extra ? ' ' + extra : '') + '/>'; }
  function L(x1, y1, x2, y2, cls, w) { return '<line x1="' + x1 + '" y1="' + y1 + '" x2="' + x2 + '" y2="' + y2 + '" class="' + cls + '" stroke-width="' + (w || 1.5) + '" stroke-linecap="round"/>'; }
  function T(x, y, s, size, cls, extra) { return '<text x="' + x + '" y="' + y + '" font-size="' + size + '" class="' + cls + '" text-anchor="middle"' + (extra ? ' ' + extra : '') + '>' + s + '</text>'; }
  function P_(d, cls, w) { return '<path d="' + d + '" class="' + cls + '" stroke-width="' + (w || 1.5) + '" stroke-linecap="round" stroke-linejoin="round"/>'; }
  var NOFILL = 'nofill';

  function arrow(x1, y1, x2, y2) {
    var a = Math.atan2(y2 - y1, x2 - x1), s = 4;
    return L(x1, y1, x2, y2, 's-ink', 1.8) + P_('M' + f1(x2 - Math.cos(a - 0.6) * s) + ' ' + f1(y2 - Math.sin(a - 0.6) * s) + ' L' + x2 + ' ' + y2 + ' L' + f1(x2 - Math.cos(a + 0.6) * s) + ' ' + f1(y2 - Math.sin(a + 0.6) * s), 's-ink ' + NOFILL, 1.8);
  }
  function bars(x, y, n, on) { var s = ''; for (var i = 0; i < 4; i++) s += R(x + i * 5, y + 12 - (i + 1) * 3, 3.4, (i + 1) * 3, 0.8, i < on ? 'f-ink' : 'f-dim'); return s; }
  function miniPhone(extra) { return R(20, 2, 24, 40, 4, 's-sub ' + NOFILL, 'stroke-width="1.4"') + (extra || ''); }
  function clockFace(cx, cy, r, ticks) { var s = C(cx, cy, r, 's-ink ' + NOFILL, 'stroke-width="1.6"'); if (ticks) for (var i = 0; i < 12; i += 3) { var a = i * Math.PI / 6; s += L(f1(cx + Math.sin(a) * r * 0.75), f1(cy - Math.cos(a) * r * 0.75), f1(cx + Math.sin(a) * r * 0.92), f1(cy - Math.cos(a) * r * 0.92), 's-ink', 1.6); } return s + L(cx, cy, cx + r * 0.45, cy - r * 0.22, 's-ink', 2) + L(cx, cy, cx + r * 0.1, cy - r * 0.7, 's-ink', 1.6) + C(cx, cy, 1.6, 'f-acc'); }
  function fillShape(shape, x, y, s, i, cls) { return h.closeShape(h.shapePath(shape, x, y, s, i), 'currentColor', 'class="' + cls + '"').replace(' fill="currentColor"', ''); }

  var DRAW = {
    themeMode: function (o) { var bg = { Dark: '#0B0B10', Amoled: '#000', Light: '#F4F4F8' }[o], ink = o === 'Light' ? '#14141A' : '#F2F2F7'; return R(8, 4, 48, 36, 6, 'x', 'fill="' + bg + '" stroke="rgba(128,128,128,.5)"') + R(14, 10, 22, 4, 2, 'x', 'fill="' + ink + '" opacity=".85"') + R(14, 18, 34, 3, 1.5, 'x', 'fill="' + ink + '" opacity=".4"') + R(14, 24, 28, 3, 1.5, 'x', 'fill="' + ink + '" opacity=".4"') + C(46, 32, 4, 'f-acc'); },
    iconShape: function (o) { if (o === 'Original') return fillShape('Circle', 8, 8, 16, 0, 'f-acc') + fillShape('Squircle', 28, 8, 16, 1, 'f-ink') + fillShape('Rounded', 8, 26, 14, 2, 'f-ink') + fillShape('Square', 28, 26, 14, 3, 'f-acc'); return fillShape(o, 17, 6, 30, 0, 'f-acc'); },
    iconTint: function (o) { var c = o === 'Accent' ? 'f-acc' : o === 'Mono' ? 'f-sub' : ''; return [0, 1, 2].map(function (i) { var col = c || ['f-g', 'f-r', 'f-b'][i]; return fillShape('Rounded', 6 + i * 19, 12, 16, 0, col); }).join(''); },
    clockStyle: function (o) {
      if (o === 'BigDigital') return T(32, 20, '14', 17, 'f-ink', 'font-weight="300"') + T(32, 38, '09', 17, 'f-ink', 'font-weight="300"') + R(24, 41, 16, 1.6, 1, 'f-acc');
      if (o === 'BigDigitalLine') return T(32, 29, '14:09', 18, 'f-ink', 'font-weight="300"');
      if (o === 'AnalogClassic') return clockFace(32, 22, 16, true);
      if (o === 'AnalogMinimal') return clockFace(32, 22, 15, false);
      if (o === 'Word') return T(32, 19, 'five past', 10, 'f-ink', 'font-weight="300"') + T(32, 32, 'two', 11, 'f-ink', 'font-weight="600"');
      return C(32, 22, 14, 's-sub ' + NOFILL, 'stroke-dasharray="3 3"') + L(23, 31, 41, 13, 's-sub', 1.6);
    },
    glanceClockSize: function (o) { var r = { Small: 6, Medium: 10, Large: 15 }[o]; return clockFace(32, 22, r, false); },
    cornerStyle: function (o) {
      var s = R(6, 3, 52, 38, 5, 's-sub ' + NOFILL, 'stroke-width="1.2"');
      if (o === 'Quarter') return s + '<path d="M6 3 H28 A22 22 0 0 1 6 25 Z" class="f-dim"/>' + C(13, 10, 3, 'f-ink');
      if (o === 'Bubble') return s + C(17, 14, 9, 'f-dim') + C(17, 14, 3, 'f-ink');
      return s + C(13, 10, 3.2, 'f-ink');
    },
    cornerIconMode: function (o) { return o === 'AppIcon' ? fillShape('Rounded', 20, 8, 24, 0, 'f-acc') : h.glyph('Camera', 32, 22, 26, 'currentColor', 1.6).replace('<g ', '<g class="s-ink" '); },
    'dock.style': function (o) {
      var ic = function (y) { return [0, 1, 2, 3].map(function (i) { return R(14 + i * 10, y, 7, 7, 2, 'f-acc'); }).join(''); };
      if (o === 'transparent') return [0, 1, 2, 3].map(function (i) { return C(17.5 + i * 10, 33, 6.5, 'f-dim') + R(14 + i * 10, 30, 7, 7, 2, 'f-acc'); }).join('');
      if (o === '3d') return R(8, 26, 48, 13, 5, 'f-sub') + R(10, 27, 44, 2, 1, 'f-ink', 'opacity=".5"') + [0, 1, 2, 3].map(function (i) { return R(14 + i * 10, 24, 7, 7, 2, 'f-acc'); }).join('') + R(12, 40, 40, 2, 1, 'f-dim');
      return R(8, 28, 48, 12, 6, 'f-dim', 'stroke="rgba(128,128,128,.5)"') + ic(30.5);
    },
    'dock.edge': function (o) { var m = miniPhone(); return m + (o === 'Bottom' ? R(23, 34, 18, 4, 2, 'f-acc') : o === 'Left' ? R(21.5, 12, 4, 20, 2, 'f-acc') : R(38.5, 12, 4, 20, 2, 'f-acc')); },
    'dock.mode': function (o) { return o === 'Collapsed' ? R(22, 32, 20, 4, 2, 'f-ink', 'opacity=".7"') : R(10, 26, 44, 12, 6, 'f-dim') + [0, 1, 2].map(function (i) { return R(15 + i * 9, 28.5, 7, 7, 2, 'f-acc'); }).join('') + L(43, 29, 43, 35, 's-sub', 1) + R(46, 29.5, 5, 5, 1.5, 'f-sub'); },
    'dock.enabled': function (o) { return R(10, 24, 44, 12, 6, o === 'off' ? 'f-dim' : 'f-sub', o === 'off' ? 'stroke-dasharray="3 3" stroke="rgba(128,128,128,.6)"' : '') + (o === 'auto' ? T(32, 33, 'A', 9, 'f-ink', 'font-weight="700"') : o === 'on' ? [0, 1, 2].map(function (i) { return R(17 + i * 10, 27, 7, 7, 2, 'f-acc'); }).join('') : L(24, 26, 40, 34, 's-ink', 1.6)); },
    searchLayout: function (o) {
      if (o === 'Filmstrip') return R(4, 12, 14, 20, 3, 'f-dim') + R(46, 12, 14, 20, 3, 'f-dim') + R(19, 8, 26, 28, 4, 'f-sub') + C(32, 19, 4, 'f-acc') + R(24, 28, 16, 2.5, 1, 'f-ink');
      if (o === 'Orbit') return '<ellipse cx="32" cy="22" rx="26" ry="14" class="s-sub ' + NOFILL + '"/><ellipse cx="32" cy="22" rx="15" ry="8" class="s-sub ' + NOFILL + '"/>' + C(32, 22, 4, 'f-acc') + C(8, 22, 3, 'f-ink') + C(54, 20, 3, 'f-ink') + C(40, 10, 2.6, 'f-ink');
      return [0, 1, 2, 3].map(function (i) { return C(10, 9 + i * 9, 3, 'f-acc') + R(17, 7.5 + i * 9, 36 - (i % 2) * 8, 3, 1.5, 'f-ink', 'opacity=".6"'); }).join('');
    },
    calmStyle: function (o) {
      var s = R(14, 4, 36, 36, 4, 's-sub ' + NOFILL, 'stroke-width="1.2"');
      if (o === 'auto') return s + T(32, 29, 'A', 16, 'f-ink', 'font-weight="700"');
      if (o === 'None') return s + L(20, 30, 44, 12, 's-sub', 1.6);
      if (o === 'Regular') return s + bars(22, 14, 3) + R(22, 28, 18, 8, 2, 's-ink ' + NOFILL, 'stroke-width="1.2"') + R(24, 30, 10, 4, 1, 'f-ink');
      if (o === 'EdgeDots') return s + [0, 1, 2, 3, 4].map(function (i) { return C(18, 9 + i * 7, 1.6, 'f-g') + C(46, 9 + i * 7, 1.6, 'f-g'); }).join('');
      if (o === 'CornerBloom') return s + '<circle cx="14" cy="40" r="15" class="f-g" opacity=".35"/><circle cx="50" cy="40" r="15" class="f-g" opacity=".35"/>';
      if (o === 'BottomGlow') return s + R(15, 28, 34, 11, 3, 'f-g', 'opacity=".4"');
      return s + L(47, 8, 47, 36, 's-sub', 2) + L(47, 18, 47, 36, 's-g', 2.4);
    },
    wpGlanceKind: function (o) { return wpCard(o); }, wpPagesKind: function (o) { return wpCard(o); },
    homeOrder: function (o) { return o === 'LeftToRight' ? arrow(12, 22, 52, 22) + R(10, 8, 8, 6, 1.5, 'f-acc') : arrow(52, 22, 12, 22) + R(46, 8, 8, 6, 1.5, 'f-acc'); },
    homePages: function (o) {
      if (o === 'Off') return R(18, 4, 28, 36, 4, 'f-sub') + T(32, 26, 'G', 12, 'f-ink', 'font-weight="700"');
      if (o === 'Paginated') return R(6, 8, 16, 28, 3, 'f-dim') + R(24, 6, 16, 32, 3, 'f-sub') + R(42, 8, 16, 28, 3, 'f-dim') + [0, 1, 2].map(function (i) { return C(26 + i * 6, 41, 1.2, i ? 'f-dim' : 'f-ink'); }).join('');
      return [0, 1, 2, 3].map(function (c) { return [0, 1, 2].map(function (r) { return R(6 + c * 14, 6 + r * 12, 10, 9, 2, 'f-sub', c === 3 ? 'opacity=".4"' : ''); }).join(''); }).join('');
    },
    scrollAxis: function (o) { return o === 'Vertical' || o === 'Long' ? arrow(32, 38, 32, 6) : arrow(10, 22, 54, 22); },
    pPaging: function (o) { return o === 'Vertical' ? arrow(32, 38, 32, 6) : arrow(10, 22, 54, 22); }, lPaging: function (o) { return o === 'Vertical' ? arrow(32, 38, 32, 6) : arrow(10, 22, 54, 22); },
    rightSide: function (o) { if (o === 'RecentsPanel') return R(6, 6, 18, 32, 4, 'f-sub') + [0, 1, 2].map(function (i) { return R(30, 8 + i * 11, 26, 8, 2, 'f-dim'); }).join(''); if (o === 'DockRail') return R(8, 6, 28, 32, 3, 'f-dim') + R(46, 8, 10, 28, 5, 'f-sub') + [0, 1, 2].map(function (i) { return R(48.5, 12 + i * 8, 5, 5, 1.4, 'f-acc'); }).join(''); return R(14, 6, 36, 32, 4, 'f-dim') + L(26, 15, 38, 29, 's-sub', 1.6); },
    leftPage: function (o) { if (o === 'Launchpad') return R(6, 6, 30, 32, 4, 'f-sub') + [0, 1, 2].map(function (i) { return C(14, 14 + i * 9, 3, 'f-acc') + R(20, 12.5 + i * 9, 12, 3, 1.5, 'f-ink', 'opacity=".6"'); }).join('') + R(40, 8, 18, 28, 4, 'f-dim'); if (o === 'Widget') return R(6, 6, 30, 32, 4, 's-sub ' + NOFILL, 'stroke-dasharray="3 3"') + R(40, 8, 18, 28, 4, 'f-dim') + h.glyph('Assistant', 21, 22, 12, 'currentColor', 1.6).replace('<g ', '<g class="s-ink" '); return R(14, 6, 36, 32, 4, 'f-dim') + L(26, 15, 38, 29, 's-sub', 1.6); },
    lpTheme: function (o) { var l = R(8, 6, 22, 32, 4, 'x', 'fill="#F4F4F8" stroke="rgba(128,128,128,.5)"'), d = R(34, 6, 22, 32, 4, 'x', 'fill="#0E0E14" stroke="rgba(128,128,128,.5)"'); return o === 'Light' ? R(18, 6, 28, 32, 4, 'x', 'fill="#F4F4F8" stroke="rgba(128,128,128,.5)"') : o === 'Dark' ? R(18, 6, 28, 32, 4, 'x', 'fill="#0E0E14" stroke="rgba(128,128,128,.5)"') : l + d; },
    wheelGrouping: function (o) { return o === 'Letter' ? T(32, 29, 'A–Z', 14, 'f-ink', 'font-weight="700"') : o === 'Category' ? R(8, 12, 22, 9, 4.5, 'f-sub') + R(34, 12, 22, 9, 4.5, 'f-dim') + R(14, 25, 26, 9, 4.5, 'f-dim') : h.glyph('Clock', 32, 22, 26, 'currentColor', 1.6).replace('<g ', '<g class="s-ink" '); },
    settingsBackdrop: function (o) { return o === 'Black' ? R(10, 6, 44, 32, 5, 'x', 'fill="#000" stroke="rgba(128,128,128,.5)"') : R(10, 6, 44, 32, 5, 'f-acc') + R(10, 6, 44, 32, 5, 'x', 'fill="#000" opacity=".6"'); },
    radarBackground: function (o) { return o === 'TrueBlack' ? R(10, 6, 44, 32, 5, 'x', 'fill="#000" stroke="rgba(128,128,128,.5)"') : R(10, 6, 44, 32, 5, 'f-acc') + R(10, 6, 44, 32, 5, 'x', 'fill="#000" opacity=".6"'); },
    radarIntensity: function (o) { return C(32, 22, 12, 'f-acc', 'opacity="' + (o === 'Subtle' ? 0.25 : 0.7) + '"') + C(32, 22, 6, 'f-ink'); },
    indMetric: function (o) { return o === 'Signal' ? bars(24, 14, 3).replace(/y="/g, 'y="') : R(18, 15, 26, 13, 3, 's-ink ' + NOFILL, 'stroke-width="1.4"') + R(20.5, 17.5, 15, 8, 1.5, 'f-ink') + R(44.5, 19, 2.4, 5, 1, 'f-ink'); },
    hapticStrength: function (o) { var n = { off: 0, light: 1, medium: 2, strong: 3 }[o]; var s = R(24, 8, 16, 28, 4, 's-sub ' + NOFILL, 'stroke-width="1.4"'); for (var i = 0; i < n; i++) s += L(18 - i * 4, 14, 18 - i * 4, 30, 's-ink', 1.4) + L(46 + i * 4, 14, 46 + i * 4, 30, 's-ink', 1.4); return s; },
    clockFormat: function (o) { return T(32, 28, o === 'Hour12' ? '2:09 pm' : o === 'Hour24' ? '14:09' : 'Auto', 12, 'f-ink', 'font-weight="600"'); },
    clockTileFormat: function (o) { return T(32, 28, o === 'Hour12' ? '2:09 pm' : o === 'Hour24' ? '14:09' : 'Auto', 12, 'f-ink', 'font-weight="600"'); },
    appsOnHome: function (o) { return o === 'Everything' ? [0, 1, 2].map(function (c) { return [0, 1, 2].map(function (r) { return R(10 + c * 16, 6 + r * 12, 11, 9, 2.5, 'f-sub'); }).join(''); }).join('') : R(10, 6, 11, 9, 2.5, 'f-acc') + R(26, 6, 11, 9, 2.5, 'f-acc') + R(42, 6, 11, 9, 2.5, 'f-dim', 'stroke-dasharray="2 2" stroke="rgba(128,128,128,.6)"') + R(10, 18, 11, 9, 2.5, 'f-dim', 'stroke-dasharray="2 2" stroke="rgba(128,128,128,.6)"'); },
  };
  function wpCard(o) {
    if (o === 'solid') return R(10, 4, 44, 36, 5, 'f-sub');
    if (o === 'gradient') return R(10, 4, 44, 36, 5, 'x', 'fill="url(#cgrad)"');
    if (o === 'live') return R(10, 4, 44, 36, 5, 'x', 'fill="#07070C"') + '<polygon points="30,40 34,40 33,8 31,8" class="f-acc" opacity=".8"/>' + C(32, 8, 2, 'f-ink');
    if (o === 'system') return R(10, 4, 44, 36, 5, 'f-dim') + C(26, 16, 5, 'f-sub') + P_('M10 34 L26 24 L38 32 L54 20', 's-sub ' + NOFILL, 1.6) + T(32, 40, 'OS', 6, 'f-ink');
    return R(10, 4, 44, 36, 5, 'f-dim') + C(24, 15, 4, 'f-sub') + P_('M10 36 L24 22 L34 30 L42 24 L54 34', 's-ink ' + NOFILL, 1.6);
  }

  /** The drawing for an option, or null. For the font field the card shows "Aa" in that font. */
  function card(key, id, values) {
    if (key === 'uiFont') return T(32, 29, 'Aa', 20, 'f-ink', 'font-family="' + h.esc(P.FONTS[id] || P.FONTS.space_grotesk) + '" font-weight="500"');
    var fn = DRAW[key];
    if (key === 'wpPagesKind' || key === 'wpGlanceKind') fn = DRAW[key];
    return fn ? fn(id, values || {}) : null;
  }
  function has(key) { return key === 'uiFont' || typeof DRAW[key] === 'function'; }

  return { card: card, has: has, keys: Object.keys(DRAW).filter(function (k) { return typeof DRAW[k] === 'function'; }).concat(['uiFont']) };
});
