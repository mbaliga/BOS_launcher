"use strict";
const test = require("node:test"), assert = require("node:assert/strict");
const I = require("../qml/AppInventory.js");

test("parseDesktopEntry reads the Desktop Entry group only", () => {
  const e = I.parseDesktopEntry("# c\n[Desktop Entry]\nName=Clock\nName[de]=Uhr\nIcon=clock\nExec=clock %u\nType=Application\n\n[Desktop Action x]\nName=Other\n");
  assert.deepEqual([e.name, e.icon, e.exec], ["Clock", "clock", "clock %u"]);
  assert.equal(I.isListable(e), true);
  assert.equal(I.parseDesktopEntry("Name=x\n"), null);
});
test("NoDisplay / Hidden / non-Application are not listable", () => {
  for (const extra of ["NoDisplay=true", "Hidden=True", "Type=Link"]) {
    const e = I.parseDesktopEntry(`[Desktop Entry]\nName=A\nType=Application\n${extra}\n`);
    assert.equal(I.isListable(e), false, extra);
  }
  assert.equal(I.isListable(I.parseDesktopEntry("[Desktop Entry]\nType=Application\n")), false);
});
test("clickAppIdUrl maps click desktop file names, rejects others", () => {
  assert.equal(I.clickAppIdUrl("dekko.dekkoproject_dekko_0.6.22.desktop"), "appid://dekko.dekkoproject/dekko/0.6.22");
  assert.equal(I.clickAppIdUrl("firefox.desktop"), "");
  assert.equal(I.clickAppIdUrl("a_b.desktop"), "");
});
test("sort and search: prefix before substring", () => {
  const l = I.sortEntries([{ name: "Maps", file: "b" }, { name: "calendar", file: "a" }, { name: "Camera", file: "c" }, { name: "Music", file: "d" }]);
  assert.deepEqual(l.map(x => x.name), ["calendar", "Camera", "Maps", "Music"]);
  assert.deepEqual(I.search(l, "ma").map(x => x.name), ["Maps"]);
  assert.deepEqual(I.search(l, "a").map(x => x.name), ["calendar", "Camera", "Maps"]);
  assert.equal(I.search(l, "  ").length, 4);
});
test("demo entries are flagged demo and unlaunchable", () => {
  const d = I.demoEntries(); assert.ok(d.length > 0); assert.ok(d.every(e => e.demo && e.exec === ""));
});
