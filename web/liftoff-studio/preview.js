/*
 * Liftoff Studio preview: a 2D drawing of the phone as the configuration describes it. Pure functions that return SVG text (no DOM), so the same
 * code is exercised under node. One unit is one dp of a 360 by 740 phone.
 *
 * Scenes: glance, pages, radar, disc, comms, recents. The page picks the scene that the active tab or field concerns (field.scene in schema.json).
 *
 * This is a drawing made from the settings, not the app: Signal wallpapers are still approximations of the app's painters, System and Picture
 * wallpapers are placeholders, text is sample text, and the
 * spacing rules follow the app's own (docs/decisions/*.md) but were not measured against a device.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.StudioPreview = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // One unit is one dp. W and H are the phone's in the orientation being drawn (portrait 360 by 740; landscape 740 by 360, see render).
  var PW = 360, PH = 740, W = PW, H = PH, LAND = false;
  var SCENES = [
    { id: 'glance', label: 'Glance' }, { id: 'pages', label: 'Pages' }, { id: 'radar', label: 'Radar' },
    { id: 'disc', label: 'Drawer' }, { id: 'comms', label: 'Comms' }, { id: 'recents', label: 'Recents' },
  ];
  /** Scenes the schema names for fields that have no screen of their own. */
  var SCENE_ALIAS = { corners: 'glance', dock: 'glance', look: 'glance' };

  var FONTS = {
    space_grotesk: "'Space Grotesk', system-ui, sans-serif", system: 'system-ui, -apple-system, Roboto, sans-serif', inter: "'Inter', system-ui, sans-serif",
    manrope: "'Manrope', system-ui, sans-serif", dm_sans: "'DM Sans', system-ui, sans-serif", jetbrains_mono: "'JetBrains Mono', ui-monospace, monospace",
    atkinson: "'Atkinson Hyperlegible', system-ui, sans-serif", lexend: "'Lexend', system-ui, sans-serif",
  };

  // ------------------------------------------------------------------------------------------------------------ helpers
  function f1(n) { return Math.round(n * 10) / 10; }
  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
  function lerp(a, b, t) { return a + (b - a) * t; }
  function rgb(n) { n = n >>> 0; return [(n >>> 16) & 255, (n >>> 8) & 255, n & 255]; }
  function css(n, alpha) {
    var c = rgb(n);
    return alpha === undefined || alpha >= 1 ? 'rgb(' + c[0] + ',' + c[1] + ',' + c[2] + ')' : 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',' + f1(alpha * 100) / 100 + ')';
  }
  function lum(n) { var c = rgb(n); return (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / 255; }
  function mixc(a, b, t) { var x = rgb(a), y = rgb(b); return ((255 << 24) | (Math.round(lerp(x[0], y[0], t)) << 16) | (Math.round(lerp(x[1], y[1], t)) << 8) | Math.round(lerp(x[2], y[2], t))) >>> 0; }
  function g(v, k, d) { var x = v[k]; return x === undefined || x === null ? d : x; }

  function theme(v) {
    var m = g(v, 'themeMode', 'Dark'), accent = g(v, 'accent', 0xFF8E7BFF);
    if (m === 'Light') return { mode: m, dark: false, bg: '#F4F4F8', ink: '#14141A', sub: '#6A6A75', card: '#FFFFFF', hair: 'rgba(0,0,0,0.12)', accent: accent, panel: 'rgba(255,255,255,0.92)' };
    return { mode: m, dark: true, bg: m === 'Amoled' ? '#000000' : '#0B0B10', ink: '#F2F2F7', sub: '#9A9AA8', card: m === 'Amoled' ? '#0C0C10' : '#17171F', hair: 'rgba(255,255,255,0.14)', accent: accent, panel: 'rgba(20,20,26,0.92)' };
  }
  function fontOf(v) { return FONTS[g(v, 'uiFont', 'space_grotesk')] || FONTS.space_grotesk; }

  function rect(x, y, w, h, rx, fill, extra) { return '<rect x="' + f1(x) + '" y="' + f1(y) + '" width="' + f1(w) + '" height="' + f1(h) + '" rx="' + f1(rx || 0) + '" fill="' + fill + '"' + (extra ? ' ' + extra : '') + '/>'; }
  function circle(cx, cy, r, fill, extra) { return '<circle cx="' + f1(cx) + '" cy="' + f1(cy) + '" r="' + f1(r) + '" fill="' + fill + '"' + (extra ? ' ' + extra : '') + '/>'; }
  function line(x1, y1, x2, y2, stroke, w, extra) { return '<line x1="' + f1(x1) + '" y1="' + f1(y1) + '" x2="' + f1(x2) + '" y2="' + f1(y2) + '" stroke="' + stroke + '" stroke-width="' + (w || 1) + '"' + (extra ? ' ' + extra : '') + '/>'; }
  function text(x, y, s, size, fill, extra) { return '<text x="' + f1(x) + '" y="' + f1(y) + '" font-size="' + f1(size) + '" fill="' + fill + '"' + (extra ? ' ' + extra : '') + '>' + esc(s) + '</text>'; }
  var uidN = 0;
  function uid(p) { uidN = (uidN + 1) % 1e6; return p + uidN; }

  // ------------------------------------------------------------------------------------------------------------- glyphs
  var GLYPHS = {
    Music: '<path d="M9 18V6l10-2v12"/><circle cx="7" cy="18" r="2.5"/><circle cx="17" cy="16" r="2.5"/>',
    Camera: '<rect x="3" y="7" width="18" height="12" rx="2.5"/><circle cx="12" cy="13" r="3.2"/><path d="M8 7l1.5-2h5L16 7"/>',
    Search: '<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3.5"/><path d="M12 12l6-5"/>',
    AllApps: '<circle cx="6" cy="6" r="1.5"/><circle cx="12" cy="6" r="1.5"/><circle cx="18" cy="6" r="1.5"/><circle cx="6" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="18" cy="12" r="1.5"/><circle cx="6" cy="18" r="1.5"/><circle cx="12" cy="18" r="1.5"/><circle cx="18" cy="18" r="1.5"/>',
    Notifications: '<path d="M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
    Settings: '<circle cx="12" cy="12" r="3.5"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/>',
    Phone: '<path d="M6 4h3l2 5-2 1.5a11 11 0 0 0 5 5l1.5-2 5 2v3a2 2 0 0 1-2 2A15 15 0 0 1 4 6a2 2 0 0 1 2-2z"/>',
    Messages: '<path d="M4 5h16v11H9l-5 4z"/>',
    Browser: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c3.5 3.5 3.5 13.5 0 17M12 3.5c-3.5 3.5-3.5 13.5 0 17"/>',
    Email: '<rect x="3" y="6" width="18" height="12" rx="2"/><path d="M3 7.5l9 6 9-6"/>',
    Maps: '<path d="M12 21s-6-6-6-11a6 6 0 0 1 12 0c0 5-6 11-6 11z"/><circle cx="12" cy="10" r="2.2"/>',
    Calendar: '<rect x="4" y="5" width="16" height="15" rx="2"/><path d="M4 10h16M8 3v4M16 3v4"/>',
    Clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3 2"/>',
    Gallery: '<rect x="4" y="5" width="16" height="14" rx="2"/><circle cx="9" cy="10" r="1.6"/><path d="M4 17l5-4 4 3 3-2 4 3"/>',
    Contacts: '<circle cx="12" cy="9" r="3.5"/><path d="M5 20a7 7 0 0 1 14 0"/>',
    Calculator: '<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M8 7h8M8 12h2M12 12h2M8 16h2M12 16h2M16 12v4"/>',
    Assistant: '<path d="M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z"/>',
    Flashlight: '<path d="M8 3h8v5l-2 3v10h-4V11L8 8z"/>',
    GridDenser: '<path d="M5 5h5v5H5zM14 5h5v5h-5zM5 14h5v5H5zM14 14h5v5h-5z"/>',
    GridSparser: '<path d="M4 4h7v7H4zM13 13h7v7h-7z"/>',
    Ringer: '<path d="M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z"/><path d="M3 9a9 9 0 0 1 2-4M21 9a9 9 0 0 0-2-4"/>',
    Vibrate: '<rect x="8" y="4" width="8" height="16" rx="1.5"/><path d="M4 8v8M20 8v8"/>',
    Silent: '<path d="M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z"/><path d="M4 4l16 16"/>',
    DoNotDisturb: '<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>',
    Comms: '<path d="M4 4h12v9H8l-4 3z"/><path d="M10 15h6l4 3v-9h-3"/>',
    Recents: '<rect x="4" y="8" width="12" height="11" rx="2"/><path d="M8 5h12v11"/>',
    Back: '<path d="M15 5l-7 7 7 7"/>',
    None: '<circle cx="12" cy="12" r="7" stroke-dasharray="2.5 3"/>',
    Note: '<path d="M10 17V6l8-2v11"/><circle cx="8" cy="17" r="2.2"/><circle cx="16" cy="15" r="2.2"/>',
  };
  var ACTION_GLYPH = {
    Music: 'Music', Camera: 'Camera', Search: 'Search', AllApps: 'AllApps', Notifications: 'Notifications', LauncherSettings: 'Settings', SystemSettings: 'Settings', Phone: 'Phone',
    Messages: 'Messages', Browser: 'Browser', Email: 'Email', Maps: 'Maps', Calendar: 'Calendar', Clock: 'Clock', Gallery: 'Gallery', Contacts: 'Contacts', Calculator: 'Calculator',
    Assistant: 'Assistant', Flashlight: 'Flashlight', GridDenser: 'GridDenser', GridSparser: 'GridSparser', RingerCycle: 'Ringer', RingerNormal: 'Ringer', RingerVibrate: 'Vibrate',
    RingerSilent: 'Silent', DoNotDisturb: 'DoNotDisturb', OpenLaunchpad: 'Comms', OpenRecents: 'Recents',
  };
  function glyphKey(actionValue) { var id = String(actionValue || '').replace(/^(action:|a:)/, ''); return ACTION_GLYPH[id] || (actionValue === '' || actionValue === '-' || !actionValue ? 'None' : 'None'); }
  function glyph(key, cx, cy, size, stroke, w) {
    return '<g transform="translate(' + f1(cx - size / 2) + ' ' + f1(cy - size / 2) + ') scale(' + f1(size / 24 * 1000) / 1000 + ')" fill="none" stroke="' + stroke + '" stroke-width="' + (w || 1.8) + '" stroke-linecap="round" stroke-linejoin="round">' + (GLYPHS[key] || GLYPHS.None) + '</g>';
  }

  // ----------------------------------------------------------------------------------------------------- app icon sample
  var APPS = ['Phone', 'Messages', 'Camera', 'Maps', 'Mail', 'Music', 'Photos', 'Clock', 'Notes', 'Files', 'Web', 'Wallet', 'Games', 'Health', 'News', 'Weather', 'Calc', 'Chat', 'Docs', 'Shop', 'Bank', 'Taxi', 'Books', 'Radio'];
  var APP_GLYPH = ['Phone', 'Messages', 'Camera', 'Maps', 'Email', 'Music', 'Gallery', 'Clock', 'Calendar', 'Contacts', 'Browser', 'Assistant', 'AllApps', 'Ringer', 'Recents', 'DoNotDisturb', 'Calculator', 'Comms', 'Messages', 'Search', 'Notifications', 'Maps', 'Calendar', 'Music'];
  var APP_COLOURS = [0xFF3DDC84, 0xFF4F8CFF, 0xFFFF6B6B, 0xFFFFB347, 0xFFB57BFF, 0xFFFF5FA2, 0xFF2EC4B6, 0xFF8A8F99, 0xFFFFD166, 0xFF5AC8FA, 0xFF7BD389, 0xFFEF8354];

  function shapePath(shape, x, y, s, i) {
    var sh = shape === 'Original' ? ['Circle', 'Squircle', 'Rounded', 'Square'][i % 4] : shape;
    if (sh === 'Circle') return '<circle cx="' + f1(x + s / 2) + '" cy="' + f1(y + s / 2) + '" r="' + f1(s / 2) + '"';
    if (sh === 'Square') return '<rect x="' + f1(x) + '" y="' + f1(y) + '" width="' + f1(s) + '" height="' + f1(s) + '" rx="' + f1(s * 0.06) + '"';
    if (sh === 'Squircle') {
      var k = s * 0.5, c = s * 0.04, x1 = x, x2 = x + s, y1 = y, y2 = y + s, m = s * 0.5;
      return '<path d="M' + f1(x1 + m) + ' ' + f1(y1) + ' C ' + f1(x2 - c) + ' ' + f1(y1) + ' ' + f1(x2) + ' ' + f1(y1 + c) + ' ' + f1(x2) + ' ' + f1(y1 + m) + ' C ' + f1(x2) + ' ' + f1(y2 - c) + ' ' + f1(x2 - c) + ' ' + f1(y2) + ' ' + f1(x1 + m) + ' ' + f1(y2) + ' C ' + f1(x1 + c) + ' ' + f1(y2) + ' ' + f1(x1) + ' ' + f1(y2 - c) + ' ' + f1(x1) + ' ' + f1(y1 + m) + ' C ' + f1(x1) + ' ' + f1(y1 + c) + ' ' + f1(x1 + c) + ' ' + f1(y1) + ' ' + f1(x1 + m) + ' ' + f1(y1) + ' Z"' + (k ? '' : '');
    }
    return '<rect x="' + f1(x) + '" y="' + f1(y) + '" width="' + f1(s) + '" height="' + f1(s) + '" rx="' + f1(s * 0.24) + '"';
  }
  function closeShape(p, fill, extra) { return p + ' fill="' + fill + '"' + (extra ? ' ' + extra : '') + (p.indexOf('<path') === 0 ? '/>' : '/>'); }

  /** One sample app icon at top-left (x, y) with edge s, in the shape, tint and plate the settings ask for. */
  function appIcon(v, x, y, s, i, T) {
    var tint = g(v, 'iconTint', 'None'), plate = g(v, 'iconPlate', true), shape = g(v, 'iconShape', 'Rounded');
    var col = APP_COLOURS[i % APP_COLOURS.length];
    if (tint === 'Accent') col = T.accent; else if (tint === 'Mono') col = T.dark ? 0xFFC9C9D2 : 0xFF55555E;
    var glyphName = APP_GLYPH[i % APP_GLYPH.length], cx = x + s / 2, cy = y + s / 2, out = '';
    var transparentKind = i % 3 === 2; // some icons ship without a background; the plate setting is for them
    if (transparentKind && !plate) return glyph(glyphName, cx, cy, s * 0.62, css(col), 1.6);
    if (transparentKind) { out += closeShape(shapePath(shape, x, y, s, i), T.dark ? '#262630' : '#E4E4EC'); return out + glyph(glyphName, cx, cy, s * 0.5, css(col), 1.7); }
    out += closeShape(shapePath(shape, x, y, s, i), css(col));
    return out + glyph(glyphName, cx, cy, s * 0.5, tint === 'Mono' ? (T.dark ? '#16161C' : '#fff') : '#FFFFFF', 1.7);
  }

  // ---------------------------------------------------------------------------------------------------------- wallpaper
  function gradientDef(id, from, to, angle) {
    var a = (angle % 360) * Math.PI / 180, dx = Math.cos(a) / 2, dy = Math.sin(a) / 2;
    return '<linearGradient id="' + id + '" x1="' + f1(0.5 - dx) + '" y1="' + f1(0.5 - dy) + '" x2="' + f1(0.5 + dx) + '" y2="' + f1(0.5 + dy) + '"><stop offset="0" stop-color="' + css(from) + '"/><stop offset="1" stop-color="' + css(to) + '"/></linearGradient>';
  }

  // Wave 58 (CFG-72): the Signal wallpapers drawn as approximations of the app's painters (feature-grid LivingWallpapers.kt), in the theme's
  // accent over the theme's black (or, in Light, pale) gradient: Beam rises from the bottom edge, Spire is a cusp from the lower third with a
  // bloom under it and a white core, Halo a tilted ring, Laser thin lines, Shaft a wide diagonal band. Still frames: no motion, no signal level.
  var BG_LIVE = {
    beam: function (a, L) {
      var cx = 180, s = '<ellipse cx="' + cx + '" cy="600" rx="200" ry="330" fill="url(#LW)"/>';
      s += '<path d="M' + (cx - 5) + ' 740 C ' + (cx - 30) + ' 520 ' + (cx - 150) + ' 300 ' + (cx - 210) + ' 40 L ' + (cx + 210) + ' 40 C ' + (cx + 150) + ' 300 ' + (cx + 30) + ' 520 ' + (cx + 5) + ' 740 Z" fill="url(#LG)" opacity="' + (L ? 0.35 : 0.8) + '"/>';
      s += '<path d="M' + (cx - 2) + ' 740 L ' + (cx - 0.5) + ' 110 L ' + (cx + 0.5) + ' 110 L ' + (cx + 2) + ' 740 Z" fill="url(#LC)"/>';
      for (var i = 0; i < 14; i++) { var y = 640 - i * 36; s += circle(cx + (i % 2 ? 3 : -3), y, 1.4, '#FFFFFF', 'fill-opacity="' + f1((L ? 0.3 : 0.75) * (1 - i / 16) * 100) / 100 + '"'); }
      return s + '<ellipse cx="' + cx + '" cy="736" rx="34" ry="12" fill="url(#LT)"/>';
    },
    spire: function (a, L) {
      var cx = 180, base = 474, h = 330, hb = 144;
      var s = rect(0, 370, 360, 370, 0, 'url(#LF)') + '<ellipse cx="' + cx + '" cy="' + (base + 90) + '" rx="200" ry="190" fill="url(#LW)"/>';
      s += '<path d="M' + cx + ' ' + (base - h) + ' C ' + f1(cx + hb * 0.03) + ' ' + f1(base - h * 0.55) + ' ' + f1(cx + hb * 0.12) + ' ' + f1(base - h * 0.12) + ' ' + (cx + hb) + ' ' + base + ' L ' + (cx - hb) + ' ' + base +
        ' C ' + f1(cx - hb * 0.12) + ' ' + f1(base - h * 0.12) + ' ' + f1(cx - hb * 0.03) + ' ' + f1(base - h * 0.55) + ' ' + cx + ' ' + (base - h) + ' Z" fill="url(#LS)" opacity="' + (L ? 0.45 : 0.95) + '"/>';
      return s + (L ? '' : '<ellipse cx="' + cx + '" cy="' + f1(base - h * 0.45) + '" rx="' + f1(hb * 0.3) + '" ry="' + f1(h * 0.38) + '" fill="url(#LT)"/>');
    },
    halo: function (a, L) {
      return '<g transform="rotate(-18 180 360)">' + [0, 1, 2].map(function (i) { return '<ellipse cx="180" cy="360" rx="' + (150 + i * 6) + '" ry="' + (96 + i * 4) + '" fill="none" stroke="' + a + '" stroke-opacity="' + f1((L ? 0.25 : 0.7) - i * 0.18) + '" stroke-width="' + (5 - i * 1.5) + '" stroke-dasharray="' + (520 - i * 60) + ' 900" stroke-linecap="round"/>'; }).join('') +
        '<ellipse cx="180" cy="360" rx="150" ry="96" fill="none" stroke="#FFFFFF" stroke-opacity="' + (L ? 0.2 : 0.8) + '" stroke-width="1.4" stroke-dasharray="90 900" stroke-linecap="round"/></g>';
    },
    laser: function (a, L) { var o = L ? 0.4 : 1; return line(0, 250, 360, 330, a, 1.6, 'stroke-opacity="' + 0.85 * o + '"') + line(0, 251, 360, 331, '#FFFFFF', 0.5, 'stroke-opacity="' + 0.7 * o + '"') + line(0, 420, 360, 380, a, 1, 'stroke-opacity="' + 0.5 * o + '"') + line(0, 560, 360, 520, a, 1, 'stroke-opacity="' + 0.35 * o + '"'); },
    shaft: function (a, L) { return '<polygon points="-20,740 120,740 380,0 240,0" fill="url(#LG)" opacity="' + (L ? 0.25 : 0.55) + '"/><polygon points="40,740 60,740 330,0 310,0" fill="#FFFFFF" opacity="' + (L ? 0.08 : 0.18) + '"/>'; },
  };
  function signalDefs(ac, L) {
    return '<linearGradient id="LG" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.9"/><stop offset="0.18" stop-color="' + ac + '" stop-opacity="0.85"/><stop offset="1" stop-color="' + ac + '" stop-opacity="0"/></linearGradient>' +
      '<linearGradient id="LC" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#FFFFFF" stop-opacity="' + (L ? 0.5 : 1) + '"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/></linearGradient>' +
      '<linearGradient id="LS" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.9"/><stop offset="0.3" stop-color="' + ac + '"/><stop offset="0.86" stop-color="' + ac + '" stop-opacity="0.8"/><stop offset="1" stop-color="' + ac + '" stop-opacity="0"/></linearGradient>' +
      '<linearGradient id="LF" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="' + ac + '" stop-opacity="0"/><stop offset="1" stop-color="' + ac + '" stop-opacity="' + (L ? 0.12 : 0.3) + '"/></linearGradient>' +
      '<radialGradient id="LW"><stop offset="0" stop-color="' + ac + '" stop-opacity="' + (L ? 0.12 : 0.32) + '"/><stop offset="1" stop-color="' + ac + '" stop-opacity="0"/></radialGradient>' +
      '<radialGradient id="LT"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.95"/><stop offset="0.4" stop-color="' + ac + '" stop-opacity="0.6"/><stop offset="1" stop-color="' + ac + '" stop-opacity="0"/></radialGradient>' +
      '<linearGradient id="BGFX" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="' + ac + '" stop-opacity="0.4"/><stop offset="1" stop-color="' + ac + '" stop-opacity="0"/></linearGradient>' +
      '<linearGradient id="LBG" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="' + (L ? '#FAFAFD' : '#000000') + '"/><stop offset="1" stop-color="' + (L ? '#E9E9F1' : '#0B0B16') + '"/></linearGradient>';
  }
  var BG_FX = {
    'glow-lines': function (a) { return [180, 300, 430, 560].map(function (y, i) { return '<path d="M0 ' + y + ' Q 180 ' + (y - 50 + i * 12) + ' 360 ' + y + '" fill="none" stroke="' + a + '" stroke-opacity="0.35" stroke-width="1.2"/>'; }).join(''); },
    'breathing-dots': function (a) { var s = ''; for (var y = 40; y < 740; y += 60) for (var x = 30; x < 360; x += 60) s += circle(x, y, 2.2, a, 'fill-opacity="0.35"'); return s; },
    'bottom-glow': function (a) { return rect(0, 560, 360, 180, 0, 'url(#BGFX)'); },
    'radar-sweep': function (a) { return '<path d="M180 370 L 360 300 A 190 190 0 0 1 340 470 Z" fill="' + a + '" fill-opacity="0.16"/>' + circle(180, 370, 120, 'none', 'stroke="' + a + '" stroke-opacity="0.25"') + circle(180, 370, 60, 'none', 'stroke="' + a + '" stroke-opacity="0.25"'); },
  };

  /**
   * The wallpaper layer for the surface ("glance" or "pages"). Returns {defs, body, ink}: ink is the text colour that reads on it.
   * Signal is an approximation in the theme colours; System and Picture are placeholders and the drawing says so on the canvas itself.
   */
  function wallpaper(v, surface, T) {
    var P = surface === 'pages' && !g(v, 'wpPagesSame', true) ? 'wpPages' : 'wpGlance';
    var kind = g(v, P + 'Kind', 'live'), id = uid('wp'), defs = '', body = '', base = 0xFF16161C, ink = '#F2F2F7';
    if (kind === 'solid') { base = g(v, P + 'Solid', 0xFF16161C); body = rect(0, 0, W, H, 0, css(base)); }
    else if (kind === 'gradient') {
      var a = g(v, P + 'From', 0xFF2A2F6B), b = g(v, P + 'To', 0xFF0B0B10);
      defs = gradientDef(id, a, b, g(v, P + 'Angle', 90)); body = rect(0, 0, W, H, 0, 'url(#' + id + ')'); base = mixc(a, b, 0.5);
    } else if (kind === 'live') {
      var s1 = g(v, P === 'wpGlance' ? 'bgSlot1' : 'wpPagesSlot1', 'spire'), s2 = g(v, P === 'wpGlance' ? 'bgSlot2' : 'wpPagesSlot2', 'breathing-dots'), ac = css(T.accent), L = !T.dark;
      defs = signalDefs(ac, L);
      body = rect(0, 0, W, H, 0, 'url(#LBG)') + (BG_LIVE[s1] ? BG_LIVE[s1](ac, L) : '') + (BG_FX[s2] ? BG_FX[s2](ac) : '');
      base = L ? 0xFFF1F1F6 : 0xFF05050A;
    } else if (kind === 'system') {
      defs = gradientDef(id, 0xFF22384D, 0xFF4A2F55, 70); body = rect(0, 0, W, H, 0, 'url(#' + id + ')') + text(180, 30, 'System live wallpaper shows through (placeholder)', 8.5, 'rgba(255,255,255,0.45)', 'text-anchor="middle"');
      var sd = g(v, P + 'SysDim', 0); if (sd) body += rect(0, 0, W, H, 0, '#000', 'opacity="' + sd / 100 + '"'); base = 0xFF30364F;
    } else { // picture
      var fit = g(v, P + 'PicFit', 'fill'), dim = g(v, P + 'PicDim', 0), inset = fit === 'fit' ? 54 : 0;
      defs = '<pattern id="' + id + '" width="14" height="14" patternUnits="userSpaceOnUse" patternTransform="rotate(35)"><rect width="14" height="14" fill="#2B2F3A"/><rect width="7" height="14" fill="#323746"/></pattern>';
      body = rect(0, 0, W, H, 0, '#050507') + rect(0, inset, W, H - 2 * inset, 0, 'url(#' + id + ')') + glyph('Gallery', 180, 330, 54, 'rgba(255,255,255,0.5)', 1.4) +
        text(180, 380, 'Picture (placeholder)', 11, 'rgba(255,255,255,0.55)', 'text-anchor="middle"') + (dim ? rect(0, 0, W, H, 0, '#000', 'opacity="' + dim / 100 + '"') : '');
      base = 0xFF2B2F3A;
    }
    ink = lum(base) > 0.62 ? '#14141A' : '#F4F4F8';
    return { defs: defs, body: body, ink: ink, base: base };
  }

  // ------------------------------------------------------------------------------------------------------ clock
  function clockParts(v) {
    var fmt = g(v, 'clockFormat', 'System'), h24 = fmt !== 'Hour12'; // System is drawn as 24 hour
    return { h: h24 ? '14' : '2', m: '09', is24: h24, date: 'Mon 23 Nov' };
  }

  function analog(cx, cy, d, style, ink, accent) {
    var r = d / 2, s = '', rot = function (deg, len) { var a = (deg - 90) * Math.PI / 180; return [cx + Math.cos(a) * len, cy + Math.sin(a) * len]; };
    if (style === 'AnalogClassic') {
      s += circle(cx, cy, r, 'none', 'stroke="' + ink + '" stroke-opacity="0.8" stroke-width="2"');
      for (var i = 0; i < 12; i++) { var p1 = rot(i * 30, r * 0.86), p2 = rot(i * 30, r * (i % 3 ? 0.93 : 0.8)); s += line(p1[0], p1[1], p2[0], p2[1], ink, i % 3 ? 1.5 : 3, 'stroke-opacity="0.85"'); }
    } else s += circle(cx, cy, r, 'none', 'stroke="' + ink + '" stroke-opacity="0.35" stroke-width="1.5"');
    var hh = rot(64.5, r * 0.5), mm = rot(54, r * 0.78);
    s += line(cx, cy, hh[0], hh[1], ink, style === 'AnalogClassic' ? 5 : 3, 'stroke-linecap="round"') + line(cx, cy, mm[0], mm[1], ink, style === 'AnalogClassic' ? 3 : 2, 'stroke-linecap="round"') + circle(cx, cy, 4, accent);
    return s;
  }

  var TOP_AREA = { w: 0.82, h: 0.42 };
  /** The Glance clock at its saved place, in the style, size, hours, date and colour of the settings. */
  function clockLayer(v, T, ink) {
    var style = g(v, 'clockStyle', 'BigDigital'); if (style === 'Off') return '';
    var lang = g(v, 'language', 'System'); if (style === 'Word' && lang !== 'System' && lang !== 'English') style = 'BigDigital';
    var cp = clockParts(v), factor = { Small: 0.6, Medium: 0.8, Large: 1 }[g(v, 'glanceClockSize', 'Large')] || 1;
    var colr = g(v, 'glanceClockAccent', false) ? css(T.accent) : ink, showDate = g(v, 'glanceClockDate', false);
    var areaW = W, areaH = H, bw, bh, body, fs;
    if (style === 'BigDigital') {
      fs = 132 * factor; bw = fs * 1.15; bh = fs * 1.9 + 14;
      body = function (l, t) { var cx = l + bw / 2; return text(cx, t + fs * 0.86, cp.h, fs, colr, 'text-anchor="middle" font-weight="300"') + text(cx, t + fs * 1.78, cp.m, fs, colr, 'text-anchor="middle" font-weight="300"') + rect(cx - fs * 0.28, t + fs * 1.88, fs * 0.56, 3, 1.5, css(T.accent)); };
    } else if (style === 'BigDigitalLine') {
      fs = 96 * factor; var txt = cp.h + ':' + cp.m; bw = fs * 0.56 * txt.length; bh = fs * 0.95;
      body = function (l, t) { return text(l + bw / 2, t + fs * 0.8, txt, fs, colr, 'text-anchor="middle" font-weight="300"'); };
    } else if (style === 'Word') {
      fs = 44 * factor; bw = fs * 5.2; bh = fs * 2.3;
      body = function (l, t) { return text(l + bw / 2, t + fs * 0.95, 'five past', fs, colr, 'text-anchor="middle" font-weight="300"') + text(l + bw / 2, t + fs * 2.05, cp.is24 ? 'two' : 'two', fs, colr, 'text-anchor="middle" font-weight="500"'); };
    } else {
      var d = 260 * factor; bw = d; bh = d;
      body = function (l, t) { return analog(l + d / 2, t + d / 2, d, style, colr, css(T.accent)); };
    }
    var dateH = showDate ? 30 : 0;
    // fit: the app shrinks the clock until it is inside its area (82 percent wide, 42 percent high)
    var sc = Math.min(1, TOP_AREA.w * areaW / bw, TOP_AREA.h * areaH / (bh + dateH));
    var w = bw * sc, h = (bh + dateH) * sc;
    var kx = LAND ? 'glanceClockXLand' : 'glanceClockX', ky = LAND ? 'glanceClockYLand' : 'glanceClockY';
    var x = clamp(g(v, kx, 0.5), 0, 1), y = clamp(g(v, ky, 0.14), 0, 1);
    var left = clamp(x * areaW - w / 2, 0, Math.max(0, areaW - w)), top = clamp(y * areaH, 0, Math.max(0, areaH - h));
    var inner = body(0, 0) + (showDate ? text(bw / 2, bh + 22, cp.date, 17, colr, 'text-anchor="middle" opacity="0.85"') : '');
    // Wave 58 (CFG-72): the box is marked so the page can drag it (the app's clock edit mode); the drag writes the same two fields.
    return '<g class="clock" data-kx="' + kx + '" data-ky="' + ky + '" data-left="' + f1(left) + '" data-top="' + f1(top) + '" data-w="' + f1(w) + '" data-h="' + f1(h) + '" data-areaw="' + areaW + '" data-areah="' + areaH + '">' +
      rect(left, top, w, h, 8, 'transparent', 'class="clock-hit"') + '<g transform="translate(' + f1(left) + ' ' + f1(top) + ') scale(' + f1(sc * 1000) / 1000 + ')">' + inner + '</g></g>';
  }

  // ----------------------------------------------------------------------------------------------------- hotspots
  function hotspotSet(v) { var s = g(v, 'glanceHotspots', 'TopLeft,TopRight,BottomRight,BottomLeft'); return s ? s.split(',') : []; }
  function cornerAction(v, c) { var a = v['corners.' + c]; return a === undefined ? 'action:None' : a; }
  /** Wave 59: a corner draws something when it is on and holds an assignment (an empty corner, "", draws nothing and reserves no room). */
  function cornerDrawn(v, c) { var a = v['corners.' + c]; return hotspotSet(v).indexOf(c) >= 0 && a !== '' && a !== '-' && a !== null; }
  function cornerGlyph(v, c, cx, cy, sz, T, ink) {
    var a = cornerAction(v, c);
    if (a === '~custom') return glyph('AllApps', cx, cy, sz, ink, 1.6);
    if (g(v, 'cornerIconMode', 'Glyph') === 'AppIcon') return appIcon(v, cx - sz / 2, cy - sz / 2, sz, CORNER_IDX[c], T);
    return glyph(glyphKey(a), cx, cy, sz, ink, 1.8);
  }
  var CORNER_IDX = { TopLeft: 2, TopRight: 5, BottomRight: 11, BottomLeft: 12 };
  function cornersLayer(v, T, ink, only) {
    var on = hotspotSet(v), style = g(v, 'cornerStyle', 'Glyph'), sz = g(v, 'cornerIconSize', 32), out = '', pad = 14;
    var rtl = g(v, 'homeOrder', 'LeftToRight') === 'RightToLeft';
    ['TopLeft', 'TopRight', 'BottomRight', 'BottomLeft'].forEach(function (c) {
      if (on.indexOf(c) < 0 || (only && only.indexOf(c) < 0)) return;
      var left = c.indexOf('Left') >= 0, top = c.indexOf('Top') >= 0, ox = left ? 0 : W, oy = top ? 0 : H, sx = left ? 1 : -1, sy = top ? 1 : -1;
      var cx, cy;
      if (style === 'Quarter') {
        var R = sz * 1.95;
        var d = 'M' + ox + ' ' + oy + ' L ' + (ox + sx * R) + ' ' + oy + ' A ' + R + ' ' + R + ' 0 0 ' + (sx * sy > 0 ? 1 : 0) + ' ' + ox + ' ' + (oy + sy * R) + ' Z';
        out += '<path d="' + d + '" fill="' + (T.dark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.10)') + '" stroke="' + T.hair + '"/>';
        cx = ox + sx * R * 0.42; cy = oy + sy * R * 0.42;
      } else if (style === 'Bubble') {
        var r = sz * 0.82; cx = ox + sx * (r + 10); cy = oy + sy * (r + 10 + (top ? 22 : 0));
        out += circle(cx, cy, r, T.dark ? 'rgba(255,255,255,0.16)' : 'rgba(0,0,0,0.12)', 'stroke="' + T.hair + '"');
      } else { cx = ox + sx * (pad + sz / 2); cy = oy + sy * (pad + sz / 2 + (top ? 22 : 0)); }
      out += cornerGlyph(v, c, cx, cy, sz * (style === 'Quarter' ? 0.62 : style === 'Bubble' ? 0.9 : 1), T, ink);
    });
    return out + (rtl ? '' : '');
  }

  // --------------------------------------------------------------------------------------------------------- calm style
  function calmLayer(v, T, ink) {
    var wp = g(v, 'wpGlanceKind', 'live'), st = g(v, 'calmStyle', 'auto');
    if (st === 'auto') st = wp === 'live' ? 'EdgeDots' : 'None';
    var inten = clamp(g(v, 'indIntensity', 0.6), 0, 1), cap = 0.35 * inten, col = g(v, 'indHigh', 0xFF6BFF8F), out = '', c = css(col);
    var ambient = ['EdgeDots', 'CornerBloom', 'BottomGlow', 'Beam'].indexOf(st) >= 0;
    var al = 0.7 * cap + 0.05;
    if (st === 'EdgeDots') { for (var y = 50; y < H - 40; y += 26) { out += circle(7, y, 2.3, c, 'fill-opacity="' + f1(al * 100) / 100 + '"') + circle(W - 7, y, 2.3, c, 'fill-opacity="' + f1(al * 100) / 100 + '"'); } }
    if (st === 'CornerBloom') {
      var id = uid('cb'); out += '<defs><radialGradient id="' + id + '"><stop offset="0" stop-color="' + c + '" stop-opacity="' + f1(al * 100) / 100 + '"/><stop offset="1" stop-color="' + c + '" stop-opacity="0"/></radialGradient></defs>' +
        circle(0, H, 130, 'url(#' + id + ')') + circle(W, H, 130, 'url(#' + id + ')') + circle(0, 0, 90, 'url(#' + id + ')') + circle(W, 0, 90, 'url(#' + id + ')');
    }
    if (st === 'BottomGlow') { var id2 = uid('bg'); out += '<defs><linearGradient id="' + id2 + '" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="' + c + '" stop-opacity="' + f1(al * 100) / 100 + '"/><stop offset="1" stop-color="' + c + '" stop-opacity="0"/></linearGradient></defs>' + rect(0, H - 150, W, 150, 0, 'url(#' + id2 + ')'); }
    if (st === 'Beam') out += line(W - 3, 120, W - 3, H - 120, c, 5, 'stroke-opacity="' + f1(al * 40) / 100 + '"') + line(W - 3, H - 120 - (H - 240) * 0.7, W - 3, H - 120, c, 3, 'stroke-opacity="' + f1(Math.min(0.5, al * 1.4) * 100) / 100 + '" stroke-linecap="round"');
    if (st === 'Regular' || (ambient && g(v, 'calmRegular', false))) out += regularIndicators(W / 2, H - 52, ink);
    return out;
  }
  function regularIndicators(cx, y, ink) {
    var s = '';
    for (var i = 0; i < 4; i++) s += rect(cx - 52 + i * 6, y + 12 - (i + 1) * 3, 4, (i + 1) * 3, 1, ink, i < 3 ? '' : 'opacity="0.35"');
    s += rect(cx + 6, y + 1, 22, 11, 3, 'none', 'stroke="' + ink + '" stroke-width="1.2"') + rect(cx + 8, y + 3, 14, 7, 1.5, ink) + rect(cx + 28.5, y + 4.5, 2, 4, 1, ink);
    return s + text(cx + 38, y + 11, '82%', 10, ink, 'font-weight="500"');
  }

  // ----------------------------------------------------------------------------------------------------------- dock
  function dockOn(v) { return g(v, 'dock.enabled', 'auto') !== 'off'; }
  /** Where the dock sits: the bottom in portrait; in landscape the edge the settings say (DockSettings.edgeFor). */
  function dockEdge(v) { var e = g(v, 'dock.edge', 'Bottom'); return LAND && (e === 'Left' || e === 'Right') ? e : 'Bottom'; }
  var MAGNIFY = { subtle: 1.3, medium: 1.6, large: 2.0 };
  /**
   * Wave 58 (CFG-72): the icon scales of a dock row while a sample pointer rests on icon [at] (the app's rail magnifies only under a finger or
   * pointer, so Studio shows it frozen): the icon under it at the strength's peak, the neighbours on a cosine falloff over two icons.
   */
  function magnifyScales(v, n, at) {
    var peak = g(v, 'dock.magnify', false) ? (MAGNIFY[g(v, 'dock.magnifyStrength', 'medium')] || 1.6) : 1, out = [];
    for (var i = 0; i < n; i++) { var d = Math.min(1, Math.abs(i - at) / 2); out.push(1 + (peak - 1) * Math.cos(d * Math.PI / 2)); }
    return out;
  }
  function dockLayer(v, T, ink, baseY) {
    if (!dockOn(v)) return { svg: '', top: H, side: null, width: 0 };
    var style = g(v, 'dock.style', 'standard'), mode = g(v, 'dock.mode', 'PinnedPlusRecent'), edge = dockEdge(v), y = baseY, out = '';
    var vertical = edge !== 'Bottom';
    if (mode === 'Collapsed') {
      if (vertical) { var hx = edge === 'Left' ? 8 : W - 13; return { svg: rect(hx, H / 2 - 46, 5, 92, 2.5, T.dark ? 'rgba(255,255,255,0.45)' : 'rgba(0,0,0,0.35)'), top: H, side: edge, width: 18 }; }
      return { svg: rect(W / 2 - 46, y + 24, 92, 5, 2.5, T.dark ? 'rgba(255,255,255,0.45)' : 'rgba(0,0,0,0.35)'), top: y + 18, side: null, width: 0 };
    }
    var magnify = g(v, 'dock.magnify', false);
    var items = [[0, 46, 1], [2, 46, 1], [5, 46, 1], [10, 46, 1], [3, 38, 0.85], [7, 38, 0.85]], n = items.length, gap = 12, divider = 16;
    var sc = magnifyScales(v, n, 1);
    var along = 0; items.forEach(function (it, i) { along += it[1] + (i < n - 1 ? (i === 3 ? divider : gap) : 0); });
    var thick = 66, pad = 14, len = along + 2 * pad;
    // the bar: horizontal at the bottom, or a column at a side edge in landscape
    var bx, by, bw, bh;
    if (vertical) { bw = thick; bh = len; bx = edge === 'Left' ? 10 : W - 10 - thick; by = (H - len) / 2; }
    else { bw = len; bh = thick; bx = (W - len) / 2; by = y - 4; }
    var defs = '<radialGradient id="dockhalo"><stop offset="0" stop-color="#000" stop-opacity="0.45"/><stop offset="1" stop-color="#000" stop-opacity="0"/></radialGradient>' +
      '<linearGradient id="slab" x1="0" y1="0" x2="' + (vertical ? 1 : 0) + '" y2="' + (vertical ? 0 : 1) + '"><stop offset="0" stop-color="' + (T.dark ? '#3A3A46' : '#FFFFFF') + '"/><stop offset="1" stop-color="' + (T.dark ? '#1B1B22' : '#CFCFD8') + '"/></linearGradient>';
    out += '<defs>' + defs + '</defs>';
    if (style === '3d') {
      for (var k = 6; k >= 1; k--) out += rect(bx - k, by + 4 + k * 1.4, bw + 2 * k, bh, 24 + k, 'rgba(0,0,0,' + f1(0.05 * (7 - k)) / 1 + ')');
      out += rect(bx, by, bw, bh, 24, 'url(#slab)', 'stroke="' + (T.dark ? 'rgba(255,255,255,0.35)' : 'rgba(255,255,255,0.9)') + '"') + (vertical ? '' : rect(bx + 12, by + 4, bw - 24, 3, 1.5, 'rgba(255,255,255,0.35)'));
    // W60: a capsule (radius = half the bar's own thickness, not a fixed value), matching the app's percent-corner shape.
    } else if (style !== 'transparent') out += rect(bx, by, bw, bh, Math.min(bw, bh) / 2, T.panel, 'stroke="' + T.hair + '"');
    // the icons, grown under the sample pointer; they grow away from the edge the dock sits on, and the row spreads to make room
    var extra = 0; items.forEach(function (it, i) { extra += it[1] * (sc[i] - 1); });
    // the grown row stays on the screen: when it would not fit, the whole row is drawn a little smaller (the bar keeps its place)
    var room = (vertical ? H : W) - 16, fit = Math.min(1, room / (along + extra)), grow = 0;
    var pos = Math.max(8, (vertical ? by : bx) + pad - extra * fit / 2 - (along * (1 - fit)) / 2), raise = style === '3d' ? 5 : 0, pointer = null;
    items.forEach(function (it, i) {
      var size = it[1] * sc[i] * fit, ix, iy; grow = Math.max(grow, size - it[1]);
      if (vertical) { iy = pos; ix = edge === 'Left' ? bx + (thick - it[1]) / 2 : bx + thick - (thick - it[1]) / 2 - size; }
      else { ix = pos; iy = by + 4 + (it[1] === 46 ? 3 : 9) + it[1] - size - raise; }
      if (style === 'transparent') out += circle(ix + size / 2, iy + size / 2, size * 0.62, 'url(#dockhalo)');
      if (style === '3d' && !vertical) out += '<ellipse cx="' + f1(ix + size / 2) + '" cy="' + f1(by + 4 + it[1] + 7) + '" rx="' + f1(size * 0.4) + '" ry="3" fill="rgba(0,0,0,0.35)"/>';
      var ic = appIcon(v, ix, iy, size, it[0], T);
      out += it[2] < 1 ? '<g opacity="' + it[2] + '">' + ic + '</g>' : ic;
      if (i === 1) pointer = vertical ? [edge === 'Left' ? ix + size + 10 : ix - 10, iy + size / 2] : [ix + size / 2, iy - 10];
      pos += size + (i < n - 1 ? (i === 3 ? divider : gap) * fit : 0);
      if (i === 3) {
        var dp = pos - divider / 2 - 2;
        out += vertical ? line(bx + 8, dp, bx + thick - 8, dp, T.hair, 1) : line(dp, by + 12, dp, by + 50, T.hair, 1);
      }
    });
    if (magnify && pointer) out += '<g class="magnify-sample">' + circle(pointer[0], pointer[1], 6, 'none', 'stroke="' + ink + '" stroke-width="1.5" stroke-opacity="0.85"') + circle(pointer[0], pointer[1], 2, ink, 'fill-opacity="0.85"') + '</g>';
    if (g(v, 'dock.groupShelf', true) && !vertical) {
      var cx0 = W / 2 - 66;
      var cy0 = by - 26 - Math.max(0, grow);
      ['Work', 'Play', 'Trip'].forEach(function (nm, i) { out += rect(cx0 + i * 46, cy0, 40, 18, 9, T.dark ? 'rgba(255,255,255,0.14)' : 'rgba(0,0,0,0.10)') + text(cx0 + i * 46 + 20, cy0 + 12.5, nm, 9, ink, 'text-anchor="middle"'); });
    }
    if (vertical) return { svg: out, top: H, side: edge, width: thick + 16 };
    return { svg: out, top: by - 30 - Math.max(0, grow), side: null, width: 0 };
  }

  // ----------------------------------------------------------------------------------------------------- chrome pieces
  function statusBar(v, T, ink) {
    if (g(v, 'hideStatusBar', true)) return '';
    return text(18, 17, '9:41', 11, ink, 'font-weight="600"') + rect(W - 44, 8, 18, 8, 2, 'none', 'stroke="' + ink + '" stroke-width="1"') + rect(W - 42, 10, 12, 4, 1, ink) + circle(W - 54, 12, 3, ink);
  }
  function musicLine(v, T, ink) {
    if (!g(v, 'showMusic', true)) return '';
    var top = g(v, 'hideStatusBar', true) ? 12 : 26, sz = g(v, 'cornerIconSize', 32), style = g(v, 'cornerStyle', 'Glyph');
    var reserve = style === 'Quarter' ? sz * 1.95 : sz + 28, full = g(v, 'musicLineWidth', 'FitAroundCorners') === 'FullWidth';
    // Wave 59 (glanceTop): only a top corner that DRAWS something (on and holding an assignment) keeps the line clear; Full width always runs edge to edge.
    var l = !full && cornerDrawn(v, 'TopLeft') ? reserve : 12, r = !full && cornerDrawn(v, 'TopRight') ? W - reserve : W - 12;
    var clip = uid('ml'), mask = uid('mf'), fade = 14;
    // The edge fade is an alpha mask on the text (never a colour painted over it), so any wallpaper shows through.
    return '<clipPath id="' + clip + '"><rect x="' + l + '" y="' + top + '" width="' + (r - l) + '" height="26"/></clipPath>' +
      '<linearGradient id="' + mask + 'g" gradientUnits="userSpaceOnUse" x1="' + l + '" x2="' + r + '" y1="0" y2="0"><stop offset="0" stop-color="#fff" stop-opacity="1"/>' +
      '<stop offset="' + f1((r - l - fade) / (r - l) * 100) / 100 + '" stop-color="#fff" stop-opacity="1"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>' +
      '<mask id="' + mask + '"><rect x="' + l + '" y="' + top + '" width="' + (r - l) + '" height="26" fill="url(#' + mask + 'g)"/></mask>' +
      '<g class="musicline" data-left="' + f1(l) + '" data-right="' + f1(r) + '" clip-path="url(#' + clip + ')" mask="url(#' + mask + ')">' +
      glyph('Note', l + 10, top + 14, 15, ink, 1.8) + text(l + 24, top + 18, 'Midnight City  -  M83  -  Hurry Up, We\'re Dreaming', 12, ink, 'opacity="0.9"') + '</g>';
  }
  function surfaceIndicator(v, T, ink, y) {
    if (!g(v, 'glanceIndicator', true)) return '';
    var n = 2 + (g(v, 'rightSide', 'RecentsPanel') !== 'Off' ? 1 : 0) + (g(v, 'leftPage', 'Launchpad') !== 'Off' ? 1 : 0), s = '', x0 = W / 2 - (n - 1) * 7;
    for (var i = 0; i < n; i++) s += circle(x0 + i * 14, y, i === 1 ? 3.2 : 2.2, ink, 'opacity="' + (i === 1 ? 0.95 : 0.4) + '"');
    return s;
  }
  function launchpadHandle(v, T, ink) {
    if (g(v, 'leftPage', 'Launchpad') === 'Off') return '';
    var rtl = g(v, 'homeOrder', 'LeftToRight') === 'RightToLeft', x = rtl ? W - 4 : 0;
    return rect(x, H / 2 - 40, 4, 80, 2, ink, 'opacity="0.28"');
  }

  // -------------------------------------------------------------------------------------------------------- the scenes
  function glanceScene(v, T) {
    var wp = wallpaper(v, 'glance', T), ink = wp.ink, s = wp.body;
    s += calmLayer(v, T, ink);
    s += statusBar(v, T, ink) + musicLine(v, T, ink);
    if (g(v, 'glanceEnabled', true)) s += clockLayer(v, T, ink);
    else s += text(180, 300, 'Glance is off: home opens on the first page', 12, ink, 'text-anchor="middle" opacity="0.7"');
    var d = dockLayer(v, T, ink, H - 108);
    s += d.svg + cornersLayer(v, T, ink) + surfaceIndicator(v, T, ink, H - 14) + launchpadHandle(v, T, ink);
    return { defs: wp.defs, body: s };
  }

  function pagesScene(v, T) {
    var wp = wallpaper(v, 'pages', T), ink = wp.ink, s = wp.body;
    var rtl = g(v, 'homeOrder', 'LeftToRight') === 'RightToLeft', mode = g(v, 'homePages', 'Off');
    var topY = g(v, 'hideStatusBar', true) ? 50 : 64, d = dockLayer(v, T, ink, H - 108), bottomY = (dockOn(v) ? d.top : H - 70) - 6;
    var inX = clamp(g(v, 'gridInsetX', 0), 0, 0.25) * W, inT = clamp(g(v, 'gridInsetTop', 0), 0, 0.25) * H, inB = clamp(g(v, 'gridInsetBottom', 0), 0, 0.25) * H;
    if (d.side) bottomY = H - 30; // a side dock in landscape leaves the bottom free and takes a strip at its edge
    var left = inX + 8 + (d.side === 'Left' ? d.width : 0), availW = W - 2 * inX - 16 - (d.side ? d.width : 0), top = topY + inT, availH = Math.max(LAND ? 80 : 120, bottomY - top - inB);
    var labels = g(v, 'labels', false), ls = g(v, 'labelScale', 1), labelH = labels ? 14 * ls : 0;
    var cols, rows, horizontalCont = false, cont = mode === 'Continuous';
    if (cont) {
      var ax = g(v, 'scrollAxis', 'Long'); horizontalCont = ax === 'Horizontal' || ax === (LAND ? 'Long' : 'Short'); // the long side is vertical in portrait, across in landscape
      cols = horizontalCont ? 5 : g(v, 'contCols', 4); rows = horizontalCont ? g(v, 'contCols', 4) : 7;
    } else if (g(v, 'adaptiveGrid', false)) { var dn = g(v, 'gridDensity', 1); cols = Math.max(2, Math.round(availW / (76 / dn))); rows = Math.max(1, Math.round(availH / ((84 + labelH) / dn))); }
    else if (LAND) { cols = g(v, 'lCols', 5); rows = g(v, 'lRows', 2); }
    else { cols = g(v, 'pCols', 3); rows = g(v, 'pRows', 6); }
    var sp = clamp(g(v, 'iconSpacing', 0), 0, 1), spV = g(v, 'spacingV', -1) < 0 ? sp : clamp(g(v, 'spacingV', -1), 0, 1);
    var pitchC = Math.min(availW / cols, availH / rows), pitchX = lerp(pitchC, availW / cols, sp), pitchY = lerp(pitchC, availH / rows, spV);
    var gridW = pitchX * cols, gridH = pitchY * rows;
    if (cont && !horizontalCont) { rows = Math.ceil(availH / pitchY) + 1; gridH = pitchY * rows; }
    var bias = clamp(g(v, 'gridBias', 0.4), 0, 1), spare = Math.max(0, availH - gridH), gx0 = left + Math.max(0, (availW - gridW) / 2), gy0 = top + (cont ? 0 : spare * bias);
    var scale = g(v, 'iconScale', 0.82), icon = Math.min(pitchX, pitchY - labelH) * scale;
    var clipId = uid('pg'); s += '<clipPath id="' + clipId + '"><rect x="0" y="' + f1(topY - 4) + '" width="' + W + '" height="' + f1(bottomY - topY + 10) + '"/></clipPath><g clip-path="url(#' + clipId + ')">';
    var count = 0, tile = !cont && cols >= 3;
    var order = [];
    if (cont && horizontalCont) { for (var c0 = 0; c0 < cols; c0++) for (var r0 = 0; r0 < rows; r0++) order.push([c0, r0]); }
    else for (var r1 = 0; r1 < rows; r1++) for (var c1 = 0; c1 < cols; c1++) order.push([c1, r1]);
    order.forEach(function (cr) {
      var c = cr[0], r = cr[1]; if (tile && r === 0 && c < 2) return;
      var cc = rtl ? cols - 1 - c : c, x = gx0 + cc * pitchX + (pitchX - icon) / 2, y = gy0 + r * pitchY + (pitchY - labelH - icon) / 2;
      if (cont && horizontalCont && x > W - 6) return;
      s += appIcon(v, x, y, icon, count, T);
      if (labels) s += text(x + icon / 2, y + icon + 11 * ls, APPS[count % APPS.length], 9.5 * ls, ink, 'text-anchor="middle" opacity="0.9"');
      count++;
    });
    if (tile) { // the clock tile
      var tx = rtl ? gx0 + (cols - 2) * pitchX : gx0, ty = gy0, tw = 2 * pitchX - 8, th = pitchY - 8, an = g(v, 'clockTileAnalog', false), cp = (function () { var f = g(v, 'clockTileFormat', 'System'); return f === 'Hour12' ? '2:09' : '14:09'; })();
      s += rect(tx + 4, ty + 4, tw, th, 16, T.dark ? 'rgba(20,20,26,0.7)' : 'rgba(255,255,255,0.75)', 'stroke="' + T.hair + '"');
      if (an) s += analog(tx + 4 + tw / 2, ty + 4 + th / 2 - (g(v, 'clockTileDate', true) ? 4 : 0), Math.min(th - 24, 70), 'AnalogClassic', T.dark ? '#F2F2F7' : '#14141A', css(T.accent));
      else s += text(tx + 4 + tw / 2, ty + 4 + th / 2 + (g(v, 'clockTileDate', true) ? 2 : 10), cp, Math.min(34, th * 0.4), T.dark ? '#F2F2F7' : '#14141A', 'text-anchor="middle" font-weight="300"');
      if (g(v, 'clockTileDate', true)) s += text(tx + 4 + tw / 2, ty + th - 6, 'Mon 23 Nov', 9, T.dark ? '#B9B9C4' : '#55555E', 'text-anchor="middle"');
    }
    if (cont) s += rect(0, bottomY - 40, W, 46, 0, 'url(#fadeP)');
    s += '</g>';
    var fadeDef = '<linearGradient id="fadeP" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity="0.35"/></linearGradient>';
    s += statusBar(v, T, ink) + musicLine(v, T, ink);
    // page indicator
    if (mode === 'Paginated') for (var i = 0; i < 4; i++) s += circle(W / 2 + (rtl ? 1 : -1) * (i - 1.5) * 12, bottomY + 4 + (dockOn(v) ? 0 : 0), i === 0 ? 3.4 : 2.4, ink, 'opacity="' + (i === 0 ? 0.95 : 0.4) + '"');
    s += d.svg + cornersLayer(v, T, ink, ['TopLeft', 'TopRight', 'BottomLeft', 'BottomRight']) + launchpadHandle(v, T, ink);
    return { defs: wp.defs + fadeDef, body: s };
  }

  function keyboard(T, y) {
    var s = rect(0, y, W, H - y, 0, T.dark ? '#14141B' : '#E6E6EE'), rowsK = [10, 9, 7], yy = y + 12;
    rowsK.forEach(function (n, r) { var kw = (W - 24) / 10, x0 = (W - n * kw) / 2; for (var i = 0; i < n; i++) s += rect(x0 + i * kw + 2, yy, kw - 4, 30, 5, T.dark ? '#2A2A35' : '#FFFFFF'); yy += 38; });
    return s + rect(60, yy, W - 120, 30, 5, T.dark ? '#2A2A35' : '#FFFFFF');
  }

  function radarScene(v, T) {
    var wp = wallpaper(v, 'glance', T), ink = T.dark ? '#F2F2F7' : '#14141A', s = '';
    var trueBlack = g(v, 'radarBackground', 'Dimmed') === 'TrueBlack';
    s += trueBlack ? rect(0, 0, W, H, 0, '#000') : wp.body + rect(0, 0, W, H, 0, '#000', 'opacity="0.62"');
    var sub = 'rgba(255,255,255,0.55)', card = 'rgba(255,255,255,0.09)', layout = g(v, 'searchLayout', 'Filmstrip');
    var kb = g(v, 'alwaysKeyboard', false) || g(v, 'kbOpenP', false), kbTop = kb ? 470 : H, intens = g(v, 'radarIntensity', 'Normal') === 'Subtle' ? 0.25 : 0.55;
    if (!trueBlack && g(v, 'wpGlanceKind', 'live') === 'live') s += '<g opacity="' + intens + '">' + circle(300, 120, 80, 'none', 'stroke="' + css(T.accent) + '" stroke-opacity="0.3"') + circle(300, 120, 130, 'none', 'stroke="' + css(T.accent) + '" stroke-opacity="0.18"') + '</g>';
    // search field
    var fy = 56; s += rect(16, fy, W - 32, 46, 23, 'rgba(255,255,255,0.12)', 'stroke="rgba(255,255,255,0.18)"') + glyph('Search', 40, fy + 23, 20, sub, 1.8) + text(60, fy + 28, 'Search apps, contacts, files', 14, sub);
    // scope chips
    var chips = ['Apps'].concat(g(v, 'searchContacts', true) ? ['People'] : [], g(v, 'searchFiles', true) ? ['Files'] : [], g(v, 'searchActions', true) ? ['Actions'] : [], g(v, 'searchWeb', true) ? [g(v, 'webEngine', 'DuckDuckGo')] : []);
    var cx = 16; chips.forEach(function (c, i) { var w = 22 + c.length * 6.3; s += rect(cx, fy + 58, w, 24, 12, i === 0 ? css(T.accent, 0.9) : card) + text(cx + w / 2, fy + 74, c, 11, i === 0 ? '#fff' : ink === '#14141A' ? '#F2F2F7' : '#F2F2F7', 'text-anchor="middle"'); cx += w + 8; });
    var y0 = fy + 112;
    if (layout === 'Filmstrip') {
      var xs = [-2, -1, 0, 1, 2], baseW = 120;
      xs.slice().sort(function (a, b) { return Math.abs(b) - Math.abs(a); }).forEach(function (k) {
        var sc = 1 - Math.abs(k) * 0.2, w = baseW * sc, h = 170 * sc, x = 180 + k * 100 * (1 - Math.abs(k) * 0.08) - w / 2, y = y0 + (170 - h) / 2 + 10;
        s += rect(x, y, w, h, 16, k === 0 ? 'rgba(255,255,255,0.18)' : card, 'stroke="rgba(255,255,255,' + (k === 0 ? 0.35 : 0.12) + ')"') + appIcon(v, x + w / 2 - 22 * sc, y + 22 * sc, 44 * sc, [2, 0, 5, 1, 7][k + 2], T) + text(x + w / 2, y + h - 20 * sc, ['Camera', 'Phone', 'Music', 'Messages', 'Clock'][k + 2], 12 * sc, '#F2F2F7', 'text-anchor="middle"');
      });
      y0 += 200;
    } else if (layout === 'Orbit') {
      var ocx = 180, ocy = y0 + 110; [48, 86, 124].forEach(function (r, i) { s += '<ellipse cx="' + ocx + '" cy="' + ocy + '" rx="' + (r + 40) + '" ry="' + r + '" fill="none" stroke="rgba(255,255,255,' + (0.2 - i * 0.04) + ')"/>'; });
      [[0, -48, 0], [150, -10, 5], [-150, 20, 2], [70, 90, 1], [-80, -80, 7], [20, 100, 9], [-30, 60, 11]].forEach(function (p, i) { s += appIcon(v, ocx + p[0] - 20, ocy + p[1] - 20, 40, p[2], T); });
      s += circle(ocx, ocy, 18, css(T.accent), 'opacity="0.9"') + glyph('Search', ocx, ocy, 18, '#fff', 2);
      y0 += 250;
    } else y0 += 4;
    // result rows
    var rowsN = layout === 'Flat' ? 7 : 3, rowH = 52;
    s += text(20, y0 + 4, layout === 'Flat' ? 'EVERYTHING' : 'TOP RESULTS', 10, sub, 'letter-spacing="1.5"');
    for (var i = 0; i < rowsN; i++) { var ry = y0 + 14 + i * rowH; if (ry + rowH > kbTop - 6) break; s += appIcon(v, 20, ry + 6, 38, [2, 0, 5, 1, 7, 3, 4][i], T) + text(70, ry + 22, ['Camera', 'Phone', 'Music', 'Messages', 'Photos', 'Maps', 'Mail'][i], 14, '#F2F2F7') + text(70, ry + 38, ['App', 'App', 'App · now playing', 'App', 'Files · 214 items', 'App', 'App'][i], 10.5, sub) + line(70, ry + rowH - 1, W - 16, ry + rowH - 1, 'rgba(255,255,255,0.1)', 1); }
    if (g(v, 'radarSignalForce', 'Live') !== 'Live') s += rect(W - 120, H - (kb ? 300 : 40), 104, 20, 10, 'rgba(255,200,80,0.25)') + text(W - 68, H - (kb ? 286 : 26), 'signal: ' + g(v, 'radarSignalForce', ''), 10, '#FFD166', 'text-anchor="middle"');
    if (kb) s += keyboard({ dark: true }, kbTop);
    return { defs: wp.defs, body: s };
  }

  function discScene(v, T) {
    var wp = wallpaper(v, 'glance', T), s = wp.body + rect(0, 0, W, H, 0, '#000', 'opacity="0.55"');
    var N = clamp(g(v, 'puckSegments', 8), 6, 8), slots = String(g(v, 'puckSlots', 'a:Flashlight\na:Clock\na:Notifications\na:DoNotDisturb\na:Search\na:Camera\na:Calculator\na:SystemSettings')).split('\n');
    var cx = 63, cy = H - 63, hub = 59, ringIn = hub, ringOut = 119, step = 360 / N, ink = '#F2F2F7';
    var grouping = g(v, 'wheelGrouping', 'Letter'), labels = g(v, 'puckLabels', false), work = g(v, 'wheelWorkOnly', false);
    // the group labels run round the ring
    var names = grouping === 'Letter' ? 'A B C D E F G H I J K L'.split(' ') : grouping === 'Category' ? ['Social', 'Media', 'Tools', 'Games', 'Work', 'Travel', 'Money'] : ['Now', 'Today', 'Week', 'Older'];
    var shown = names.slice(0, grouping === 'Letter' ? 9 : names.length), arcSpan = 100;
    shown.forEach(function (nm, i) {
      var ang = 5 + (arcSpan * i) / Math.max(1, shown.length - 1), a = ang * Math.PI / 180, rr = 150, tx = cx + Math.cos(a) * rr, ty = cy - Math.sin(a) * rr, sel = i === 2;
      if (sel) s += circle(tx, ty, 13, 'rgba(255,255,255,0.9)');
      s += text(tx, ty + 4, nm.length > 6 ? nm.slice(0, 6) : nm, grouping === 'Letter' ? 13 : 10, sel ? '#111' : ink, 'text-anchor="middle" font-weight="' + (sel ? 700 : 500) + '"');
    });
    // apps of the chosen group, fanned out
    for (var k = 0; k < 6; k++) { var a2 = (25 + k * 11) * Math.PI / 180, rad = 215 + (k % 2) * 40; s += appIcon(v, cx + Math.cos(a2) * rad - 22, cy - Math.sin(a2) * rad - 22, 44, k + 3, T); }
    // the ring
    var id = uid('ring'), clip = uid('cl');
    s += '<clipPath id="' + clip + '"><rect x="0" y="0" width="' + W + '" height="' + H + '"/></clipPath><g clip-path="url(#' + clip + ')">';
    s += '<circle cx="' + cx + '" cy="' + cy + '" r="' + ((ringIn + ringOut) / 2) + '" fill="none" stroke="rgba(255,255,255,0.92)" stroke-width="' + (ringOut - ringIn) + '"/>';
    for (var i = 0; i < N; i++) {
      var ang0 = 45 - step + i * step, a0 = (ang0 - step / 2) * Math.PI / 180, am = ang0 * Math.PI / 180;
      s += line(cx + Math.cos(a0) * ringIn, cy - Math.sin(a0) * ringIn, cx + Math.cos(a0) * ringOut, cy - Math.sin(a0) * ringOut, 'rgba(0,0,0,0.45)', 1.2);
      var gx = cx + Math.cos(am) * (ringIn + ringOut) / 2, gy = cy - Math.sin(am) * (ringIn + ringOut) / 2, sl = slots[i] || '-';
      if (gx > 14 && gy > 14 && gx < W - 14) {
        s += glyph(sl === '-' ? 'None' : sl.indexOf('p:') === 0 || sl === '~custom' ? 'AllApps' : glyphKey(sl), gx, gy - (labels ? 5 : 0), 24, '#14141A', 1.8);
        if (labels) s += text(gx, gy + 17, sl === '-' ? '' : sl.indexOf('a:') === 0 ? sl.slice(2).replace(/([a-z])([A-Z])/g, '$1 $2').slice(0, 9) : 'App', 8.5, '#14141A', 'text-anchor="middle" font-weight="600"');
      }
    }
    s += circle(cx, cy, hub, '#06060A', 'stroke="rgba(255,255,255,0.25)"') + glyph('Back', cx + 14, cy - 14, 26, '#fff', 2.2) + '</g>';
    s += text(W - 16, 34, work ? 'Work apps only' : 'All apps', 12, 'rgba(255,255,255,0.6)', 'text-anchor="end"');
    return { defs: wp.defs, body: s };
  }

  function commsScene(v, T) {
    var wp = wallpaper(v, 'glance', T), side = g(v, 'leftPage', 'Launchpad'), lpTheme = g(v, 'lpTheme', 'Dark'), dark = lpTheme === 'System' ? T.dark : lpTheme === 'Dark';
    var rtl = g(v, 'homeOrder', 'LeftToRight') === 'RightToLeft';
    var bg = dark ? '#0E0E14' : '#F5F5F9', ink = dark ? '#F2F2F7' : '#14141A', sub = dark ? '#9A9AA8' : '#6A6A75', card = dark ? '#1A1A23' : '#FFFFFF', hair = dark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.1)';
    var scale = clamp(g(v, 'commsScale', 100), 75, 125) / 100, s = wp.body + rect(0, 0, W, H, 0, '#000', 'opacity="0.35"');
    var sliver = 26, px = rtl ? sliver : 0, pw = W - sliver;
    s += rect(px, 0, pw, H, 0, side === 'Off' ? 'rgba(0,0,0,0.0)' : bg);
    if (side === 'Off') s += text(W / 2, 360, 'Nothing is left of home', 14, '#F2F2F7', 'text-anchor="middle"') + text(W / 2, 382, 'A swipe from this edge does nothing', 11, 'rgba(255,255,255,0.5)', 'text-anchor="middle"');
    else if (side === 'Widget') s += rect(px + 16, 60, pw - 32, H - 120, 20, card, 'stroke="' + hair + '" stroke-dasharray="5 4"') + glyph('Assistant', px + pw / 2, 330, 40, sub, 1.4) + text(px + pw / 2, 380, 'A widget of your choice', 13, sub, 'text-anchor="middle"') + text(px + pw / 2, 398, '(placeholder)', 10, sub, 'text-anchor="middle"');
    else {
      var rail = g(v, 'launchpadRail', true), x0 = px + 16 + (rail ? 30 : 0), cw = pw - 32 - (rail ? 30 : 0), fs = 14 * scale;
      s += text(px + 16, 44, 'COMMS', 12, sub, 'letter-spacing="2"');
      if (rail) [['M', 3, 0xFF4F8CFF], ['A', 12, 0xFFFF6B6B], ['J', 1, 0xFF3DDC84], ['W', 7, 0xFFFFB347]].forEach(function (u, i) { var y = 76 + i * 40; s += circle(px + 22, y, 12, css(u[2])) + text(px + 22, y + 4, u[0], 11, '#fff', 'text-anchor="middle" font-weight="600"') + circle(px + 32, y - 9, 6, '#FF4D4D') + text(px + 32, y - 6, String(u[1]), 7.5, '#fff', 'text-anchor="middle"'); });
      // pinned block
      s += text(x0, 78, 'PINNED', 10, sub, 'letter-spacing="1.5"');
      [0, 1, 2, 3].forEach(function (i) { var cxp = x0 + 20 + i * (cw / 4); s += circle(cxp, 108, 19, css(APP_COLOURS[(i * 3) % 12])) + text(cxp, 113, ['M', 'A', 'J', 'W'][i], 14, '#fff', 'text-anchor="middle" font-weight="600"') + text(cxp, 142, ['Mum', 'Ana', 'Jo', 'Work'][i], 9.5, sub, 'text-anchor="middle"'); });
      // categories
      var cats = ['All', 'People', 'Work', 'Promos'], cxx = x0;
      cats.forEach(function (c, i) { var w = 24 + c.length * 7; s += rect(cxx, 162, w, 26, 13, i === 0 ? css(T.accent) : card, i === 0 ? '' : 'stroke="' + hair + '"') + text(cxx + w / 2, 179, c, 11.5, i === 0 ? '#fff' : ink, 'text-anchor="middle"'); cxx += w + 8; });
      // rows
      var rows = [['Ana', 'Are we still on for 6?', 'now', 2], ['Standup', 'Moved to 10:30', '9m', 1], ['Mum', 'Call me when you can', '1h', 3], ['Bank', 'New sign-in noticed', '2h', 1], ['Jo', 'Photos from Sunday', '3h', 4], ['Shop', 'Your parcel is out', '5h', 1]];
      var y = 210, rh = 56 * scale;
      rows.forEach(function (r, i) { if (y + rh > H - 40) return; s += circle(x0 + 18, y + rh / 2, 17 * Math.min(scale, 1.1), css(APP_COLOURS[(i * 2 + 1) % 12])) + text(x0 + 46, y + rh / 2 - 3, r[0], fs, ink, 'font-weight="600"') + text(x0 + 46, y + rh / 2 + 14 * scale, r[1], 11.5 * scale, sub) + text(x0 + cw, y + rh / 2 - 3, r[2], 10, sub, 'text-anchor="end"') + line(x0 + 46, y + rh, x0 + cw, y + rh, hair, 1); y += rh; });
    }
    // the home card peeks out at the other edge
    var cardX = rtl ? 0 : W - sliver;
    s += rect(rtl ? -14 : W - sliver, 20, sliver + 14, H - 40, 22, '#000', 'opacity="0.9"') + rect(rtl ? -14 : W - sliver, 20, sliver + 14, H - 40, 22, 'none', 'stroke="rgba(255,255,255,0.3)"');
    return { defs: wp.defs, body: s + (cardX < 0 ? '' : '') };
  }

  function recentsScene(v, T) {
    var wp = wallpaper(v, 'glance', T), side = g(v, 'rightSide', 'RecentsPanel'), rtl = g(v, 'homeOrder', 'LeftToRight') === 'RightToLeft';
    var s = wp.body + rect(0, 0, W, H, 0, '#000', 'opacity="0.5"');
    // the home, shrunk to a card on the other side
    var cw = 112, ch = 236, cx = rtl ? W - cw - 14 : 14, cy = 60;
    if (side === 'DockRail') {
      var x = rtl ? 8 : W - 70;
      s += rect(x, 120, 62, 400, 26, T.panel, 'stroke="' + T.hair + '"');
      for (var i = 0; i < 6; i++) s += appIcon(v, x + 8, 134 + i * 62, 46, [0, 2, 5, 10, 3, 7][i], T);
      s += text(W / 2, 330, 'The right-edge swipe reveals the dock rail', 12, 'rgba(255,255,255,0.7)', 'text-anchor="middle"') + text(W / 2, 348, g(v, 'dock.enabled', 'auto') === 'off' ? '(the dock is off: nothing is shown)' : '', 10, 'rgba(255,255,255,0.45)', 'text-anchor="middle"');
    } else if (side === 'Off') {
      s += text(W / 2, 360, 'Nothing on the right side', 14, '#F2F2F7', 'text-anchor="middle"');
    } else {
      s += rect(cx, cy, cw, ch, 18, wp.base ? css(wp.base) : '#222', 'stroke="rgba(255,255,255,0.3)"') + text(cx + cw / 2, cy + 40, '14:09', 20, wp.ink, 'text-anchor="middle" font-weight="300"');
      for (var j = 0; j < 8; j++) s += appIcon(v, cx + 12 + (j % 3) * 32, cy + 80 + Math.floor(j / 3) * 40, 26, j, T);
      var lim = clamp(g(v, 'recentsLimit', 24), 8, 48), tx = rtl ? 14 : cw + 36, tw = W - tx - (rtl ? cw + 36 : 14);
      s += text(tx, 80, 'RECENT', 11, 'rgba(255,255,255,0.55)', 'letter-spacing="2"') + text(tx + tw, 80, 'up to ' + lim, 10, 'rgba(255,255,255,0.4)', 'text-anchor="end"');
      var colsR = 2, pw = tw / colsR;
      for (var k = 0; k < 10; k++) { var gx = tx + (k % colsR) * pw, gy = 100 + Math.floor(k / colsR) * 78; s += appIcon(v, gx + pw / 2 - 22, gy, 44, k + 1, T) + text(gx + pw / 2, gy + 60, APPS[(k + 1) % APPS.length], 10, '#F2F2F7', 'text-anchor="middle"'); }
      if (g(v, 'recentsUsage', true)) s += text(W / 2, H - 28, 'Apps opened elsewhere are added', 10, 'rgba(255,255,255,0.45)', 'text-anchor="middle"');
    }
    return { defs: wp.defs, body: s };
  }

  var SCENE_FN = { glance: glanceScene, pages: pagesScene, radar: radarScene, disc: discScene, comms: commsScene, recents: recentsScene };

  /**
   * The phone as SVG text for a scene. [values] is a configuration (schema key -> value; missing keys read as the defaults of the code that the
   * drawing hard-codes, which are the same as the schema defaults).
   */
  /** Scenes drawn in landscape when asked (opts.landscape): the home surfaces, where the side dock and the landscape clock place live. */
  var LANDSCAPE_SCENES = { glance: true, pages: true };
  function render(scene, values, opts) {
    var id = SCENE_ALIAS[scene] || scene, land = !!(opts && opts.landscape) && !!LANDSCAPE_SCENES[id];
    LAND = land; W = land ? PH : PW; H = land ? PW : PH;
    try {
      var v = values || {}, T = theme(v), sc = SCENE_FN[id] || glanceScene, r = sc(v, T), font = fontOf(v), cid = uid('scr');
      var fw = W + 20, fh = H + 20;
      return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + fw + ' ' + fh + '" role="img" aria-label="Phone preview: ' + esc(scene) + (land ? ', landscape' : '') + '" font-family="' + esc(font) + '" data-landscape="' + land + '">' +
        '<defs><clipPath id="' + cid + '"><rect x="10" y="10" width="' + W + '" height="' + H + '" rx="34"/></clipPath>' + r.defs + '</defs>' +
        '<rect x="0" y="0" width="' + fw + '" height="' + fh + '" rx="44" fill="#16161B"/><rect x="2" y="2" width="' + (fw - 4) + '" height="' + (fh - 4) + '" rx="42" fill="none" stroke="rgba(255,255,255,0.22)" stroke-width="1.5"/>' +
        '<g clip-path="url(#' + cid + ')"><g class="screen" transform="translate(10 10)">' + r.body + '</g></g>' +
        (land ? '<rect x="16" y="' + (fh / 2 - 28) + '" width="6" height="56" rx="3" fill="rgba(0,0,0,0.55)"/>' : '<rect x="162" y="16" width="56" height="6" rx="3" fill="rgba(0,0,0,0.55)"/>') + '</svg>';
    } finally { LAND = false; W = PW; H = PH; }
  }

  /**
   * Wave 58 (CFG-72): the clock dragged on the Glance. The box (in dp of the drawn screen) is kept inside the screen and turned into the two
   * placement fields the app's edit mode writes (centre across, top down, each 0 to 1, to the hundredth).
   */
  function clockFromBox(left, top, w, h, areaW, areaH) {
    var l = clamp(left, 0, Math.max(0, areaW - w)), t = clamp(top, 0, Math.max(0, areaH - h));
    return { x: Math.round(clamp((l + w / 2) / areaW, 0, 1) * 100) / 100, y: Math.round(clamp(t / areaH, 0, 1) * 100) / 100 };
  }

  /** The scene a field wants (aliases resolved), or the tab's own scene. */
  var TAB_SCENE = { home: 'glance', look: 'glance', apps: 'disc', radar: 'radar', side: 'comms', system: 'glance' };
  function sceneFor(field, tabId) { var s = field && field.scene; return SCENE_ALIAS[s] || s || TAB_SCENE[tabId] || 'glance'; }

  return {
    SCENES: SCENES, FONTS: FONTS, TAB_SCENE: TAB_SCENE, W: W, H: H,
    render: render, sceneFor: sceneFor, clockFromBox: clockFromBox, magnifyScales: magnifyScales, LANDSCAPE_SCENES: LANDSCAPE_SCENES,
    helpers: { glyph: glyph, glyphKey: glyphKey, GLYPHS: GLYPHS, appIcon: appIcon, theme: theme, css: css, shapePath: shapePath, closeShape: closeShape, rect: rect, circle: circle, line: line, text: text, analog: analog, f1: f1, esc: esc, lum: lum, mixc: mixc, g: g, clamp: clamp, APP_COLOURS: APP_COLOURS, regularIndicators: regularIndicators },
  };
});
