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
function build() {
  const p = fixtureProfile();
  return {
    'studio-settings.json': Core.toText(Core.exportSettings(ix, p)),
    'studio-backup.json': Core.toText(Core.exportState(ix, p, 1700000000000)),
  };
}
module.exports = { build, fixtureProfile };
if (require.main === module) {
  const dir = path.join(__dirname, 'fixtures');
  fs.mkdirSync(dir, { recursive: true });
  for (const [name, text] of Object.entries(build())) fs.writeFileSync(path.join(dir, name), text);
  console.log('wrote fixtures');
}
