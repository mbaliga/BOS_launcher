import QtQuick 2.12
import "SphereMath.js" as SM
import "AppInventory.js" as Inv

// Liftoff app picker (Ubuntu Touch REFRAME of the Android launcher; not a launcher/home).
// Plain QtQuick only (no Lomiri.Components) so it also loads under qmlscene on a desktop.
Rectangle {
    id: stage
    width: 400; height: 300
    color: "#0b0d12"

    property var allEntries: Inv.demoEntries()   // replaced by the spike's result when it finds a real inventory
    property bool live: false                    // true only after the spike read real .desktop files
    property string query: ""
    property var entries: Inv.search(live ? Inv.sortEntries(allEntries) : allEntries, query)
    property real pos: Math.floor(entries.length / 2)
    readonly property int count: entries.length
    property string toast: ""

    function clampPos(p) { return Math.max(0, Math.min(Math.max(0, count - 1), p)); }
    onEntriesChanged: pos = clampPos(pos)

    function activate(i) {
        var e = entries[i];
        if (Math.abs(i - pos) > 0.5) { snap.to = i; snap.restart(); return; }
        var url = live ? Inv.clickAppIdUrl(e.file) : "";
        if (url !== "") { Qt.openUrlExternally(url); showToast("Opening " + e.name); }
        else showToast(e.demo ? "Demo entry - launching is disabled" : e.name + ": no launchable id");
    }
    function showToast(t) { toast = t; toastTimer.restart(); }
    Timer { id: toastTimer; interval: 1600; onTriggered: stage.toast = "" }
    NumberAnimation { id: snap; target: stage; property: "pos"; duration: 180; easing.type: Easing.OutCubic }

    Item {
        id: flow
        anchors.fill: parent
        Repeater {
            model: stage.count
            delegate: Item {
                id: card
                readonly property var lay: SM.cardLayout(index, stage.pos, 1)
                visible: lay !== null
                width: 86; height: 86
                x: stage.width / 2 - width / 2
                y: stage.height / 2 - height / 2 - 10
                z: lay ? lay.z : 0
                transform: Matrix4x4 {
                    matrix: {
                        if (!card.lay) return Qt.matrix4x4();
                        var m = SM.cardMatrixLocal(card.lay, card.x, card.y, card.width, card.height,
                                                stage.width, stage.height, SM.P.persp);
                        return Qt.matrix4x4(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7],
                                            m[8], m[9], m[10], m[11], m[12], m[13], m[14], m[15]);
                    }
                }
                Rectangle {
                    anchors.fill: parent; radius: 18
                    color: stage.entries[index].demo ? "#1d2230" : "#232a3d"
                    border.width: card.lay && card.lay.focus ? 2 : 1
                    border.color: card.lay && card.lay.focus ? "#8E7BFF" : "#39405a"
                    Text {
                        anchors.centerIn: parent; width: parent.width - 8
                        text: stage.entries[index].name; color: "#e8eaf2"; font.pixelSize: 13
                        horizontalAlignment: Text.AlignHCenter; elide: Text.ElideRight
                    }
                }
            }
        }
    }

    MouseArea {
        anchors.fill: parent
        property real startX: 0; property real startPos: 0; property real moved: 0
        onPressed: { snap.stop(); startX = mouse.x; startPos = stage.pos; moved = 0; }
        onPositionChanged: { moved = Math.max(moved, Math.abs(mouse.x - startX));
            stage.pos = stage.clampPos(startPos - (mouse.x - startX) / 150); }
        onReleased: { snap.to = stage.clampPos(Math.round(stage.pos)); snap.restart();
            if (moved < 6 && stage.count > 0 && Math.abs(mouse.y - (stage.height / 2 - 10)) < 60
                && Math.abs(mouse.x - stage.width / 2) < 60) stage.activate(Math.round(stage.pos)); }
    }

    // Honesty banner: this is a reframe, and the inventory is unverified until the spike says so.
    Rectangle {
        anchors { left: parent.left; right: parent.right; top: parent.top }
        height: banner.implicitHeight + 10; color: stage.live ? "#12301f" : "#3a2a10"
        Text { id: banner; anchors.centerIn: parent; width: parent.width - 16; wrapMode: Text.WordWrap
            horizontalAlignment: Text.AlignHCenter; font.pixelSize: 11; color: "#f3e9d2"
            text: stage.live ? "Liftoff picker - inventory from .desktop files (device-verified? see checklist)"
                             : "Liftoff picker - DEMO entries. Run the confinement spike to look for real apps." }
    }

    Row {
        anchors { left: parent.left; bottom: parent.bottom; margins: 8 }
        spacing: 8
        Rectangle { width: 130; height: 30; radius: 6; color: "#1d2230"; border.color: "#39405a"
            TextInput { anchors.fill: parent; anchors.margins: 6; color: "#e8eaf2"; font.pixelSize: 14
                onTextChanged: stage.query = text
                Text { visible: parent.text === ""; text: "type to search"; color: "#7b8299"; font.pixelSize: 14 } } }
        Rectangle { width: 110; height: 30; radius: 6; color: "#1d2230"; border.color: "#35E0FF"
            Text { anchors.centerIn: parent; text: "Run spike (U0)"; color: "#e8eaf2"; font.pixelSize: 12 }
            MouseArea { anchors.fill: parent; onClicked: spikeLoader.active = true } }
    }

    Rectangle { visible: stage.toast !== ""; anchors { horizontalCenter: parent.horizontalCenter; bottom: parent.bottom; bottomMargin: 48 }
        width: toastText.implicitWidth + 24; height: 28; radius: 14; color: "#2a3050"
        Text { id: toastText; anchors.centerIn: parent; text: stage.toast; color: "#e8eaf2"; font.pixelSize: 12 } }

    Loader {
        id: spikeLoader; active: false; anchors.fill: parent; z: 10000
        source: "SpikePage.qml"
        onLoaded: {
            item.finished.connect(function (found) {
                if (found.length > 0) { stage.allEntries = found; stage.live = true; }
                spikeLoader.active = false;
            });
        }
    }
}
