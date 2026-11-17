// Pure helpers for the U0/U1 inventory path. No Qt imports (node-tested).
// What is NOT known: whether a confined Click app is allowed to read the .desktop files these
// helpers parse. The spike (qml/SpikePage.qml) finds out on a device; until then the picker shows
// clearly-labelled demo entries and never pretends they are installed apps.

// Parse the [Desktop Entry] group of a freedesktop .desktop file (Name[xx] localisations ignored
// except as a fallback). Returns null when there is no [Desktop Entry] group.
function parseDesktopEntry(text) {
    var lines = String(text).split(/\r?\n/), inGroup = false, found = false, e = {};
    for (var i = 0; i < lines.length; i++) {
        var l = lines[i].trim();
        if (l === "" || l[0] === "#") continue;
        if (l[0] === "[") { inGroup = (l === "[Desktop Entry]"); found = found || inGroup; continue; }
        if (!inGroup) continue;
        var eq = l.indexOf("="); if (eq < 0) continue;
        var key = l.slice(0, eq).trim(), val = l.slice(eq + 1).trim();
        if (key.indexOf("[") >= 0) continue;
        e[key] = val;
    }
    if (!found) return null;
    return { name: e.Name || "", icon: e.Icon || "", exec: e.Exec || "", type: e.Type || "Application",
             noDisplay: /^true$/i.test(e.NoDisplay || ""), hidden: /^true$/i.test(e.Hidden || ""),
             onlyShowIn: e.OnlyShowIn ? e.OnlyShowIn.split(";").filter(Boolean) : [],
             notShowIn: e.NotShowIn ? e.NotShowIn.split(";").filter(Boolean) : [] };
}

function isListable(entry) {
    return !!entry && entry.type === "Application" && entry.name !== "" && !entry.noDisplay && !entry.hidden;
}

// Click installs name desktop files "<package>_<app>_<version>.desktop"; Lomiri launches them with
// appid://<package>/<app>/<version> (url-dispatcher policy group). Returns "" if not a click name.
function clickAppIdUrl(desktopFileName) {
    var base = String(desktopFileName).replace(/\.desktop$/, "");
    var m = /^([a-z0-9][a-z0-9+.-]*)_([a-zA-Z0-9][a-zA-Z0-9+.-]*)_([0-9][A-Za-z0-9.+~:-]*)$/.exec(base);
    return m ? "appid://" + m[1] + "/" + m[2] + "/" + m[3] : "";
}

// Alphabetical, case-insensitive, stable on ties by file name.
function sortEntries(list) {
    return list.slice().sort(function (a, b) {
        var x = a.name.toLowerCase(), y = b.name.toLowerCase();
        return x < y ? -1 : x > y ? 1 : (a.file < b.file ? -1 : a.file > b.file ? 1 : 0);
    });
}

// Prototype-equivalent type-to-search: prefix matches rank above substring matches.
function search(list, q) {
    q = String(q).trim().toLowerCase();
    if (!q) return list.slice();
    var pre = [], sub = [];
    list.forEach(function (e) {
        var n = e.name.toLowerCase();
        if (n.indexOf(q) === 0) pre.push(e); else if (n.indexOf(q) > 0) sub.push(e);
    });
    return pre.concat(sub);
}

// The prototype's demo inventory, shown ONLY when no real inventory is available, and labelled so.
var DEMO_NAMES = ['Adobe','Authenticator','Bank','Books','Browser','Calculator','Calendar','Camera','Chat','Clock','Cloud','Contacts','Drive','Files','Fitness'];
function demoEntries() {
    return DEMO_NAMES.map(function (n) { return { name: n, icon: "", exec: "", file: "", demo: true }; });
}

if (typeof module !== "undefined" && module.exports) {
    module.exports = { parseDesktopEntry: parseDesktopEntry, isListable: isListable, clickAppIdUrl: clickAppIdUrl,
        sortEntries: sortEntries, search: search, demoEntries: demoEntries };
}
