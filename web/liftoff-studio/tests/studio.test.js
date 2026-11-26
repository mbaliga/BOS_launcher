// Run: node --test web/liftoff-studio/tests/   (Node 18 or newer, no dependencies)
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Core = require('../core.js');
const Prev = require('../preview.js');
const Cards = require('../cards.js');

const dir = path.join(__dirname, '..');
const schema = JSON.parse(fs.readFileSync(path.join(dir, 'schema.json'), 'utf8'));
const ix = Core.indexSchema(schema);
const D = Core.defaults(ix);
const sample = JSON.parse(fs.readFileSync(path.join(__dirname, 'sample-settings.json'), 'utf8'));
const sampleState = JSON.parse(fs.readFileSync(path.join(__dirname, 'sample-backup.json'), 'utf8'));

test('schema.js carries exactly the same document as schema.json', () => {
  const js = fs.readFileSync(path.join(dir, 'schema.js'), 'utf8');
  const sandbox = { window: {} };
  new Function('window', js)(sandbox.window);
  assert.deepEqual(sandbox.window.LIFTOFF_SCHEMA, schema);
});

test('six tabs, every field on a known tab, no duplicate keys', () => {
  assert.deepEqual(schema.tabs.map((t) => t.label), ['HOME', 'LOOK', 'APPS', 'RADAR', 'SIDE PANEL', 'SYSTEM']);
  const ids = new Set(schema.tabs.map((t) => t.id));
  const seen = new Set();
  for (const f of schema.fields) { assert.ok(ids.has(f.tab), f.key); assert.ok(!seen.has(f.key), 'duplicate ' + f.key); seen.add(f.key); }
});

test('every default passes its own check, every enum default is an option', () => {
  for (const f of Core.editable(ix)) {
    const r = Core.checkValue(f, f.default);
    assert.ok(!r.error, f.key + ': ' + r.error);
    assert.equal(r.warn, undefined, f.key + ' default warns: ' + r.warn);
  }
});

test('the app\'s own settings file reads without a warning and exports back to the same prefs', () => {
  const r = Core.importDocument(ix, sample);
  assert.ok(r.ok, JSON.stringify(r.errors));
  assert.deepEqual(r.warnings, []);
  assert.equal(r.values.themeMode, 'Amoled');
  assert.equal(r.values.glanceHotspots, 'BottomRight,BottomLeft');
  assert.equal(r.values.accent, 0xFF39FF88);
  const out = Core.exportSettings(ix, { name: 'x', values: r.values, extras: r.extras });
  assert.equal(out.format, 'liftoff-settings');
  assert.equal(out.settingsVersion, schema.settingsVersion);
  // every key of the sample is in the export with the same value (device-bound ones are the one allowed difference)
  for (const [k, v] of Object.entries(sample.prefs)) {
    if (ix.byKey[k] && ix.byKey[k].deviceBound) { assert.ok(!(k in out.prefs), k + ' must not be exported'); continue; }
    assert.deepEqual(out.prefs[k], v, k);
  }
});

test('round trip: import(export(x)) equals x for a profile with many changes', () => {
  const values = Object.assign({}, D, { themeMode: 'Light', accent: 0xFF112233, clockStyle: 'AnalogMinimal', glanceClockX: 0.35, 'dock.style': '3d', 'dock.enabled': 'off', 'corners.TopLeft': 'action:Flashlight',
    'gestures.PinchOut': 'action:Search', calmStyle: 'Beam', commsScale: 115, hapticStrength: 'strong', puckSegments: 6, uiFont: 'lexend' });
  const p = { name: 'rt', values, extras: Core.newExtras() };
  for (const kind of ['settings', 'state']) {
    const doc = kind === 'state' ? Core.exportState(ix, p, 1) : Core.exportSettings(ix, p);
    const back = Core.importDocument(ix, JSON.parse(JSON.stringify(doc)));
    assert.ok(back.ok, JSON.stringify(back.errors));
    for (const f of Core.editable(ix)) {
      const inFile = kind === 'state' || f.scope === 'settings';
      assert.deepEqual(back.values[f.key], inFile ? values[f.key] : f.default, kind + ' ' + f.key);
    }
  }
});

test('calmStyle left on Automatic is not written, a chosen one is', () => {
  const a = Core.exportSettings(ix, { name: 'a', values: D, extras: Core.newExtras() });
  assert.ok(!('calmStyle' in a.prefs));
  const b = Core.exportSettings(ix, { name: 'b', values: Object.assign({}, D, { calmStyle: 'Regular' }), extras: Core.newExtras() });
  assert.equal(b.prefs.calmStyle, 'Regular');
});

