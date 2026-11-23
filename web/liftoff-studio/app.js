/* Liftoff Studio page: wires the schema, the profiles, the preview and the dialogs. All logic that can be tested without a browser is in core.js. */
(function () {
  'use strict';
  var Core = window.StudioCore, Prev = window.StudioPreview, Cards = window.StudioCards, SCHEMA = window.LIFTOFF_SCHEMA;
  var ix = Core.indexSchema(SCHEMA);
  var $ = function (id) { return document.getElementById(id); };
  function el(tag, attrs, kids) {
    var e = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      if (k === 'class') e.className = attrs[k]; else if (k === 'text') e.textContent = attrs[k]; else if (k === 'html') e.innerHTML = attrs[k];
      else if (k.slice(0, 2) === 'on') e.addEventListener(k.slice(2), attrs[k]); else if (attrs[k] === true) e.setAttribute(k, ''); else if (attrs[k] !== false && attrs[k] != null) e.setAttribute(k, attrs[k]);
    });
    (kids || []).forEach(function (c) { if (c) e.appendChild(typeof c === 'string' ? document.createTextNode(c) : c); });
    return e;
  }

  // ---- storage (a browser can refuse it: then the page still works, it just cannot keep anything)
  var storage, storageOk = true;
  try { storage = window.localStorage; storage.setItem('liftoff_studio_probe', '1'); storage.removeItem('liftoff_studio_probe'); } catch (e) { storage = Core.memoryStorage(); storageOk = false; }
  var profiles = new Core.Profiles(ix, storage);
  var ui = { tab: 'home', scene: 'glance', follow: true, theme: 'auto', adv: {} };
  try { var saved = JSON.parse(storage.getItem(Core.UI_KEY) || '{}'); ['tab', 'theme', 'follow'].forEach(function (k) { if (saved[k] !== undefined) ui[k] = saved[k]; }); } catch (e) { /* defaults */ }
  if (!ix.tabs.some(function (t) { return t.id === ui.tab; })) ui.tab = 'home';
  function saveUi() { try { storage.setItem(Core.UI_KEY, JSON.stringify({ tab: ui.tab, theme: ui.theme, follow: ui.follow })); } catch (e) { /* not kept */ } }
  var undoSnapshot = null;

  function vals() { return profiles.active().values; }
  function fmtVal(f, v) {
    if (f.type === 'int' || f.type === 'float') {
      var pct = f.unit === '%' && f.max <= 2;
      var n = pct ? Math.round(v * 100) : (f.type === 'int' ? v : Math.round(v * 100) / 100);
      return n + (f.unit === 'deg' ? '°' : f.unit ? (f.unit === '%' ? '%' : ' ' + f.unit) : '');
    }
    return String(v);
  }
  function showWhenOk(f) {
    if (!f.showWhen) return true;
    var cur = vals()[f.showWhen.key];
    return f.showWhen.in.indexOf(String(cur)) >= 0;
  }
  function toast(msg) { var t = $('toast'); t.textContent = msg; t.classList.add('show'); clearTimeout(toast.t); toast.t = setTimeout(function () { t.classList.remove('show'); }, 3200); }
  function argbHex(n) { return '#' + (n >>> 0).toString(16).padStart(8, '0').slice(2); }
  function hexArgb(h) { return (parseInt('FF' + h.replace('#', ''), 16)) >>> 0; }

  // ---- preview
  var renderQueued = false;
  function renderPreview() {
    if (renderQueued) return; renderQueued = true;
    requestAnimationFrame(function () {
      renderQueued = false;
      $('phone').innerHTML = Prev.render(ui.scene, vals());
      var sb = $('scenes'); Array.prototype.forEach.call(sb.children, function (b) { b.setAttribute('aria-pressed', String(b.dataset.scene === ui.scene)); });
      $('caption').textContent = captionFor(ui.scene);
    });
  }
  function captionFor(scene) {
    var v = vals();
    if (scene === 'glance') return v.glanceEnabled === false ? 'Glance is off: the first page is the home.' : 'The Glance: wallpaper, clock, music line, hotspots, calm style and dock.';
    if (scene === 'pages') return v.homePages === 'Off' ? 'Pages are Off, so home is the Glance only. This is how the first page would look.' : v.homePages === 'Continuous' ? 'The scroll after the Glance, column by column.' : 'A page after the Glance.';
    if (scene === 'radar') return 'Radar, search. The layout is a sketch, the results are samples.';
    if (scene === 'disc') return 'The app drawer disc with its shortcut ring at the bottom left corner.';
    if (scene === 'comms') return 'Comms, the side panel left of home (shown over the home card).';
    return 'The Recents panel, or the dock rail, on the other side of home.';
  }
  function setScene(s, fromField) { if (ui.scene !== s) { ui.scene = s; renderPreview(); } else if (!fromField) renderPreview(); }
  function sceneOfField(f) { return Prev.sceneFor(f, f.tab); }

  // ---- controls
  function row(f, label, ctl, stack, extraLab) {
    var changed = JSON.stringify(vals()[f.key]) !== JSON.stringify(f.default);
    var lab = el('div', { class: 'lab' }, [el('b', { text: label || f.label }), f.note ? el('small', { text: f.note }) : null]);
    if (f.scope !== 'settings') lab.firstChild.appendChild(el('span', { class: 'tag', text: 'Backup file', title: 'Travels in the Backup file from Export, not in the settings-only file' }));
    var reset = el('button', { type: 'button', class: 'reset', text: 'Reset', hidden: !changed, 'aria-label': 'Reset ' + f.label + ' to the default', onclick: function () { profiles.reset(f.key); afterChange(f); renderPanel(); } });
    var r = el('div', { class: 'row' + (stack ? ' stack' : ''), 'data-key': f.key }, [lab, el('div', { class: 'ctl' }, [ctl, stack ? null : reset])]);
    if (stack) { lab.appendChild(reset); }
    r.addEventListener('focusin', function () { if (ui.follow) setScene(sceneOfField(f), true); });
    return r;
  }
  function afterChange(f) {
    if (storage && profiles.saveError) toast(profiles.saveError);
    if (ui.follow && f) setScene(sceneOfField(f), true);
    renderPreview(); updateProfileBar();
  }
  function set(f, v) { profiles.set(f.key, v); afterChange(f); var r = document.querySelector('.row[data-key="' + f.key.replace(/"/g, '') + '"] .reset'); if (r) r.hidden = JSON.stringify(v) === JSON.stringify(f.default); }

  function boolCtl(f) {
    var cur = vals()[f.key] === true;
    var inp = el('input', { type: 'checkbox', role: 'switch', id: 'c_' + f.key, 'aria-label': f.label, onchange: function () { set(f, inp.checked); refreshConditional(); } });
    inp.checked = cur;
    return el('span', { class: 'sw' }, [inp, el('i')]);
  }
  function cardsCtl(f) {
    var cur = vals()[f.key], wrap = el('div', { class: 'cards', role: 'radiogroup', 'aria-label': f.label });
    var opts = f.options.slice();
    if (cur === Core.CUSTOM) opts.push({ id: Core.CUSTOM, label: 'Custom (kept)' });
    else if (!opts.some(function (o) { return o.id === cur; })) opts.push({ id: String(cur), label: 'Unknown: ' + cur });
    opts.forEach(function (o) {
      var art = o.id === Core.CUSTOM ? null : Cards.card(f.key, o.id, vals());
      var inp = el('input', { type: 'radio', name: 'r_' + f.key, value: o.id, onchange: function () { set(f, o.id); refreshConditional(); } });
      inp.checked = o.id === cur;
      wrap.appendChild(el('label', { class: 'cardopt' + (art ? '' : ' textonly') }, [inp, art ? el('span', { class: 'art', 'aria-hidden': 'true', html: '<svg viewBox="0 0 64 44" focusable="false">' + art + '</svg>' }) : null, el('span', { text: o.label })]));
    });
    return wrap;
  }
  function selectCtl(f) {
    var cur = vals()[f.key], sel = el('select', { id: 'c_' + f.key, 'aria-label': f.label, onchange: function () { set(f, sel.value); refreshConditional(); } });
    f.options.forEach(function (o) { sel.appendChild(el('option', { value: o.id, text: o.label })); });
    if (cur === Core.CUSTOM) sel.appendChild(el('option', { value: Core.CUSTOM, text: 'Custom (kept as it is)' }));
    else if (!f.options.some(function (o) { return o.id === cur; })) sel.appendChild(el('option', { value: String(cur), text: 'Unknown: ' + cur }));
    sel.value = String(cur);
    return sel;
  }
  function sliderCtl(f) {
    var cur = vals()[f.key], pct = f.unit === '%' && f.max <= 2, out = el('output', { class: 'readout', for: 'c_' + f.key });
    var inp = el('input', { type: 'range', id: 'c_' + f.key, min: f.min, max: f.max, step: f.step || (f.type === 'int' ? 1 : 0.01), 'aria-label': f.label,
      oninput: function () { var n = f.type === 'int' ? Math.round(Number(inp.value)) : Number(inp.value); out.textContent = fmtVal(f, n); profiles.active().values[f.key] = n; afterChange(f); },
      onchange: function () { var n = f.type === 'int' ? Math.round(Number(inp.value)) : Number(inp.value); set(f, n); refreshConditional(); } });
    inp.value = cur; out.textContent = fmtVal(f, cur);
    return el('div', { class: 'slider' }, [inp, out]);
  }
  function colourCtl(f) {
    var cur = vals()[f.key], hex = el('span', { class: 'hex', text: argbHex(cur) });
    var inp = el('input', { type: 'color', id: 'c_' + f.key, 'aria-label': f.label, oninput: function () { hex.textContent = inp.value; profiles.active().values[f.key] = hexArgb(inp.value); afterChange(f); }, onchange: function () { set(f, hexArgb(inp.value)); } });
    inp.value = argbHex(cur);
    return el('div', { class: 'ctl' }, [inp, hex]);
  }
  var CORNER_LABEL = { TopLeft: 'Top left', TopRight: 'Top right', BottomRight: 'Bottom right', BottomLeft: 'Bottom left' };
  function cornersCtl(f) {
    var on = (vals()[f.key] || '').split(',').filter(Boolean), wrap = el('div');
    var grid = el('div', { class: 'hotspots', role: 'group', 'aria-label': f.label });
    Core.CORNERS.forEach(function (c) {
      var act = vals()['corners.' + c], gk = Prev.helpers.glyphKey(act === Core.CUSTOM ? 'action:AllApps' : act);
      var inp = el('input', { type: 'checkbox', onchange: function () { var now = Core.CORNERS.filter(function (x) { return x === c ? inp.checked : on.indexOf(x) >= 0; }); on = now; set(f, now.join(',')); renderPanelSoon(); } });
      inp.checked = on.indexOf(c) >= 0;
      grid.appendChild(el('label', { class: 'hot' }, [inp, el('span', { html: '<svg viewBox="0 0 24 24" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + Prev.helpers.GLYPHS[gk] + '</svg>' }), CORNER_LABEL[c]]));
    });
    var presets = [['Four', Core.CORNERS], ['Two (bottom)', ['BottomRight', 'BottomLeft']], ['One', ['BottomRight']], ['None', []]], pr = el('div', { class: 'presetrow' });
    presets.forEach(function (p) {
      var key = Core.CORNERS.filter(function (c) { return p[1].indexOf(c) >= 0; }).join(',');
      pr.appendChild(el('button', { type: 'button', class: 'pill', 'aria-pressed': String(key === vals()[f.key]), text: p[0], onclick: function () { set(f, key); renderPanel(); } }));
    });
    wrap.appendChild(grid); wrap.appendChild(pr); return wrap;
  }
  function slotsCtl(f) {
    var cur = String(vals()[f.key]).split('\n'), seg = vals().puckSegments || 8, grid = el('div', { class: 'slotgrid' });
    for (var i = 0; i < f.count; i++) (function (i) {
      var sel = el('select', { 'aria-label': 'Segment ' + (i + 1), onchange: function () { var a = String(vals()[f.key]).split('\n'); a[i] = sel.value; set(f, a.join('\n')); } });
      f.options.forEach(function (o) { sel.appendChild(el('option', { value: o.id, text: o.label })); });
      if (cur[i] === Core.CUSTOM || (cur[i] && !f.options.some(function (o) { return o.id === cur[i]; }))) sel.appendChild(el('option', { value: cur[i], text: 'App or custom (kept)' }));
      sel.value = cur[i] || '-';
      grid.appendChild(el('label', { class: i < seg ? '' : 'off' }, ['Segment ' + (i + 1) + (i < seg ? '' : ' (hidden at ' + seg + ')'), sel]));
    })(i);
    return grid;
  }
  function textCtl(f) { var inp = el('input', { type: 'text', value: vals()[f.key] || '', 'aria-label': f.label, onchange: function () { set(f, inp.value); } }); return inp; }

  function buildField(f) {
    var count = f.options ? f.options.length : 0, v = vals()[f.key];
    if (f.type === 'bool') return row(f, f.label, boolCtl(f));
    if (f.type === 'enum') return count <= 9 && count > 0 ? row(f, f.label, cardsCtl(f), true) : row(f, f.label, selectCtl(f));
    if (f.type === 'int' || f.type === 'float') return row(f, f.label, sliderCtl(f));
    if (f.type === 'colour') return row(f, f.label, colourCtl(f));
    if (f.type === 'corners') return row(f, f.label, cornersCtl(f), true);
    if (f.type === 'slots') return row(f, f.label, slotsCtl(f), true);
    return row(f, f.label, textCtl(f));
  }

  // ---- the panel
  var conditionalQueued = false;
  function refreshConditional() { if (conditionalQueued) return; conditionalQueued = true; setTimeout(function () { conditionalQueued = false; var y = window.scrollY; renderPanel(true); window.scrollTo(0, y); }, 0); }
  function renderPanelSoon() { setTimeout(function () { renderPanel(true); }, 0); }
  function renderTabs() {
    var t = $('tabs'); t.innerHTML = '';
    ix.tabs.forEach(function (tab) {
      t.appendChild(el('button', { type: 'button', role: 'tab', class: 'tab', id: 'tab_' + tab.id, 'aria-selected': String(tab.id === ui.tab), 'aria-controls': 'tabPanel', tabindex: tab.id === ui.tab ? '0' : '-1', text: tab.label,
        onclick: function () { selectTab(tab.id); },
        onkeydown: function (e) {
          var i = ix.tabs.findIndex(function (x) { return x.id === ui.tab; }), n = null;
          if (e.key === 'ArrowRight') n = (i + 1) % ix.tabs.length; else if (e.key === 'ArrowLeft') n = (i + ix.tabs.length - 1) % ix.tabs.length; else if (e.key === 'Home') n = 0; else if (e.key === 'End') n = ix.tabs.length - 1;
          if (n !== null) { e.preventDefault(); selectTab(ix.tabs[n].id); $('tab_' + ix.tabs[n].id).focus(); }
        } }));
    });
  }
  function selectTab(id) { ui.tab = id; saveUi(); renderTabs(); renderPanel(); if (ui.follow) setScene(Prev.TAB_SCENE[id] || 'glance'); $('tabPanel').setAttribute('aria-labelledby', 'tab_' + id); }

  function renderPanel(keepFocus) {
    var focusKey = keepFocus && document.activeElement && document.activeElement.closest ? (document.activeElement.closest('.row') || {}).dataset : null;
    var tab = ix.tabs.filter(function (t) { return t.id === ui.tab; })[0], root = $('tabPanel'); root.innerHTML = '';
    root.appendChild(el('p', { class: 'blurb', text: tab.blurb }));
    var groups = [], seen = {};
    ix.fields.forEach(function (f) { if (f.tab !== ui.tab || f.deviceBound) return; if (!seen[f.group]) { seen[f.group] = { name: f.group, fields: [] }; groups.push(seen[f.group]); } seen[f.group].fields.push(f); });
    groups.forEach(function (gr) {
      var sec = el('section', { class: 'group' }, [el('h2', { text: gr.name })]);
      var main = gr.fields.filter(function (f) { return !f.advanced && showWhenOk(f); }), adv = gr.fields.filter(function (f) { return f.advanced && showWhenOk(f); });
      main.forEach(function (f) { sec.appendChild(buildField(f)); });
      if (adv.length) {
        var d = el('details', { class: 'more' }, [el('summary', { text: 'More (' + adv.length + ')' })]);
        d.open = !!ui.adv[gr.name]; d.addEventListener('toggle', function () { ui.adv[gr.name] = d.open; });
        adv.forEach(function (f) { d.appendChild(buildField(f)); });
        sec.appendChild(d);
      }
      if (main.length || adv.length) root.appendChild(sec);
    });
    var bound = ix.fields.filter(function (f) { return f.tab === ui.tab && f.deviceBound; });
    if (ui.tab === 'apps') root.appendChild(el('div', { class: 'note', html: '<b>Workspaces and app groups</b> are made on the phone, with the apps you have installed. They are not edited here. When you import a full Backup into Studio and export it again, they travel through unchanged.' }));
    if (ui.tab === 'system') {
      root.appendChild(el('div', { class: 'note', html: '<b>Backup and access.</b> Export makes the file the app\'s Backup imports, and Import reads a file the app made. Permissions (notifications, usage access, the default launcher) are granted on the phone and cannot be set from a file.' }));
      root.appendChild(el('div', { class: 'summary' }, [
        el('span', { text: profiles.changedKeys().length + ' settings differ from the app defaults.' }),
        el('button', { type: 'button', class: 'btn', text: 'Reset this profile', onclick: function () { resetProfile(); } }),
      ]));
    }
    if (bound.length || ui.tab === 'system') {
      var all = ix.fields.filter(function (f) { return f.deviceBound; });
      if (ui.tab === 'system') root.appendChild(el('div', { class: 'note', html: '<b>Stays on the phone.</b> These are never in an export and Studio does not touch them: the first-run tour state, the widget on the left page, and the picture files of the wallpapers (' + all.length + ' settings in all). A restored phone with a Picture wallpaper but no file draws the solid colour.' }));
    }
    root.setAttribute('aria-labelledby', 'tab_' + ui.tab);
    if (focusKey && focusKey.key) { var again = document.querySelector('.row[data-key="' + focusKey.key + '"] input, .row[data-key="' + focusKey.key + '"] select'); if (again && again.focus) try { again.focus({ preventScroll: true }); } catch (e) { /* ignore */ } }
  }

  // ---- profile bar and dialogs
  function updateProfileBar() {
    var sel = $('profileSel'); sel.innerHTML = '';
    profiles.list().forEach(function (p) { sel.appendChild(el('option', { value: p.id, text: p.name })); });
    sel.value = profiles.active().id;
  }
  function fullRender() { updateProfileBar(); renderTabs(); renderPanel(); renderPreview(); }

  function openDlg(id) { var d = $(id); if (d.showModal) d.showModal(); else d.setAttribute('open', ''); return d; }
  function describeP(p) { return profiles.changedKeys(p).length + ' changed'; }
  function renderProfileList() {
    var ul = $('profileList'); ul.innerHTML = '';
    profiles.list().forEach(function (p) {
      var li = el('li', { 'aria-current': String(p.id === profiles.active().id) }, [
        el('span', { class: 'pname', text: p.name }), el('span', { class: 'count', text: describeP(p) }),
        el('button', { type: 'button', class: 'btn', text: p.id === profiles.active().id ? 'In use' : 'Use', disabled: p.id === profiles.active().id, onclick: function () { profiles.use(p.id); fullRender(); renderProfileList(); } }),
      ]);
      ul.appendChild(li);
    });
  }
  function renderPresets() {
    var l = $('looks'), s = $('starters'); l.innerHTML = ''; s.innerHTML = '';
    Core.LOOKS.forEach(function (lk) { l.appendChild(el('button', { type: 'button', class: 'chip', onclick: function () { applyPresetToCurrent(lk); } }, [el('b', { text: lk.name }), el('small', { text: lk.note })])); });
    Core.STARTERS.forEach(function (st) { s.appendChild(el('button', { type: 'button', class: 'chip', onclick: function () { var p = profiles.create(st.name, Core.applyPreset(ix, Core.defaults(ix), st)); fullRender(); renderProfileList(); toast('Made the profile ' + p.name); } }, [el('b', { text: st.name }), el('small', { text: st.note })])); });
  }
  function applyPresetToCurrent(preset) {
    undoSnapshot = JSON.parse(JSON.stringify(vals()));
    profiles.replaceValues(Core.applyPreset(ix, vals(), preset)); fullRender(); renderProfileList(); $('pUndo').hidden = false; toast('Applied ' + preset.name);
  }
  function resetProfile() { undoSnapshot = JSON.parse(JSON.stringify(vals())); profiles.replaceValues(Core.defaults(ix)); fullRender(); renderProfileList(); $('pUndo').hidden = false; toast('Reset to the app defaults'); }

  function renameInline() {
    var li = $('profileList').querySelector('li[aria-current="true"]'), name = li.querySelector('.pname'), inp = el('input', { type: 'text', value: profiles.active().name, 'aria-label': 'Profile name', maxlength: 60 });
    name.replaceWith(inp); inp.focus(); inp.select();
    var done = false, finish = function (ok) { if (done) return; done = true; if (ok) profiles.rename(inp.value); fullRender(); renderProfileList(); };
    inp.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); finish(true); } else if (e.key === 'Escape') { e.stopPropagation(); e.preventDefault(); finish(false); } });
    inp.addEventListener('blur', function () { finish(true); });
  }

  // import
  function showMsg(id, errors, warnings, ok) {
    var m = $(id); m.className = 'msg' + (errors && errors.length ? ' err' : ok ? ' ok' : ''); m.innerHTML = '';
    if (errors && errors.length) { m.appendChild(el('div', { text: 'This file was not imported:' })); m.appendChild(el('ul', {}, errors.slice(0, 8).map(function (e) { return el('li', { text: e }); }))); if (errors.length > 8) m.appendChild(el('div', { text: '... and ' + (errors.length - 8) + ' more' })); }
    else if (ok) m.appendChild(el('div', { text: ok }));
    if (warnings && warnings.length) { m.appendChild(el('ul', { class: 'warn' }, warnings.slice(0, 6).map(function (w) { return el('li', { class: 'warn', text: w }); }))); }
  }
  var pendingFileName = '';
  function doImport(text, nameHint) {
    var where = document.querySelector('input[name="impWhere"]:checked').value, res;
    var t = String(text || '').trim();
    if (t.charAt(0) !== '{' && (t.indexOf('p=') >= 0 || /^[A-Za-z0-9_-]{20,}$/.test(t))) {
      res = Core.readShare(ix, t); if (res.ok) { res.kind = 'link'; }
    } else res = Core.importDocument(ix, t);
    if (!res.ok) { showMsg('impMsg', res.errors); return false; }
    var name = res.name || nameHint || 'Imported';
    if (where === 'current') { var p = profiles.active(); p.values = res.values; p.extras = res.extras; profiles.save(); }
    else profiles.create(name.replace(/\.json$/i, ''), res.values, res.extras);
    fullRender(); showMsg('impMsg', null, res.warnings, 'Imported ' + (res.kind === 'state' ? 'a Backup (settings, dock, corners and gestures read)' : res.kind === 'link' ? 'a shared profile' : 'a settings file') + '.');
    toast('Imported'); return true;
  }
  function readFile(file) {
    if (!file) return;
    if (file.size > 12 * 1024 * 1024) { showMsg('impMsg', ['The file is larger than 12 MB, which is not a Liftoff file']); return; }
    var fr = new FileReader(); fr.onerror = function () { showMsg('impMsg', ['The file could not be read']); };
    fr.onload = function () { pendingFileName = file.name; if (doImport(String(fr.result), file.name)) { /* done */ } };
    fr.readAsText(file);
  }

  // export
  function download(name, text) {
    var blob = new Blob([text], { type: 'application/json' }), a = el('a', { href: URL.createObjectURL(blob), download: name });
    document.body.appendChild(a); a.click(); setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 500);
  }
  function copyText(text, msgId) {
    var done = function () { toast('Copied'); }, fail = function () { showMsg(msgId, ['Copying was blocked by the browser. Select the text and copy it by hand, or use Download.']); };
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(done, fail); else fail();
  }
  function exportText(kind) { var p = profiles.active(); return Core.toText(kind === 'state' ? Core.exportState(ix, p) : Core.exportSettings(ix, p)); }
  function exportName(kind) { return 'liftoff-' + Core.slug(profiles.active().name) + (kind === 'state' ? '-backup' : '-settings') + '.json'; }

  // share
  function openShare() {
    var r = Core.shareFragment(ix, profiles.active()), out = $('shareOut'), m = $('shareMsg');
    if (!r.ok) { out.value = ''; showMsg('shareMsg', [r.error]); $('shareCopy').disabled = true; }
    else { var base = location.href.split('#')[0]; out.value = base + '#' + r.fragment; $('shareCopy').disabled = false; showMsg('shareMsg', null, null, out.value.length + ' characters. Anyone with this link gets a copy; nothing is stored on a server.'); }
    openDlg('dlgShare');
  }

  // theme
  function applyTheme() {
    var r = document.documentElement; if (ui.theme === 'auto') r.removeAttribute('data-theme'); else r.setAttribute('data-theme', ui.theme);
    var b = $('btnTheme'); b.textContent = 'Theme: ' + ui.theme; b.setAttribute('aria-label', 'Colour theme of this page: ' + ui.theme + '. Press to change.');
  }

  // ---- wiring
  function init() {
    applyTheme();
    var sb = $('scenes'); Prev.SCENES.forEach(function (s) { sb.appendChild(el('button', { type: 'button', 'data-scene': s.id, 'aria-pressed': String(s.id === ui.scene), text: s.label, onclick: function () { ui.scene = s.id; renderPreview(); } })); });
    $('follow').checked = ui.follow; $('follow').addEventListener('change', function () { ui.follow = $('follow').checked; saveUi(); });
    ui.scene = Prev.TAB_SCENE[ui.tab] || 'glance';
    $('profileSel').addEventListener('change', function () { profiles.use($('profileSel').value); fullRender(); });
    $('btnProfiles').addEventListener('click', function () { renderProfileList(); renderPresets(); openDlg('dlgProfiles'); });
    $('pNew').addEventListener('click', function () { profiles.create('New profile'); fullRender(); renderProfileList(); renameInline(); });
    $('pDup').addEventListener('click', function () { profiles.duplicate(); fullRender(); renderProfileList(); toast('Duplicated'); });
    $('pRename').addEventListener('click', renameInline);
    $('pDelete').addEventListener('click', function () {
      if (profiles.list().length <= 1) { toast('The last profile cannot be deleted'); return; }
      var p = profiles.active(); if (window.confirm('Delete the profile "' + p.name + '"? This cannot be undone.')) { profiles.remove(p.id); fullRender(); renderProfileList(); }
    });
    $('pReset').addEventListener('click', resetProfile);
    $('pUndo').addEventListener('click', function () { if (undoSnapshot) { profiles.replaceValues(undoSnapshot); undoSnapshot = null; fullRender(); renderProfileList(); $('pUndo').hidden = true; toast('Undone'); } });
    $('btnImport').addEventListener('click', function () { showMsg('impMsg', null, null, null); $('pasteIn').value = ''; openDlg('dlgImport'); });
    $('fileIn').addEventListener('change', function () { readFile($('fileIn').files[0]); $('fileIn').value = ''; });
    $('impGo').addEventListener('click', function () { doImport($('pasteIn').value, 'Pasted'); });
    var drop = $('drop');
    ['dragenter', 'dragover'].forEach(function (ev) { drop.addEventListener(ev, function (e) { e.preventDefault(); drop.classList.add('over'); }); });
    ['dragleave', 'drop'].forEach(function (ev) { drop.addEventListener(ev, function (e) { e.preventDefault(); drop.classList.remove('over'); }); });
    drop.addEventListener('drop', function (e) { readFile(e.dataTransfer && e.dataTransfer.files[0]); });
    // a file dropped anywhere on the page opens the import
    window.addEventListener('dragover', function (e) { e.preventDefault(); });
    window.addEventListener('drop', function (e) { if (e.target.closest && e.target.closest('#drop')) return; e.preventDefault(); if (e.dataTransfer && e.dataTransfer.files[0]) { showMsg('impMsg', null, null, null); openDlg('dlgImport'); readFile(e.dataTransfer.files[0]); } });
    $('btnExport').addEventListener('click', function () { showMsg('xMsg', null, null, null); openDlg('dlgExport'); });
    $('xSettings').addEventListener('click', function () { download(exportName('settings'), exportText('settings')); showMsg('xMsg', null, null, 'Downloaded ' + exportName('settings')); });
    $('xState').addEventListener('click', function () { download(exportName('state'), exportText('state')); showMsg('xMsg', null, null, 'Downloaded ' + exportName('state')); });
    $('xSettingsCopy').addEventListener('click', function () { copyText(exportText('settings'), 'xMsg'); });
    $('xStateCopy').addEventListener('click', function () { copyText(exportText('state'), 'xMsg'); });
    $('btnShare').addEventListener('click', openShare);
    $('shareCopy').addEventListener('click', function () { copyText($('shareOut').value, 'shareMsg'); });
    $('shareOut').addEventListener('focus', function () { $('shareOut').select(); });
    $('btnTheme').addEventListener('click', function () { ui.theme = ui.theme === 'auto' ? 'light' : ui.theme === 'light' ? 'dark' : 'auto'; applyTheme(); saveUi(); });
    // a profile in the link
    if (/^#p=/.test(location.hash)) {
      var r = Core.readShare(ix, location.hash);
      history.replaceState(null, '', location.pathname + location.search);
      var b = $('banner'); b.hidden = false;
      if (r.ok) {
        var made = profiles.create(r.name, r.values, r.extras); b.innerHTML = '';
        b.appendChild(document.createTextNode('A shared profile was opened from the link and added here as "' + made.name + '". ' + (r.warnings.length ? r.warnings.length + ' notes: ' + r.warnings.slice(0, 2).join('; ') + '. ' : '')));
        b.appendChild(el('button', { type: 'button', class: 'btn', text: 'Remove it', onclick: function () { profiles.remove(made.id); fullRender(); b.hidden = true; } })); b.appendChild(document.createTextNode(' '));
        b.appendChild(el('button', { type: 'button', class: 'btn', text: 'Dismiss', onclick: function () { b.hidden = true; } }));
      }
      else { b.textContent = 'The link could not be read: ' + r.errors.join('; '); }
    } else if (!storageOk) { var b2 = $('banner'); b2.hidden = false; b2.textContent = 'This browser is not letting the page keep data, so profiles last until you close the page. Use Export to keep your work.'; }
    fullRender();
    $('tabPanel').setAttribute('aria-labelledby', 'tab_' + ui.tab);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
  window.__studio = { profiles: profiles, ix: ix, get ui() { return ui; }, selectTab: selectTab, renderPreview: renderPreview, setScene: function (s) { ui.scene = s; renderPreview(); } };
}());
