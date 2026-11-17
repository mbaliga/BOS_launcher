import QtQuick 2.12
import Qt.labs.folderlistmodel 2.12
import "AppInventory.js" as Inv

// U0 confinement spike. Tries to LIST and READ .desktop files from the places a Lomiri device keeps
// them and reports exactly what the AppArmor profile allowed. It cannot know the answer in advance;
// the verdict on screen is the evidence, and it must be copied into docs/DEVICE_CHECKLIST_UT.md.
Rectangle {
    id: spike
    color: "#0b0d12"
    signal finished(var found)

    property var dirs: [ "file:///usr/share/applications",
                         "file:///home/phablet/.local/share/applications",
                         "file:///usr/share/click/preinstalled" ]
    property int dirIndex: 0
    property var log: []
    property var found: []
    property int pending: 0

    function note(s) { log = log.concat([s]); }

    FolderListModel {
        id: fl
        nameFilters: ["*.desktop"]
        showDirs: false
        folder: spike.dirs[spike.dirIndex]
        onStatusChanged: if (status === FolderListModel.Ready) spike.scan()
    }

    function scan() {
        var d = dirs[dirIndex];
        note(d + ": listing OK, " + fl.count + " .desktop files");
        for (var i = 0; i < fl.count; i++) readOne(fl.get(i, "fileURL").toString(), fl.get(i, "fileName"));
        if (fl.count === 0) next();
    }
    function readOne(url, name) {
        pending++;
        var x = new XMLHttpRequest();
        x.onreadystatechange = function () {
            if (x.readyState !== XMLHttpRequest.DONE) return;
            var e = x.responseText ? Inv.parseDesktopEntry(x.responseText) : null;
            if (e && Inv.isListable(e)) { e.file = name; found = found.concat([e]); }
            else if (!x.responseText) note("read denied/empty: " + name);
            if (--pending === 0) next();
        };
        x.open("GET", url); x.send();
    }
    property bool complete: false
    function next() {
        if (complete) return;
        if (dirIndex + 1 < dirs.length) { dirIndex++; watchdog.restart(); return; }
        complete = true; watchdog.stop();
        note("VERDICT: " + (found.length > 0 ? "inventory readable (" + found.length + " listable apps)"
                                             : "NO readable inventory - U1 picker has nothing to list; fall back to demo Click (U2)"));
    }
    Timer { id: watchdog; interval: 8000; running: true
            onTriggered: { note("TIMEOUT on " + spike.dirs[spike.dirIndex] + " (never Ready; possibly denied by AppArmor)"); spike.next(); } }

    Column { anchors { fill: parent; margins: 12 } spacing: 4
        Text { text: "U0 confinement spike"; color: "#35E0FF"; font.pixelSize: 16 }
        Repeater { model: spike.log; Text { width: spike.width - 24; wrapMode: Text.WrapAnywhere; text: modelData; color: "#e8eaf2"; font.pixelSize: 11 } }
        Text { text: "Tap to close and use result"; color: "#7b8299"; font.pixelSize: 12 }
    }
    MouseArea { anchors.fill: parent; onClicked: spike.finished(spike.found) }
}