test('unknown fields are kept: prefs, settings document fields and a whole Backup', () => {
  const doc = JSON.parse(JSON.stringify(sample));
  doc.prefs.futureThing = { a: [1, 2] }; doc.prefs.homeLanding = 'Minimal'; doc.extraTop = 'kept';
  const r = Core.importDocument(ix, doc);
  assert.ok(r.ok);
  const out = Core.exportSettings(ix, { name: 'u', values: r.values, extras: r.extras });
  assert.deepEqual(out.prefs.futureThing, { a: [1, 2] });
  assert.equal(out.prefs.homeLanding, 'Minimal');
  assert.equal(out.extraTop, 'kept');
  const st = Core.importDocument(ix, sampleState);
  assert.ok(st.ok, JSON.stringify(st.errors));
  const back = Core.exportState(ix, { name: 's', values: st.values, extras: st.extras }, 5);
  for (const k of ['layout', 'workspaces', 'sessions', 'widgets', 'launchpad', 'paletteAliases', 'futureSection']) assert.deepEqual(back[k], sampleState[k], k);
  assert.deepEqual(back.shortcuts.corners.TopRight, sampleState.shortcuts.corners.TopRight, 'a corner holding an app is kept');
  assert.equal(back.dock.groups.length, sampleState.dock.groups.length);
  assert.equal(back.dock.activeId, sampleState.dock.activeId);
  assert.equal(back.settings.prefs.futureInSettings, 1);
});

test('a Backup is read for the dock, corners, gestures and files', () => {
  const r = Core.importDocument(ix, sampleState);
  assert.equal(r.kind, 'state');
  assert.equal(r.values['dock.style'], 'transparent');
  assert.equal(r.values['dock.enabled'], 'auto');
  assert.equal(r.values['corners.TopLeft'], 'action:Phone');
  assert.equal(r.values['corners.TopRight'], Core.CUSTOM);
  assert.equal(r.values['gestures.DoubleTap'], '');
  assert.equal(r.values['gestures.Shake'], Core.CUSTOM);
  assert.equal(r.values.commsScale, 110);
  assert.equal(r.values.puckSegments, 7);
  assert.match(r.values.puckSlots, /^a:Flashlight\n/);
});

test('wrong types are refused with a readable message that names the setting', () => {
  const bad = { format: 'liftoff-settings', settingsVersion: 2, prefs: { themeMode: 5, labels: 'yes', iconScale: 'big', accent: 1.5, glanceHotspots: [1] } };
  const r = Core.importDocument(ix, bad);
  assert.equal(r.ok, false);
  assert.equal(r.errors.length, 5);
  assert.match(r.errors.join('\n'), /Theme \(themeMode\) must be one of its names/);
  assert.match(r.errors.join('\n'), /App names \(labels\) must be on or off/);
  assert.match(r.errors.join('\n'), /Icon size \(iconScale\) must be a number/);
});

test('files that are not Liftoff files are refused clearly', () => {
  assert.match(Core.importDocument(ix, '').errors[0], /empty/);
  assert.match(Core.importDocument(ix, '{oops').errors[0], /Not a JSON file/);
  assert.match(Core.importDocument(ix, '[]').errors[0], /top level must be an object/);
  assert.match(Core.importDocument(ix, '{"format":"clackpad_config"}').errors[0], /Not a Liftoff file/);
  assert.match(Core.importDocument(ix, { format: 'liftoff-state', settings: {} }).errors[0], /no schema version/);
  assert.equal(Core.importDocument(ix, { format: 'liftoff-settings', prefs: [] }).ok, false);
});

test('out-of-range numbers are clamped with a warning, unknown option names are kept with a warning', () => {
  const r = Core.importDocument(ix, { format: 'liftoff-settings', settingsVersion: 2, prefs: { iconScale: 3, pCols: 99, iconShape: 'Hexagon' } });
  assert.ok(r.ok);
  assert.equal(r.values.iconScale, 0.95);
  assert.equal(r.values.pCols, 10);
  assert.equal(r.values.iconShape, 'Hexagon');
  assert.equal(r.warnings.length, 3);
  assert.equal(Core.exportSettings(ix, { name: 'w', values: r.values, extras: r.extras }).prefs.iconShape, 'Hexagon');
});

test('device-bound keys never reach an export and an import drops them with a note', () => {
  const r = Core.importDocument(ix, { format: 'liftoff-settings', settingsVersion: 2, prefs: { onboardingDone: true, leftWidgetId: 7, wpGlancePic: 'x.png', themeMode: 'Dark' } });
  assert.ok(r.ok);
  assert.equal(r.warnings.length, 3);
  const out = Core.exportSettings(ix, { name: 'b', values: r.values, extras: r.extras });
  for (const k of ['onboardingDone', 'leftWidgetId', 'wpGlancePic']) assert.ok(!(k in out.prefs), k);
});

test('the exported prefs hold exactly the settings-scope keys of the schema (plus none unknown)', () => {
  const out = Core.exportSettings(ix, { name: 'e', values: D, extras: Core.newExtras() });
  const want = schema.fields.filter((f) => f.scope === 'settings' && !f.deviceBound && f.key !== 'calmStyle').map((f) => f.key).sort();
  assert.deepEqual(Object.keys(out.prefs).sort(), want);
});

