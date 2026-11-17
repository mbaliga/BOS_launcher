import QtQuick 2.12
import QtTest 1.12
import "../qml"

TestCase {
    name: "Picker"; when: windowShown
    Main { id: m; width: 400; height: 300 }
    function test_demo_mode_and_count() { compare(m.live, false); verify(m.count > 0); }
    function test_focus_card_is_centered_and_unrotated() {
        m.pos = 7; wait(50);
        var c = m.children[0].children[7];   // Repeater delegates live under flow
        verify(c !== undefined);
        verify(c.lay !== null);
        verify(c.lay.focus);                 // card 7 is the focused one at pos 7
        compare(c.lay.offset, 0);
    }
    function test_search_filters() { m.query = "Ca"; compare(m.entries.length, 4); compare(m.entries[3].name, "Authenticator"); m.query = ""; }
    function test_demo_launch_is_disabled() { m.pos = 3; m.activate(3); compare(m.toast.indexOf("disabled") >= 0, true); }
}
