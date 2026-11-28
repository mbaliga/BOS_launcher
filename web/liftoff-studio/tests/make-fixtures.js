// Writes the two files the Kotlin test (core-data StudioSchemaW56Test) feeds to the app's own codec.
// Run: node web/liftoff-studio/tests/make-fixtures.js     (the node test checks they are current)
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const Core = require('../core.js');
const schema = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'schema.json'), 'utf8'));
const ix = Core.indexSchema(schema);

function fixtureProfile() {
  const values = Object.assign(Core.defaults(ix), {
    themeMode: 'Light', accent: 0xFF112233, clockStyle: 'AnalogMinimal', glanceClockX: 0.35, glanceHotspots: 'BottomRight,BottomLeft', calmStyle: 'Beam', uiFont: 'lexend',
    wpGlanceKind: 'gradient', homePages: 'Continuous', 'dock.style': '3d', 'dock.enabled': 'off', 'dock.edge': 'Left', 'corners.TopLeft': 'action:Flashlight',
    'gestures.PinchOut': 'action:Search', 'gestures.DoubleTap': '', commsScale: 115, hapticStrength: 'strong', puckSegments: 6,
  });
  const extras = Core.newExtras();
  extras.unknownPrefs.studioKeptField = { note: 'unknown fields survive' };
  return { name: 'Fixture', values, extras };
}
// Wave 58 (CFG-71): a Backup whose phone profiles were made in Studio's Profiles panel (feature-dock StudioProfilesW58Test reads it with the app's codecs).
const APPS = ['com.google.android.gm/.ConversationListActivityGmail#0', 'com.microsoft.office.outlook/.MainActivity#0', 'com.whatsapp/.Main#0', 'org.telegram.messenger/org.telegram.ui.LaunchActivity#0', 'com.example.notes/.Main#0'];
function profilesBackup() {
  const base = { format: Core.STATE_FORMAT, schemaVersion: schema.stateSchemaVersion, layout: { pages: [{ k: APPS, s: 'Grid', r: 2, id: 1 }], removed: [], hidden: [], seeded: true } };
  let doc = Core.withPhoneProfiles(ix, base);
  let values = Object.assign(Core.defaults(ix), { themeMode: 'Dark', searchFiles: false });
  const apps = Core.backupApps(doc);
  const mk = (start, name, carries) => { const r = Core.createPhoneProfile(ix, doc, { start, name, carries, values, apps }); doc = r.doc; return r.id; };
  const work = mk('Work', 'Work');
  mk('Calm', 'Calm');
  mk('Desk', 'Desk');
  const g = Core.addAppGroup(ix, doc, 'Work apps', Core.suggestGroups(ix, apps).find((x) => x.kind === 'Work').apps, work);
  doc = g.doc;
  const sw = Core.switchPhoneProfile(ix, doc, work, values);
  doc = sw.doc; values = sw.values;
  const p = { name: 'Profiles', values, extras: Object.assign(Core.newExtras(), { base: doc }) };
  return Core.exportState(ix, { values, extras: Object.assign({}, p.extras, { base: Core.syncActivePhoneProfile(ix, doc, values) }) }, 1700000000000);
}
function build() {
  const p = fixtureProfile();
  return {
    'studio-settings.json': Core.toText(Core.exportSettings(ix, p)),
    'studio-backup.json': Core.toText(Core.exportState(ix, p, 1700000000000)),
    'studio-profiles-backup.json': stableIds(Core.toText(profilesBackup())),
  };
}
// The ids Studio makes are random; the fixture is compared as text, so they are renamed in order of appearance.
function stableIds(text) {
  const doc = JSON.parse(text), map = {};
  let n = 0, g = 0;
  doc.workspaces.forEach((w) => { if (w.id !== Core.PHONE_DEFAULT_ID) map[w.id] = 'p' + (++n); });
  (doc.dock.groups || []).forEach((x) => { map[x.id] = 'g' + (++g); });
  return text.replace(/"([0-9a-fg]{8,9})"/g, (m, id) => (map[id] ? '"' + map[id] + '"' : m));
}
module.exports = { build, fixtureProfile, profilesBackup, stableIds };
if (require.main === module) {
  const dir = path.join(__dirname, 'fixtures');
  fs.mkdirSync(dir, { recursive: true });
  for (const [name, text] of Object.entries(build())) fs.writeFileSync(path.join(dir, name), text);
  console.log('wrote fixtures');
}