test('a partial Backup carries only the sections Studio manages', () => {
  const doc = Core.exportState(ix, { name: 'p', values: D, extras: Core.newExtras() }, 42);
  assert.equal(doc.format, 'liftoff-state');
  assert.equal(doc.schemaVersion, schema.stateSchemaVersion);
  assert.deepEqual(Object.keys(doc).sort(), ['app', 'dock', 'exportedAt', 'format', 'prefFiles', 'schemaVersion', 'settings', 'shortcuts']);
  assert.equal(doc.dock.settings.enabled, null);
  assert.deepEqual(doc.shortcuts.corners.TopLeft, { t: 'single', i: 'action:Camera' });
  assert.deepEqual(doc.prefFiles.comms_scale.pct, { t: 'i', v: 100 });
  assert.deepEqual(doc.prefFiles.launcher_haptics.strength, { t: 's', v: 'medium' });
});

test('share link: round trip, only differences, size bound, damaged and wrong-type links refused', () => {
  const values = Object.assign({}, D, { themeMode: 'Light', 'dock.style': '3d', accent: 0xFF010203 });
  const frag = Core.shareFragment(ix, { name: 'Désk ☾', values });
  assert.ok(frag.ok); assert.ok(frag.fragment.length < 400);
  const back = Core.readShare(ix, 'https://example.org/liftoff/studio/#' + frag.fragment);
  assert.ok(back.ok, JSON.stringify(back.errors));
  assert.equal(back.name, 'Désk ☾');
  assert.deepEqual(back.values, values);
  assert.equal(Core.readShare(ix, '#p=!!!').ok, false);
  assert.equal(Core.readShare(ix, 'hello world').ok, false);
  const evil = Buffer.from(JSON.stringify({ v: 1, n: 'x', d: { themeMode: 7 } })).toString('base64url');
  assert.match(Core.readShare(ix, '#p=' + evil).errors[0], /themeMode/);
  const huge = Object.assign({}, D, { puckSlots: 'a:Flashlight\n'.repeat(2) + 'x'.repeat(9000) });
  assert.equal(Core.shareFragment(ix, { name: 'big', values: huge }).ok, false);
  assert.equal(Core.readShare(ix, '#p=' + 'A'.repeat(7000)).ok, false);
});

test('profiles: new, duplicate, rename, delete, use, persistence, the last one stays', () => {
  const st = Core.memoryStorage();
  let pr = new Core.Profiles(ix, st);
  assert.equal(pr.list().length, 1);
  assert.equal(pr.remove(), false);
  pr.set('themeMode', 'Light');
  const b = pr.create('Work', { accent: 1 });
  assert.equal(pr.active().id, b.id);
  assert.equal(pr.active().values.themeMode, 'Dark', 'a new profile starts from the defaults');
  const c = pr.duplicate();
  assert.equal(c.name, 'Work copy');
  pr.rename('Work');                       // already taken: made unique
  assert.equal(pr.active().name, 'Work 2');
  pr.use(pr.list()[0].id);
  assert.equal(pr.active().values.themeMode, 'Light');
  pr = new Core.Profiles(ix, st);          // reload from storage
  assert.equal(pr.list().length, 3);
  assert.equal(pr.active().values.themeMode, 'Light');
  assert.equal(pr.remove(), true);
  assert.equal(pr.list().length, 2);
  assert.ok(pr.active());
  assert.equal(pr.set('onboardingDone', true), false, 'a device-bound key cannot be set');
});

test('profiles fall back cleanly when storage is damaged or refuses to write', () => {
  const broken = { getItem: () => '{not json', setItem: () => { throw new Error('quota'); } };
  const pr = new Core.Profiles(ix, broken);
  assert.equal(pr.list().length, 1);
  pr.set('labels', true);
  assert.match(pr.saveError, /would not keep/);
  assert.equal(pr.active().values.labels, true);
});

test('quick looks and starter profiles only use real keys with valid values', () => {
  for (const p of Core.LOOKS.concat(Core.STARTERS)) {
    const v = Core.applyPreset(ix, D, p);
    for (const [k, x] of Object.entries(v)) assert.ok(!Core.checkValue(ix.byKey[k], x).error, p.id + ' ' + k);
  }
  assert.deepEqual(Core.STARTERS.map((s) => s.name), ['Work', 'Calm', 'Desk']);
  assert.ok(Core.LOOKS.length >= 4);
});

