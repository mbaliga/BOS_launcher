/*
 * Liftoff Studio core: the logic behind the page, with no DOM so it runs under node (see tests/studio.test.js).
 *
 *  - reads schema.json / schema.js (generated from the app's code, see README) into an index;
 *  - turns a configuration (a flat map: schema key -> value) into the app's own JSON ("liftoff-settings" and a partial "liftoff-state")
 *    and back, validating types and keeping every field it does not know;
 *  - profiles (New / Duplicate / Rename / Delete / Use) over any storage with getItem / setItem;
 *  - share links (the profile in the URL fragment, size bounded; nothing leaves the browser);
 *  - quick looks and starter profiles.
 *
 * A configuration holds only schema keys. What came from a file and is not a schema key stays in profile.extras so that exporting it again loses nothing.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.StudioCore = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var SETTINGS_FORMAT = 'liftoff-settings';
  var STATE_FORMAT = 'liftoff-state';
  var CUSTOM = '~custom'; // a corner, gesture or ring segment that holds an app, a palette or a widget: kept as it is, not editable here
  var STORE_KEY = 'liftoff_studio_profiles_v1';
  var UI_KEY = 'liftoff_studio_ui_v1';
  var MAX_SHARE_CHARS = 6000;
  var CORNERS = ['TopLeft', 'TopRight', 'BottomRight', 'BottomLeft'];

  // ---------------------------------------------------------------------------------------------------------- schema

  function indexSchema(schema) {
    var byKey = {}, scopes = {};
    schema.fields.forEach(function (f) { byKey[f.key] = f; (scopes[f.scope] = scopes[f.scope] || []).push(f); });
    return { schema: schema, fields: schema.fields, byKey: byKey, scopes: scopes, tabs: schema.tabs };
  }

  /** The editable fields: everything that is not bound to a device. */
  function editable(ix) { return ix.fields.filter(function (f) { return !f.deviceBound; }); }

  function defaults(ix) {
    var d = {};
    editable(ix).forEach(function (f) { d[f.key] = f.default; });
    return d;
  }

  function isNum(v) { return typeof v === 'number' && isFinite(v); }
  function describe(v) { return v === null ? 'null' : Array.isArray(v) ? 'a list' : typeof v === 'string' ? '"' + v.slice(0, 24) + '"' : typeof v === 'object' ? 'an object' : String(v); }

  function round(v, f) {
    if (f.type === 'int') return Math.round(v);
    var step = f.step || 0.01; // floats keep what the file said, only noise from the float cast is trimmed
    var digits = Math.max(2, Math.min(6, (String(step).split('.')[1] || '').length + 1));
    return Number(v.toFixed(digits));
  }

  /**
   * Checks one value against its field. Returns {value, warn?} or {error}. A wrong type is an error; a value outside the range is clamped
   * (the app does the same) and an option id this Studio does not know is kept as it is with a warning.
   */
  function checkValue(f, v) {
    var label = f.label + ' (' + f.key + ')';
    switch (f.type) {
      case 'bool':
        if (typeof v !== 'boolean') return { error: label + ' must be on or off (true or false), not ' + describe(v) };
        return { value: v };
      case 'int': case 'float': {
        if (!isNum(v)) return { error: label + ' must be a number, not ' + describe(v) };
        var n = f.type === 'int' ? Math.round(v) : v, warn;
        if (f.min != null && n < f.min) { warn = label + ': ' + n + ' is below ' + f.min + ', set to ' + f.min; n = f.min; }
        if (f.max != null && n > f.max) { warn = label + ': ' + n + ' is above ' + f.max + ', set to ' + f.max; n = f.max; }
        return { value: round(n, f), warn: warn };
      }
      case 'colour': {
        if (!isNum(v) || Math.floor(v) !== v) return { error: label + ' must be a colour number (ARGB), not ' + describe(v) };
        var c = v < 0 ? v + 4294967296 : v;
        if (c < 0 || c > 4294967295) return { error: label + ' is not a 32-bit colour: ' + v };
        return { value: c };
      }
      case 'enum': {
        if (typeof v !== 'string') return { error: label + ' must be one of its names (text), not ' + describe(v) };
        if (!f.options.some(function (o) { return o.id === v; })) return { value: v, warn: label + ': "' + v + '" is not an option this Studio knows; kept as it is, the app falls back to its default' };
        return { value: v };
      }
      case 'corners': {
        if (typeof v !== 'string') return { error: label + ' must be a comma list of corners, not ' + describe(v) };
        var on = v.split(',').filter(Boolean), bad = on.filter(function (x) { return CORNERS.indexOf(x) < 0; });
        return { value: CORNERS.filter(function (x) { return on.indexOf(x) >= 0; }).join(','), warn: bad.length ? label + ': ignored ' + bad.join(', ') : undefined };
      }
      case 'slots': {
        if (typeof v !== 'string') return { error: label + ' must be text with one shortcut per line, not ' + describe(v) };
        var lines = v.split('\n');
        while (lines.length < f.count) lines.push('-');
        return { value: lines.slice(0, f.count).join('\n') };
      }
      case 'string':
        if (typeof v !== 'string') return { error: label + ' must be text, not ' + describe(v) };
        return { value: v };
      default: return { error: label + ' has an unknown type ' + f.type };
    }
  }

  // ------------------------------------------------------------------------------------------------ import (from the app's JSON)

  function parseText(text) {
    if (typeof text !== 'string') return { error: 'Nothing to read' };
    var t = text.replace(/^﻿/, '').trim();
    if (!t) return { error: 'The file is empty' };
    try { return { json: JSON.parse(t) }; }
    catch (e) { return { error: 'Not a JSON file: ' + String(e.message || e).replace(/^JSON\.parse: /, '').slice(0, 120) }; }
  }

  function readEnabled(v) { return v === null || v === undefined ? 'auto' : v === true ? 'on' : v === false ? 'off' : v; }

  /**
   * Reads a Liftoff document (the app's "Export settings only" file, or a full Backup file) into a profile body.
   * Returns {ok:true, values, extras, warnings, kind} or {ok:false, errors}. Values missing from the file take the default.
   */
  function importDocument(ix, input) {
    var doc = input;
    if (typeof input === 'string') { var p = parseText(input); if (p.error) return { ok: false, errors: [p.error] }; doc = p.json; }
    if (!doc || typeof doc !== 'object' || Array.isArray(doc)) return { ok: false, errors: ['Not a Liftoff file: the top level must be an object'] };
    var errors = [], warnings = [], kind, settings, extras = { unknownPrefs: {}, settingsTop: {}, base: null };
    if (doc.format === SETTINGS_FORMAT) {
      kind = 'settings'; settings = doc;
      if (typeof doc.settingsVersion === 'number' && doc.settingsVersion > ix.schema.settingsVersion) warnings.push('This settings file is newer (version ' + doc.settingsVersion + ') than this Studio knows (' + ix.schema.settingsVersion + '); fields it does not know are kept.');
    } else if (doc.format === STATE_FORMAT) {
      kind = 'state';
      if (typeof doc.schemaVersion !== 'number') errors.push('The Backup file has no schema version');
      else if (doc.schemaVersion > ix.schema.stateSchemaVersion) warnings.push('This Backup is newer (version ' + doc.schemaVersion + ') than this Studio knows (' + ix.schema.stateSchemaVersion + '); fields it does not know are kept.');
      settings = doc.settings;
      if (settings === undefined && doc.prefs && typeof doc.prefs === 'object') settings = { prefs: doc.prefs }; // schema 1 kept the prefs at the top
      if (settings !== undefined && (settings === null || typeof settings !== 'object' || Array.isArray(settings))) { errors.push('"settings" must be an object'); settings = undefined; }
      if (settings === undefined && !errors.length) warnings.push('This Backup has no settings section; Studio shows the defaults.');
    } else {
      return { ok: false, errors: ['Not a Liftoff file (format is ' + (typeof doc.format === 'string' ? '"' + doc.format + '"' : 'missing') + '). Use a file made by Backup > Export in the app, or by this Studio.'] };
    }

    var values = defaults(ix);
    var known = {};
    function take(f, raw) {
      var r = checkValue(f, raw);
      if (r.error) errors.push(r.error); else { values[f.key] = r.value; if (r.warn) warnings.push(r.warn); }
    }

    if (settings) {
      var prefs = settings.prefs;
      if (prefs !== undefined && (prefs === null || typeof prefs !== 'object' || Array.isArray(prefs))) { errors.push('"prefs" must be an object'); prefs = {}; }
      prefs = prefs || {};
      Object.keys(settings).forEach(function (k) { if (k !== 'format' && k !== 'settingsVersion' && k !== 'prefs') extras.settingsTop[k] = settings[k]; });
      Object.keys(prefs).forEach(function (k) {
        var f = ix.byKey[k];
        if (!f || f.scope !== 'settings') { extras.unknownPrefs[k] = prefs[k]; return; }
        if (f.deviceBound) { warnings.push(f.label + ' (' + k + ') belongs to the phone it was made on and is left out.'); return; }
        known[k] = true;
        if (f.optional && f.key === 'calmStyle' && (prefs[k] === null)) return;
        take(f, prefs[k]);
      });
    }

    if (kind === 'state') {
      var dock = doc.dock && doc.dock.settings;
      if (doc.dock !== undefined && doc.dock !== null && (typeof doc.dock !== 'object' || Array.isArray(doc.dock))) errors.push('"dock" must be an object');
      else if (dock && typeof dock === 'object') (ix.scopes.dock || []).forEach(function (f) {
        var n = f.key.replace('dock.', '');
        if (!(n in dock)) return;
        if (f.key === 'dock.enabled') {
          var e = readEnabled(dock[n]);
          if (['auto', 'on', 'off'].indexOf(e) < 0) errors.push(f.label + ' (dock.enabled) must be true, false or null, not ' + describe(dock[n])); else values[f.key] = e;
        } else take(f, dock[n]);
      });
      var sc = doc.shortcuts;
      if (sc !== undefined && sc !== null && (typeof sc !== 'object' || Array.isArray(sc))) errors.push('"shortcuts" must be an object');
      else if (sc) {
        var corners = sc.corners || {}, gestures = sc.gestures || {};
        (ix.scopes.shortcuts || []).forEach(function (f) {
          var isCorner = f.key.indexOf('corners.') === 0, n = f.key.split('.')[1], raw = (isCorner ? corners : gestures)[n];
          if (raw === undefined) return;
          if (isCorner) {
            var slot = raw;
            if (typeof raw === 'string') { try { slot = JSON.parse(raw); } catch (e) { slot = null; } }
            if (slot && slot.t === 'single' && typeof slot.i === 'string' && slot.i.indexOf('action:') === 0) values[f.key] = slot.i;
            else if (slot && slot.t === 'empty') values[f.key] = '';
            else values[f.key] = CUSTOM;
          } else {
            if (typeof raw !== 'string') { errors.push(f.label + ' (' + f.key + ') must be text, not ' + describe(raw)); return; }
            values[f.key] = raw === '' || raw.indexOf('action:') === 0 ? raw : CUSTOM;
          }
        });
      }
      var pf = doc.prefFiles;
      if (pf !== undefined && pf !== null && (typeof pf !== 'object' || Array.isArray(pf))) errors.push('"prefFiles" must be an object');
      else if (pf) (ix.scopes.prefFile || []).forEach(function (f) {
        var file = pf[f.file], cell = file && file[f.fileKey];
        if (!cell) return;
        if (typeof cell !== 'object' || !('v' in cell)) { errors.push(f.label + ' (' + f.file + '.' + f.fileKey + ') must look like {"t":..,"v":..}'); return; }
        if (f.key === 'puckSlots') {
          var r0 = checkValue(f, cell.v);
          if (r0.error) { errors.push(r0.error); return; }
          values[f.key] = r0.value.split('\n').map(function (s) { return s === '-' || s.indexOf('a:') === 0 ? s : CUSTOM; }).join('\n');
          return;
        }
        take(f, cell.v);
      });
      extras.base = doc;
    }
    if (errors.length) return { ok: false, errors: errors };
    return { ok: true, kind: kind, values: values, extras: extras, warnings: warnings };
  }

  // ------------------------------------------------------------------------------------------------ export (the app's JSON)

  function settingsPrefs(ix, values, extras) {
    var prefs = {};
    Object.keys((extras && extras.unknownPrefs) || {}).forEach(function (k) { prefs[k] = extras.unknownPrefs[k]; });
    (ix.scopes.settings || []).forEach(function (f) {
      if (f.deviceBound) return;
      var v = values[f.key];
      if (v === undefined) v = f.default;
      if (f.key === 'calmStyle' && (v === 'auto' || v == null)) return; // never chosen is stored as no key at all
      prefs[f.key] = v;
    });
    // The order of the app's own file is alphabetical per store; sorted keys make a stable, diffable file.
    var sorted = {};
    Object.keys(prefs).sort().forEach(function (k) { sorted[k] = prefs[k]; });
    return sorted;
  }

  /** The app's "Export settings only" document. Dock, corners, gestures and the other files are not part of it (see exportState). */
  function exportSettings(ix, profile) {
    var doc = { format: SETTINGS_FORMAT, settingsVersion: ix.schema.settingsVersion, prefs: settingsPrefs(ix, profile.values, profile.extras) };
    var top = (profile.extras && profile.extras.settingsTop) || {};
    Object.keys(top).forEach(function (k) { if (!(k in doc)) doc[k] = top[k]; });
    return doc;
  }

  function clone(o) { return o === undefined ? undefined : JSON.parse(JSON.stringify(o)); }

  function dockEnabledOut(v) { return v === 'on' ? true : v === 'off' ? false : null; }

  /**
   * A Backup ("liftoff-state") document that carries the settings, the dock settings, the corner and gesture actions and the ring and Comms files.
   * With a profile that was imported from a Backup the rest of that Backup (layout, groups, workspaces ...) is carried through untouched;
   * otherwise only these sections are present, and the app's import then changes only these sections.
   */
  function exportState(ix, profile, now) {
    var v = profile.values, ex = profile.extras || {};
    var doc = ex.base ? clone(ex.base) : { format: STATE_FORMAT, schemaVersion: ix.schema.stateSchemaVersion };
    doc.format = STATE_FORMAT;
    if (typeof doc.schemaVersion !== 'number') doc.schemaVersion = ix.schema.stateSchemaVersion;
    doc.exportedAt = now === undefined ? Date.now() : now;
    doc.app = 'liftoff-studio';
    delete doc.prefs; // a schema 1 top-level prefs map is replaced by the settings section
    var s = exportSettings(ix, profile);
    doc.settings = s;

    // dock.settings
    doc.dock = doc.dock && typeof doc.dock === 'object' && !Array.isArray(doc.dock) ? doc.dock : {};
    var ds = doc.dock.settings && typeof doc.dock.settings === 'object' ? doc.dock.settings : {};
    (ix.scopes.dock || []).forEach(function (f) {
      var n = f.key.replace('dock.', ''), x = v[f.key] === undefined ? f.default : v[f.key];
      ds[n] = n === 'enabled' ? dockEnabledOut(x) : x;
    });
    doc.dock.settings = ds;

    // shortcuts: corners and gestures
    doc.shortcuts = doc.shortcuts && typeof doc.shortcuts === 'object' && !Array.isArray(doc.shortcuts) ? doc.shortcuts : {};
    var sc = doc.shortcuts;
    sc.corners = sc.corners && typeof sc.corners === 'object' ? sc.corners : {};
    sc.gestures = sc.gestures && typeof sc.gestures === 'object' ? sc.gestures : {};
    (ix.scopes.shortcuts || []).forEach(function (f) {
      var n = f.key.split('.')[1], x = v[f.key] === undefined ? f.default : v[f.key];
      if (x === CUSTOM) return; // kept as the file had it
      if (f.key.indexOf('corners.') === 0) {
        var slot = x === '' ? { t: 'empty' } : { t: 'single', i: x };
        var old = sc.corners[n];
        if (old && typeof old === 'object') { // unknown fields inside a slot survive
          var merged = clone(old); merged.t = slot.t; if (slot.i !== undefined) merged.i = slot.i; else delete merged.i; slot = merged;
        }
        sc.corners[n] = slot;
      } else sc.gestures[n] = x;
    });

    // prefFiles: only the keys this Studio manages, inside the files it knows
    var pf = doc.prefFiles && typeof doc.prefFiles === 'object' && !Array.isArray(doc.prefFiles) ? doc.prefFiles : {};
    (ix.scopes.prefFile || []).forEach(function (f) {
      var x = v[f.key] === undefined ? f.default : v[f.key];
      pf[f.file] = pf[f.file] && typeof pf[f.file] === 'object' ? pf[f.file] : {};
      if (f.key === 'puckSlots' && typeof x === 'string' && x.indexOf(CUSTOM) >= 0) {
        // keep the app segments the file already had
        var oldCell = pf[f.file][f.fileKey], oldLines = oldCell && typeof oldCell.v === 'string' ? oldCell.v.split('\n') : [];
        x = x.split('\n').map(function (s, i) { return s === CUSTOM ? (oldLines[i] || '-') : s; }).join('\n');
      }
      pf[f.file][f.fileKey] = { t: f.fileType, v: x };
    });
    doc.prefFiles = pf;
    return doc;
  }

  function toText(doc) { return JSON.stringify(doc, null, 2) + '\n'; }

  function slug(name) { return String(name || 'profile').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40) || 'profile'; }

  // ------------------------------------------------------------------------------------------------------ share links

  function b64urlEncode(str) {
    var bytes = typeof TextEncoder !== 'undefined' ? new TextEncoder().encode(str) : Buffer.from(str, 'utf8');
    var bin = ''; for (var i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
    var b = typeof btoa === 'function' ? btoa(bin) : Buffer.from(bin, 'binary').toString('base64');
    return b.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }
  function b64urlDecode(s) {
    var b = s.replace(/-/g, '+').replace(/_/g, '/'); while (b.length % 4) b += '=';
    var bin = typeof atob === 'function' ? atob(b) : Buffer.from(b, 'base64').toString('binary');
    var bytes = new Uint8Array(bin.length); for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return typeof TextDecoder !== 'undefined' ? new TextDecoder().decode(bytes) : Buffer.from(bytes).toString('utf8');
  }

  /** The fragment (without the #) that carries a profile: its name and only the values that differ from the defaults. Returns {ok, fragment} or {ok:false, error}. */
  function shareFragment(ix, profile) {
    var d = defaults(ix), diff = {};
    Object.keys(profile.values).forEach(function (k) { if (k in d && JSON.stringify(profile.values[k]) !== JSON.stringify(d[k])) diff[k] = profile.values[k]; });
    var frag = 'p=' + b64urlEncode(JSON.stringify({ v: 1, n: String(profile.name || '').slice(0, 60), d: diff }));
    if (frag.length > MAX_SHARE_CHARS) return { ok: false, error: 'This profile is too large for a link (' + frag.length + ' characters, the limit is ' + MAX_SHARE_CHARS + '). Use Export to make a file instead.' };
    return { ok: true, fragment: frag };
  }

  /** Reads a pasted link, a "#p=..." fragment or the bare payload. The values go through the same checks as a file. */
  function readShare(ix, text) {
    var s = String(text || '').trim(), at = s.indexOf('p=');
    if (at < 0 && !/^[A-Za-z0-9_-]+$/.test(s)) return { ok: false, errors: ['This is not a Studio link'] };
    var payload = at >= 0 ? s.slice(at + 2).split(/[&\s]/)[0] : s, obj;
    if (payload.length > MAX_SHARE_CHARS) return { ok: false, errors: ['The link is longer than the ' + MAX_SHARE_CHARS + ' characters Studio accepts'] };
    try { obj = JSON.parse(b64urlDecode(payload)); } catch (e) { return { ok: false, errors: ['The link is damaged and cannot be read'] }; }
    if (!obj || obj.v !== 1 || typeof obj.d !== 'object' || obj.d === null || Array.isArray(obj.d)) return { ok: false, errors: ['The link is from a version of Studio this one cannot read'] };
    var values = defaults(ix), errors = [], warnings = [];
    Object.keys(obj.d).forEach(function (k) {
      var f = ix.byKey[k];
      if (!f || f.deviceBound) { warnings.push('Ignored ' + k + ' (not a setting of this Studio)'); return; }
      var r = checkValue(f, obj.d[k]);
      if (r.error) errors.push(r.error); else { values[k] = r.value; if (r.warn) warnings.push(r.warn); }
    });
    if (errors.length) return { ok: false, errors: errors };
    return { ok: true, name: typeof obj.n === 'string' && obj.n ? obj.n : 'Shared profile', values: values, extras: { unknownPrefs: {}, settingsTop: {}, base: null }, warnings: warnings };
  }

  // ---------------------------------------------------------------------------------------------------- looks and starters

  function colour(hex) { return parseInt('FF' + hex.replace('#', ''), 16) >>> 0; }

  var LOOKS = [
    { id: 'midnight', name: 'Midnight', note: 'Deep blue gradient, violet accent', values: { themeMode: 'Dark', accent: colour('8E7BFF'), wpGlanceKind: 'gradient', wpGlanceFrom: colour('2A2F6B'), wpGlanceTo: colour('0B0B10'), wpGlanceAngle: 90, iconShape: 'Rounded', iconTint: 'None', uiFont: 'space_grotesk', 'dock.style': 'transparent' } },
    { id: 'paper', name: 'Paper', note: 'Light, warm solid colour, round icons', values: { themeMode: 'Light', accent: colour('D9534F'), wpGlanceKind: 'solid', wpGlanceSolid: colour('F1EBDD'), iconShape: 'Circle', iconTint: 'None', uiFont: 'manrope', 'dock.style': 'standard', clockStyle: 'AnalogMinimal' } },
    { id: 'terminal', name: 'Terminal', note: 'AMOLED black, green accent, square mono icons', values: { themeMode: 'Amoled', accent: colour('39FF88'), wpGlanceKind: 'solid', wpGlanceSolid: colour('000000'), iconShape: 'Square', iconTint: 'Mono', uiFont: 'jetbrains_mono', 'dock.style': 'transparent', clockStyle: 'BigDigitalLine' } },
    { id: 'sunset', name: 'Sunset', note: 'Warm gradient, squircle icons, 3D dock', values: { themeMode: 'Dark', accent: colour('FFB347'), wpGlanceKind: 'gradient', wpGlanceFrom: colour('5B2A6B'), wpGlanceTo: colour('FF7A3D'), wpGlanceAngle: 60, iconShape: 'Squircle', iconTint: 'None', uiFont: 'lexend', 'dock.style': '3d' } },
  ];

  var STARTERS = [
    { id: 'work', name: 'Work', note: 'Light, tidy pages, names on, search leads with files', values: {
      themeMode: 'Light', accent: colour('2F6FED'), wpGlanceKind: 'solid', wpGlanceSolid: colour('EEF1F6'), clockStyle: 'BigDigitalLine', clockFormat: 'Hour24', glanceClockDate: true,
      homePages: 'Paginated', pCols: 4, pRows: 6, labels: true, iconShape: 'Rounded', 'dock.enabled': 'on', 'dock.style': 'standard', calmStyle: 'Regular',
      searchLayout: 'Flat', searchFiles: true, searchContacts: true, leftPage: 'Launchpad', launchpadRail: true, uiSounds: false, hapticStrength: 'light' } },
    { id: 'calm', name: 'Calm', note: 'Dark gradient, only the clock, two hotspots, soft edge light', values: {
      themeMode: 'Amoled', accent: colour('7FD6C2'), wpGlanceKind: 'gradient', wpGlanceFrom: colour('10243A'), wpGlanceTo: colour('050509'), wpGlanceAngle: 90, clockStyle: 'BigDigital', glanceClockSize: 'Large',
      homePages: 'Off', glanceHotspots: 'BottomRight,BottomLeft', 'dock.enabled': 'off', calmStyle: 'EdgeDots', indIntensity: 0.5, labels: false, uiSounds: false, hapticStrength: 'light', showMusic: true } },
    { id: 'desk', name: 'Desk', note: 'Dense continuous page, 3D dock, Orbit search', values: {
      themeMode: 'Dark', accent: colour('4FC3F7'), wpGlanceKind: 'live', homePages: 'Continuous', contCols: 6, scrollAxis: 'Long', labels: true, labelScale: 0.9, iconScale: 0.78, pCols: 5, pRows: 8,
      'dock.enabled': 'on', 'dock.style': '3d', searchLayout: 'Orbit', searchLayoutPinned: true, rightSide: 'DockRail', clockStyle: 'AnalogClassic', glanceClockSize: 'Medium', hapticStrength: 'medium' } },
  ];

  /** The defaults with the preset's values on top (checked with the same rules as a file; a bad preset is a bug the tests catch). */
  function applyPreset(ix, base, preset) {
    var out = Object.assign({}, base);
    Object.keys(preset.values).forEach(function (k) {
      var f = ix.byKey[k];
      if (!f) throw new Error('preset ' + preset.id + ' names unknown key ' + k);
      var r = checkValue(f, preset.values[k]);
      if (r.error) throw new Error('preset ' + preset.id + ': ' + r.error);
      out[k] = r.value;
    });
    return out;
  }

  // ---------------------------------------------------------------------------------------------------- phone profiles (wave 57)
  //
  // On the phone a profile (it was a workspace) can carry groups of settings: Backup.workspaces[i] has "carries" (group ids, see
  // schema.profileGroups) and "settings" (schema key -> the value exactly as the Backup holds it at that place: the prefs value, the
  // gesture string, the dock.settings value with enabled true/false/null, the haptic id). Studio reads them into its own values and
  // writes them back without touching anything else in the Backup.

  function profileKeys(ix, carries) {
    var out = [];
    (ix.schema.profileGroups || []).forEach(function (g) { if ((carries || []).indexOf(g.id) >= 0) g.keys.forEach(function (k) { if (out.indexOf(k) < 0) out.push(k); }); });
    return out;
  }

  function fromPhone(f, v) { return f.key === 'dock.enabled' ? readEnabled(v) : v; }
  function toPhone(f, v) { return f.key === 'dock.enabled' ? dockEnabledOut(v) : v; }

  /**
   * The phone profiles of a Backup: [{id, name, active, carries, keys, values, warnings}]. values holds Studio values for the keys the profile
   * has a value for; a key it carries without a value takes the phone's global value on the phone. A value of the wrong type is left out with a warning.
   */
  function phoneProfiles(ix, doc) {
    if (!doc || doc.format !== STATE_FORMAT || !Array.isArray(doc.workspaces)) return [];
    var active = doc.dock && doc.dock.activeId;
    return doc.workspaces.filter(function (w) { return w && typeof w === 'object'; }).map(function (w) {
      var carries = Array.isArray(w.carries) ? w.carries.filter(function (c) { return typeof c === 'string'; }) : [];
      var values = {}, warnings = [], s = w.settings && typeof w.settings === 'object' && !Array.isArray(w.settings) ? w.settings : {};
      Object.keys(s).forEach(function (k) {
        var f = ix.byKey[k];
        if (!f) { warnings.push(k + ' is not a setting this Studio knows; kept as it is'); return; }
        var r = f.key === 'dock.enabled' ? { value: fromPhone(f, s[k]) } : checkValue(f, s[k]);
        if (r.error) warnings.push(r.error); else values[k] = r.value;
      });
      return { id: String(w.id), name: String(w.name || ''), active: w.id === active, carries: carries, keys: profileKeys(ix, carries), values: values, warnings: warnings };
    });
  }

  /**
   * A copy of [doc] where phone profile [id] holds [values] (Studio values) for the keys it carries; keys it does not carry are ignored,
   * every other field of the Backup (and of the profile) is kept. Returns {ok, doc} or {ok:false, error}.
   */
  function setPhoneProfile(ix, doc, id, values) {
    var out = clone(doc);
    var w = (out.workspaces || []).filter(function (x) { return x && String(x.id) === String(id); })[0];
    if (!w) return { ok: false, error: 'No profile with the id ' + id + ' in this Backup' };
    var keys = profileKeys(ix, w.carries);
    var s = w.settings && typeof w.settings === 'object' && !Array.isArray(w.settings) ? w.settings : {};
    Object.keys(values || {}).forEach(function (k) {
      var f = ix.byKey[k];
      if (!f || keys.indexOf(k) < 0) return;
      s[k] = toPhone(f, values[k]);
    });
    w.settings = s;
    return { ok: true, doc: out };
  }

  // ---------------------------------------------------------------------------------------------------------- profiles

  function memoryStorage() { var m = {}; return { getItem: function (k) { return k in m ? m[k] : null; }, setItem: function (k, v) { m[k] = String(v); }, removeItem: function (k) { delete m[k]; } }; }

  function uid() { return 'p' + Math.random().toString(36).slice(2, 10); }
  function newExtras() { return { unknownPrefs: {}, settingsTop: {}, base: null }; }

  function Profiles(ix, storage) {
    this.ix = ix;
    this.storage = storage || memoryStorage();
    this.saveError = null;
    this.state = this.load();
  }
  Profiles.prototype.load = function () {
    var st = null;
    try { var raw = this.storage.getItem(STORE_KEY); if (raw) st = JSON.parse(raw); } catch (e) { st = null; }
    var d = defaults(this.ix);
    if (!st || !Array.isArray(st.profiles) || !st.profiles.length) st = { activeId: 'p1', profiles: [{ id: 'p1', name: 'My home', values: d, extras: newExtras() }] };
    // Fill in keys added since a profile was saved, drop values whose key left the schema.
    st.profiles.forEach(function (p) { p.values = Object.assign({}, d, p.values); p.extras = p.extras || newExtras(); p.name = p.name || 'Untitled'; p.id = p.id || uid(); });
    if (!st.profiles.some(function (p) { return p.id === st.activeId; })) st.activeId = st.profiles[0].id;
    return st;
  };
  Profiles.prototype.save = function () {
    try { this.storage.setItem(STORE_KEY, JSON.stringify(this.state)); this.saveError = null; return true; }
    catch (e) { this.saveError = 'This browser would not keep the profiles (storage is full or blocked). Export the file to keep your work.'; return false; }
  };
  Profiles.prototype.list = function () { return this.state.profiles; };
  Profiles.prototype.active = function () { var id = this.state.activeId; return this.state.profiles.filter(function (p) { return p.id === id; })[0] || this.state.profiles[0]; };
  Profiles.prototype.use = function (id) { if (this.state.profiles.some(function (p) { return p.id === id; })) this.state.activeId = id; this.save(); return this.active(); };
  Profiles.prototype.uniqueName = function (name) {
    var names = this.state.profiles.map(function (p) { return p.name; }), base = String(name || 'New profile').trim().slice(0, 60) || 'New profile', n = base, i = 2;
    while (names.indexOf(n) >= 0) n = base + ' ' + i++;
    return n;
  };
  Profiles.prototype.create = function (name, values, extras) {
    var p = { id: uid(), name: this.uniqueName(name), values: Object.assign(defaults(this.ix), values || {}), extras: extras || newExtras() };
    this.state.profiles.push(p); this.state.activeId = p.id; this.save(); return p;
  };
  Profiles.prototype.duplicate = function () {
    var src = this.active();
    return this.create(src.name + ' copy', clone(src.values), clone(src.extras));
  };
  Profiles.prototype.rename = function (name, id) {
    var p = id ? this.state.profiles.filter(function (x) { return x.id === id; })[0] : this.active();
    if (!p) return null;
    var t = String(name || '').trim().slice(0, 60) || 'Untitled';
    if (t !== p.name) { p.name = this.state.profiles.some(function (x) { return x !== p && x.name === t; }) ? this.uniqueName(t) : t; }
    this.save(); return p;
  };
  /** Deletes a profile; the last one cannot be deleted. Returns false then. */
  Profiles.prototype.remove = function (id) {
    if (this.state.profiles.length <= 1) return false;
    var target = id || this.state.activeId;
    this.state.profiles = this.state.profiles.filter(function (p) { return p.id !== target; });
    if (this.state.activeId === target) this.state.activeId = this.state.profiles[0].id;
    this.save(); return true;
  };
  Profiles.prototype.set = function (key, value) {
    var f = this.ix.byKey[key]; if (!f || f.deviceBound) return false;
    this.active().values[key] = value; this.save(); return true;
  };
  Profiles.prototype.reset = function (key) { var f = this.ix.byKey[key]; if (!f) return false; this.active().values[key] = f.default; this.save(); return true; };
  Profiles.prototype.replaceValues = function (values) { this.active().values = Object.assign(defaults(this.ix), values); this.save(); };
  Profiles.prototype.changedKeys = function (profile) { var d = defaults(this.ix), v = (profile || this.active()).values; return Object.keys(d).filter(function (k) { return JSON.stringify(v[k]) !== JSON.stringify(d[k]); }); };

  return {
    SETTINGS_FORMAT: SETTINGS_FORMAT, STATE_FORMAT: STATE_FORMAT, CUSTOM: CUSTOM, STORE_KEY: STORE_KEY, UI_KEY: UI_KEY, MAX_SHARE_CHARS: MAX_SHARE_CHARS, CORNERS: CORNERS,
    indexSchema: indexSchema, editable: editable, defaults: defaults, checkValue: checkValue,
    importDocument: importDocument, exportSettings: exportSettings, exportState: exportState, toText: toText, slug: slug,
    shareFragment: shareFragment, readShare: readShare, LOOKS: LOOKS, STARTERS: STARTERS, applyPreset: applyPreset,
    Profiles: Profiles, memoryStorage: memoryStorage, newExtras: newExtras,
    phoneProfiles: phoneProfiles, setPhoneProfile: setPhoneProfile, profileKeys: profileKeys,
  };
});