test('the preview draws every scene for the defaults, every look and every starter, and each option of the look fields', () => {
  const scenes = Prev.SCENES.map((s) => s.id);
  const configs = [D].concat(Core.LOOKS.concat(Core.STARTERS).map((p) => Core.applyPreset(ix, D, p)));
  for (const cfg of configs) for (const s of scenes) {
    const svg = Prev.render(s, cfg);
    assert.match(svg, /^<svg[^]*<\/svg>$/);
    assert.ok(!/undefined|NaN|\[object/.test(svg), s + ' has a broken value');
    // tags are balanced (a cheap well-formedness check)
    const opens = (svg.match(/<(?!\/|\?|!)[a-zA-Z][^>]*[^/]>/g) || []).filter((t) => !/^<(rect|circle|line|path|ellipse|polygon|stop|text|use)\b/.test(t) || /^<(text)\b/.test(t)).length;
    const closes = (svg.match(/<\/[a-zA-Z]+>/g) || []).length;
    assert.equal(opens, closes, s + ' tags');
  }
  for (const f of Core.editable(ix)) {
    if (f.type !== 'enum') continue;
    for (const o of f.options) for (const s of ['glance', 'pages', 'radar', 'disc', 'comms', 'recents']) {
      const svg = Prev.render(s, Object.assign({}, D, { [f.key]: o.id }));
      assert.ok(!/undefined|NaN/.test(svg), f.key + '=' + o.id + ' in ' + s);
    }
  }
});

test('the preview follows the settings: clock style, position, corners, dock and font change the drawing', () => {
  const base = Prev.render('glance', D);
  const diff = (k, v) => assert.notEqual(Prev.render('glance', Object.assign({}, D, { [k]: v })), base, k + '=' + v);
  diff('clockStyle', 'AnalogClassic'); diff('glanceClockY', 0.5); diff('cornerStyle', 'Bubble'); diff('dock.style', '3d'); diff('uiFont', 'jetbrains_mono');
  diff('glanceHotspots', 'BottomLeft'); diff('calmStyle', 'Beam'); diff('wpGlanceKind', 'solid'); diff('glanceClockDate', true); diff('showMusic', false);
  assert.match(Prev.render('glance', Object.assign({}, D, { clockStyle: 'BigDigitalLine', clockFormat: 'Hour12' })), />2:09</);
  assert.match(Prev.render('glance', Object.assign({}, D, { uiFont: 'lexend' })), /font-family="'Lexend'/);
  assert.match(Prev.render('disc', Object.assign({}, D, { puckSegments: 6 })), /<line/);
  assert.equal(Prev.render('pages', Object.assign({}, D, { pCols: 5, pRows: 8 })).includes('Pages'), false);
});

test('the preview picks the scene the field concerns', () => {
  assert.equal(Prev.sceneFor(ix.byKey.searchLayout, 'radar'), 'radar');
  assert.equal(Prev.sceneFor(ix.byKey.puckSlots, 'apps'), 'disc');
  assert.equal(Prev.sceneFor(ix.byKey.leftPage, 'side'), 'comms');
  assert.equal(Prev.sceneFor(ix.byKey.rightSide, 'side'), 'recents');
  assert.equal(Prev.sceneFor(ix.byKey['dock.style'], 'home'), 'glance');
  assert.equal(Prev.sceneFor(ix.byKey.pCols, 'home'), 'pages');
  assert.equal(Prev.sceneFor(ix.byKey.cornerStyle, 'home'), 'glance');
});

test('option cards exist for the main visual choices and never throw', () => {
  for (const key of ['themeMode', 'iconShape', 'clockStyle', 'cornerStyle', 'dock.style', 'searchLayout', 'calmStyle', 'wpGlanceKind', 'homeOrder', 'homePages', 'uiFont', 'rightSide', 'leftPage']) {
    const f = ix.byKey[key]; assert.ok(f, key); assert.ok(Cards.has(key), key);
    for (const o of f.options) { const svg = Cards.card(key, o.id, D); assert.ok(svg && !/undefined|NaN/.test(svg), key + ' ' + o.id); }
  }
});

test('index.html loads only relative files and no external address', () => {
  const html = fs.readFileSync(path.join(dir, 'index.html'), 'utf8');
  const urls = [...html.matchAll(/(?:src|href)="([^"]+)"/g)].map((m) => m[1]).filter((u) => !u.startsWith('data:') && !u.startsWith('#'));
  for (const u of urls) { assert.ok(!/^(https?:)?\/\//.test(u) && !u.startsWith('/'), 'not relative: ' + u); assert.ok(fs.existsSync(path.join(dir, u)), 'missing: ' + u); }
  for (const f of ['studio.css', 'app.js', 'core.js', 'preview.js', 'cards.js']) {
    const t = fs.readFileSync(path.join(dir, f), 'utf8');
    assert.ok(!/(src|href|url\()\s*=?\s*["']?https?:\/\//.test(t), f + ' has an external address');
    assert.ok(!/\bfetch\(|XMLHttpRequest|sendBeacon|WebSocket/.test(t), f + ' makes a network request');
  }
});

test('the fixtures the Kotlin test reads are what Studio writes now', () => {
  const { build } = require('./make-fixtures.js');
  for (const [name, text] of Object.entries(build())) assert.equal(fs.readFileSync(path.join(__dirname, 'fixtures', name), 'utf8'), text, name + ' is stale: run node tests/make-fixtures.js');
});

// ---- wave 57 (profiles, CFG-49): a phone profile's settings subset round-trips through Studio

test('phone profiles: the groups name only schema keys and every key is in one group', () => {
  const seen = new Set();
  for (const g of schema.profileGroups) for (const k of g.keys) { assert.ok(ix.byKey[k], g.id + ': ' + k); assert.ok(!seen.has(k), 'twice: ' + k); seen.add(k); }
  assert.deepEqual(schema.profileGroups.map((g) => g.id), ['look', 'home', 'radar', 'side', 'sound']);
});

test('phone profiles: a Backup profile reads into Studio values, is edited and written back with nothing else changed', () => {
  const doc = JSON.parse(JSON.stringify(sampleState));
  doc.dock = Object.assign({}, doc.dock, { activeId: 'w', globalSettings: { themeMode: 'Dark' } });
  doc.workspaces = [{ id: 'default', name: 'Home' }, { id: 'w', name: 'Work', carries: ['look', 'home'], settings: { themeMode: 'Light', accent: 4287527935, 'dock.enabled': true, 'gestures.SwipeUp': 'action:AllApps', futureKey: 1 }, groupLinks: ['g1'] }];
  const ps = Core.phoneProfiles(ix, doc);
  assert.deepEqual(ps.map((p) => [p.name, p.active]), [['Home', false], ['Work', true]]);
  const w = ps[1];
  assert.equal(w.values.themeMode, 'Light');
  assert.equal(w.values['dock.enabled'], 'on');
  assert.ok(w.keys.includes('glanceHotspots') && !w.keys.includes('searchFiles'));
  assert.ok(w.warnings.some((x) => x.includes('futureKey')));
  const r = Core.setPhoneProfile(ix, doc, 'w', Object.assign({}, w.values, { themeMode: 'Amoled', 'dock.enabled': 'off', searchFiles: false }));
  assert.ok(r.ok);
  const out = r.doc.workspaces[1];
  assert.equal(out.settings.themeMode, 'Amoled');
  assert.equal(out.settings['dock.enabled'], false);
  assert.equal(out.settings.searchFiles, undefined, 'a key the profile does not carry is not written');
  assert.equal(out.settings.futureKey, 1, 'an unknown key is kept');
  assert.deepEqual(out.groupLinks, ['g1']);
  assert.deepEqual(r.doc.dock.globalSettings, { themeMode: 'Dark' });
  // and the whole Backup still exports through Studio unchanged apart from the profile
  const p = Core.importDocument(ix, r.doc);
  assert.ok(p.ok);
  const again = Core.exportState(ix, { values: p.values, extras: p.extras }, 1);
  assert.deepEqual(again.workspaces, r.doc.workspaces);
  assert.deepEqual(again.dock.globalSettings, { themeMode: 'Dark' });
  assert.equal(Core.setPhoneProfile(ix, doc, 'nope', {}).ok, false);
});

// ---- wave 58 (studioPreview)

test('CFG-70: the tabs carry the app\'s sections in order and every field sits in one of its tab\'s sections, in that order', () => {
  assert.deepEqual(schema.tabs.find((t) => t.id === 'apps').sections, ['Profiles', 'App groups and sessions', 'App drawer', 'Hidden apps', 'Open with']);
  for (const t of schema.tabs) {
    const fs2 = schema.fields.filter((f) => f.tab === t.id);
    let last = -1;
    for (const f of fs2) { const i = t.sections.indexOf(f.group); assert.ok(i >= 0, f.key + ' in ' + f.group); assert.ok(i >= last, f.key + ' out of order'); last = i; assert.ok(/^[^/]+\/.+/.test(f.row), f.key + ' names its app row'); }
  }
  assert.equal(ix.byKey.kbOpenP.tab, 'radar', 'the keyboard-with-Radar rows are on the Radar tab, as in the app');
  assert.equal(ix.byKey.rightSide.tab, 'side');
  assert.equal(ix.byKey.wpGlanceKind.group, 'Wallpaper');
});

const APPKEYS = ['com.google.android.gm/.ConversationListActivityGmail#0', 'com.microsoft.office.outlook/.MainActivity#0', 'com.whatsapp/.Main#0',
  'org.telegram.messenger/org.telegram.ui.LaunchActivity#0', 'com.example.mybank/.Main#0', 'com.example.paytoday/.Main#0', 'com.example.notes/.Main#0', 'com.example.mail/.Main#10'];
function phoneBase() { return Core.withPhoneProfiles(ix, { format: Core.STATE_FORMAT, schemaVersion: schema.stateSchemaVersion, layout: { pages: [{ k: APPKEYS, s: 'Grid', r: 2, id: 1 }] } }); }

test('CFG-71: a list started in Studio has the phone\'s Home; New from each start carries and sets what the phone\'s starters do', () => {
  let doc = Core.withPhoneProfiles(ix, null);
  assert.deepEqual(doc.workspaces, [{ id: 'default', name: 'Home' }]);
  assert.equal(doc.dock.activeId, 'default');
  doc = phoneBase();
  const values = Object.assign({}, D, { themeMode: 'Light' }), apps = Core.backupApps(doc);
  const starts = schema.profileStarts.map((s) => s.id);
  assert.deepEqual(starts, ['Current', 'Empty', 'Work', 'Calm', 'Desk']);
  const made = {};
  for (const st of starts) { const r = Core.createPhoneProfile(ix, doc, { start: st, values, apps }); assert.ok(r.ok, st); doc = r.doc; made[st] = doc.workspaces.find((w) => w.id === r.id); }
  assert.equal(made.Empty.carries, undefined); assert.equal(made.Empty.settings, undefined);
  assert.deepEqual(JSON.parse(JSON.parse(made.Empty.home).pages), [{ k: [], s: 'Grid', r: 2, id: 1 }]);
  assert.deepEqual(made.Work.carries, ['radar']); assert.equal(made.Work.settings.searchFiles, true);
  assert.deepEqual(JSON.parse(JSON.parse(made.Work.home).pages)[0].k, APPKEYS.filter((k) => /gm\/|outlook|#10/.test(k)).sort((a, b) => (a.includes('gm/') ? -1 : b.includes('gm/') ? 1 : a.includes('outlook') ? -1 : 1)));
  assert.ok(made.Work.notifyApps.includes('com.example.mail'), 'a work-profile app counts as work');
  assert.deepEqual(made.Calm.carries, ['home', 'look']);
  assert.equal(made.Calm.settings.homePages, 'Off'); assert.equal(made.Calm.settings.calmStyle, 'EdgeDots'); assert.equal(made.Calm.settings.glanceHotspots, 'BottomRight,BottomLeft');
  assert.equal(made.Calm.settings.themeMode, 'Light', 'a carried key without a starter value takes the global value');
  assert.deepEqual(made.Calm.notifyApps, ['com.whatsapp', 'org.telegram.messenger']);
  assert.equal(made.Desk.settings['dock.enabled'], true); assert.equal(made.Desk.settings['dock.edge'], 'Left'); assert.equal(made.Desk.settings.kbOpenL, true);
  assert.deepEqual(doc.workspaces.map((w) => w.name), ['Home', 'Current setup', 'Empty', 'Work', 'Calm', 'Desk']);
  // ticking other groups before saving: a starter value of an unticked group is dropped
  const r2 = Core.createPhoneProfile(ix, doc, { start: 'Calm', name: 'Calm', carries: ['look'], values, apps });
  const c2 = r2.doc.workspaces.find((w) => w.id === r2.id);
  assert.equal(c2.name, 'Calm 2'); assert.equal(c2.settings.homePages, undefined); assert.equal(c2.settings.calmStyle, 'EdgeDots');
  assert.equal(Core.createPhoneProfile(ix, doc, { start: 'Nope' }).ok, false);
});

test('CFG-71: rename, duplicate and delete follow the phone\'s rules', () => {
  let doc = phoneBase();
  const a = Core.createPhoneProfile(ix, doc, { start: 'Work', values: D, apps: [] }); doc = a.doc;
  assert.equal(Core.renamePhoneProfile(doc, a.id, 'Home').ok, false, 'names are unique');
  assert.equal(Core.renamePhoneProfile(doc, a.id, '  ').ok, false);
  doc = Core.renamePhoneProfile(doc, a.id, 'Office').doc;
  const d = Core.duplicatePhoneProfile(doc, a.id); doc = d.doc;
  const copy = doc.workspaces.find((w) => w.id === d.id);
  assert.equal(copy.name, 'Office copy'); assert.deepEqual(copy.carries, ['radar']); assert.notEqual(copy.id, a.id);
  assert.equal(Core.deletePhoneProfile(doc, 'default').ok, false, 'the active profile cannot be deleted');
  doc = Core.deletePhoneProfile(doc, d.id).doc;
  assert.deepEqual(doc.workspaces.map((w) => w.name), ['Home', 'Office']);
  assert.equal(Core.deletePhoneProfile(Core.withPhoneProfiles(ix, null), 'default').ok, false);
});

test('CFG-71: carries and switching follow the phone\'s precedence, and switching back restores the values', () => {
  let doc = phoneBase(), values = Object.assign({}, D, { themeMode: 'Light', searchFiles: false });
  const w = Core.createPhoneProfile(ix, doc, { start: 'Work', values, apps: [] }); doc = w.doc;
  // switch to Work: its searchFiles becomes live, the global false is kept aside
  let r = Core.switchPhoneProfile(ix, doc, w.id, values); doc = r.doc; values = r.values;
  assert.equal(values.searchFiles, true); assert.equal(doc.dock.activeId, w.id); assert.equal(doc.dock.globalSettings.searchFiles, false);
  // an edit while Work is active belongs to Work; the theme (not carried) stays global
  values = Object.assign({}, values, { searchLayout: 'Orbit', themeMode: 'Amoled' });
  // add Look to the active profile: the live values become its own and are kept aside as the global ones
  r = Core.setPhoneCarries(ix, doc, w.id, ['radar', 'look'], values); doc = r.doc; values = r.values;
  assert.equal(doc.workspaces.find((x) => x.id === w.id).settings.themeMode, 'Amoled'); assert.equal(doc.dock.globalSettings.themeMode, 'Amoled');
  // back to Home: Work keeps Orbit and files on, the global values come back
  r = Core.switchPhoneProfile(ix, doc, 'default', values); doc = r.doc; values = r.values;
  const work = doc.workspaces.find((x) => x.id === w.id);
  assert.equal(work.settings.searchLayout, 'Orbit'); assert.equal(work.settings.searchFiles, true);
  assert.equal(values.searchFiles, false); assert.equal(values.searchLayout, D.searchLayout); assert.equal(values.themeMode, 'Amoled');
  assert.equal(doc.dock.globalSettings, undefined, 'Home carries nothing, nothing is kept aside');
  // drop Look from the active profile puts the global value back live
  r = Core.switchPhoneProfile(ix, doc, w.id, Object.assign({}, values, { themeMode: 'Dark' })); doc = r.doc; values = r.values;
  values = Object.assign({}, values, { themeMode: 'Light' });
  r = Core.setPhoneCarries(ix, doc, w.id, ['radar'], values); doc = r.doc; values = r.values;
  assert.equal(values.themeMode, 'Dark'); assert.equal(doc.workspaces.find((x) => x.id === w.id).settings.themeMode, undefined);
  // a group added to another profile starts from the global values
  r = Core.setPhoneCarries(ix, doc, 'default', ['sound'], values);
  assert.equal(r.doc.workspaces[0].settings.uiSounds, values.uiSounds);
  assert.equal(Core.switchPhoneProfile(ix, doc, 'nope', values).ok, false);
});

test('CFG-71: app group suggestions come from the apps a Backup names, with the phone\'s tables, and save as app groups of 2 to 4', () => {
  const doc = phoneBase(), apps = Core.backupApps(doc);
  assert.equal(apps.length, APPKEYS.length);
  const g = Core.suggestGroups(ix, apps);
  assert.deepEqual(g.map((x) => x.kind), ['Work', 'Personal', 'Money']);
  assert.deepEqual(g[0].apps.map((a) => a.pkg), ['com.google.android.gm', 'com.microsoft.office.outlook', 'com.example.mail']);
  assert.deepEqual(g[2].apps.map((a) => a.pkg), ['com.example.mybank', 'com.example.paytoday'], 'bank and pay words');
  assert.equal(Core.addAppGroup(ix, doc, 'One', g[0].apps.slice(0, 1)).ok, false);
  const r = Core.addAppGroup(ix, doc, 'Work apps', g[0].apps, 'default');
  assert.ok(r.ok);
  const grp = r.doc.dock.groups[0];
  assert.deepEqual(grp.members[0], { target: { p: 'com.google.android.gm', c: 'com.google.android.gm.ConversationListActivityGmail', u: 0 }, display: null, bounds: null, placement: 'Default' });
  assert.equal(grp.members[2].target.u, 10);
  assert.deepEqual(r.doc.workspaces[0].groupLinks, [grp.id]);
  assert.deepEqual(Core.suggestGroups(ix, Core.backupApps({ format: Core.STATE_FORMAT })), [], 'a file with no apps suggests nothing (the page says suggestions run on the phone)');
  // apps are also found in profile homes, pins and groups
  const more = Core.backupApps(r.doc);
  assert.equal(more.length, APPKEYS.length);
});

test('CFG-71: the profiles travel in the exported Backup and survive an import and export', () => {
  let doc = phoneBase();
  doc = Core.createPhoneProfile(ix, doc, { start: 'Desk', values: D, apps: [] }).doc;
  const p = { name: 'x', values: Object.assign({}, D), extras: Object.assign(Core.newExtras(), { base: doc }) };
  const out = Core.exportState(ix, p, 1);
  assert.deepEqual(out.workspaces, doc.workspaces);
  assert.equal(out.dock.activeId, 'default');
  const back = Core.importDocument(ix, out);
  assert.ok(back.ok);
  assert.deepEqual(Core.phoneProfiles(ix, back.extras.base).map((x) => x.name), ['Home', 'Desk']);
  assert.deepEqual(Core.exportState(ix, back, 1).workspaces, doc.workspaces);
  // the active profile holds the live values of what it carries when exported
  const sw = Core.switchPhoneProfile(ix, doc, doc.workspaces[1].id, D);
  const synced = Core.syncActivePhoneProfile(ix, sw.doc, Object.assign({}, sw.values, { kbOpenP: false }));
  assert.equal(synced.workspaces[1].settings.kbOpenP, false);
  const fix = require('./make-fixtures.js');
  assert.equal(fs.readFileSync(path.join(__dirname, 'fixtures', 'studio-profiles-backup.json'), 'utf8'), fix.build()['studio-profiles-backup.json'], 'studio-profiles-backup.json is stale: run node tests/make-fixtures.js');
});

test('CFG-72: the Signal wallpaper is drawn in the theme colours, not as a placeholder, for every wallpaper and effect', () => {
  const opts = (k) => ix.byKey[k].options.map((o) => o.id);
  for (const s1 of opts('bgSlot1')) for (const s2 of opts('bgSlot2')) for (const themeMode of ['Dark', 'Light']) {
    const svg = Prev.render('glance', Object.assign({}, D, { wpGlanceKind: 'live', bgSlot1: s1, bgSlot2: s2, themeMode, accent: 0xFF112233 }));
    assert.ok(!/placeholder/i.test(svg), s1 + '/' + s2 + ' still says placeholder');
    assert.ok(svg.includes('url(#LBG)'), 'the black (or pale) gradient behind it');
    if (s1 !== 'none') assert.ok(svg.includes('rgb(17,34,51)'), s1 + ' uses the accent');
  }
  const spire = Prev.render('glance', Object.assign({}, D, { wpGlanceKind: 'live', bgSlot1: 'spire', bgSlot2: 'none' }));
  const beam = Prev.render('glance', Object.assign({}, D, { wpGlanceKind: 'live', bgSlot1: 'beam', bgSlot2: 'none' }));
  assert.notEqual(spire, beam);
  assert.ok(Prev.render('glance', Object.assign({}, D, { wpGlanceKind: 'live', bgSlot1: 'none', bgSlot2: 'breathing-dots' })).match(/<circle/g).length > 40, 'the dots');
});

test('CFG-72: landscape draws the home surfaces wide, with the dock at the side edge the settings say', () => {
  const v = Object.assign({}, D, { 'dock.enabled': 'on', 'dock.edge': 'Left', homePages: 'Paginated' });
  const land = Prev.render('glance', v, { landscape: true });
  assert.ok(land.includes('viewBox="0 0 760 380"') && land.includes('data-landscape="true"'));
  assert.ok(Prev.render('glance', v).includes('viewBox="0 0 380 760"'), 'portrait keeps the dock at the bottom');
  // the dock bar is a column near the left edge in landscape, near the right with Right, and at the bottom with Bottom
  const bar = (svg) => { const m = /<rect x="([\d.]+)" y="([\d.]+)" width="66" height="([\d.]+)"/.exec(svg); return m && m.slice(1).map(Number); };
  assert.equal(bar(land)[0], 10);
  assert.ok(bar(Prev.render('glance', Object.assign({}, v, { 'dock.edge': 'Right' }), { landscape: true }))[0] > 600);
  assert.equal(bar(Prev.render('glance', Object.assign({}, v, { 'dock.edge': 'Bottom' }), { landscape: true })), null, 'Bottom stays a row');
  for (const sc of ['glance', 'pages']) for (const edge of ['Bottom', 'Left', 'Right']) for (const mode of ['Collapsed', 'PinnedPlusRecent'])
    assert.ok(Prev.render(sc, Object.assign({}, v, { 'dock.edge': edge, 'dock.mode': mode }), { landscape: true }).length > 1000);
  assert.ok(Prev.render('radar', v, { landscape: true }).includes('viewBox="0 0 380 760"'), 'the other scenes stay portrait');
  // the landscape clock place is the landscape pair
  const c = /<g class="clock" data-kx="(\w+)" data-ky="(\w+)"/.exec(land);
  assert.deepEqual([c[1], c[2]], ['glanceClockXLand', 'glanceClockYLand']);
});

test('CFG-72: dock magnification is drawn as a sample bulge when it is on, growing with the strength', () => {
  const v = Object.assign({}, D, { 'dock.enabled': 'on' });
  assert.ok(!Prev.render('glance', v).includes('magnify-sample'));
  const on = Object.assign({}, v, { 'dock.magnify': true });
  assert.ok(Prev.render('glance', on).includes('magnify-sample'));
  const peak = (s) => Math.max.apply(null, Prev.magnifyScales(Object.assign({}, on, { 'dock.magnifyStrength': s }), 6, 1));
  assert.equal(peak('subtle'), 1.3); assert.equal(peak('medium'), 1.6); assert.equal(peak('large'), 2.0);
  const sc = Prev.magnifyScales(Object.assign({}, on, { 'dock.magnifyStrength': 'large' }), 6, 1);
  assert.ok(sc[1] > sc[0] && sc[0] > 1 && sc[3] === 1, 'neighbours grow less, two away not at all');
  assert.deepEqual(Prev.magnifyScales(v, 6, 1), [1, 1, 1, 1, 1, 1]);
  assert.ok(Prev.render('glance', Object.assign({}, on, { 'dock.edge': 'Right' }), { landscape: true }).includes('magnify-sample'), 'the side dock too');
});

test('CFG-72: the clock drag writes the placement fields, kept inside the screen, to the hundredth', () => {
  assert.deepEqual(Prev.clockFromBox(100, 74, 160, 120, 360, 740), { x: 0.5, y: 0.1 });
  assert.deepEqual(Prev.clockFromBox(-50, -20, 160, 120, 360, 740), { x: 0.22, y: 0 }, 'not past the left or top');
  assert.deepEqual(Prev.clockFromBox(400, 900, 160, 120, 360, 740), { x: 0.78, y: 0.84 }, 'not past the right or bottom');
  const svg = Prev.render('glance', Object.assign({}, D, { glanceClockX: 0.3, glanceClockY: 0.5 }));
  const m = /data-left="([\d.]+)" data-top="([\d.]+)" data-w="([\d.]+)" data-h="([\d.]+)" data-areaw="(\d+)" data-areah="(\d+)"/.exec(svg).slice(1).map(Number);
  const back = Prev.clockFromBox(m[0], m[1], m[2], m[3], m[4], m[5]);
  assert.ok(Math.abs(back.x - 0.3) < 0.011 && Math.abs(back.y - 0.5) < 0.011, 'the drawn box reads back as the saved place');
  for (const k of ['glanceClockX', 'glanceClockY', 'glanceClockXLand', 'glanceClockYLand']) assert.equal(Core.checkValue(ix.byKey[k], back.x).error, undefined);
});
