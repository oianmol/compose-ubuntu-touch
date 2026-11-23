import QtQuick 2.12
import QtQuick.Window 2.12

// Native text field that the Compose app reparents into its own window (like a UIKitView), so Lomiri sees one
// surface and Ubuntu Touch's keyboard (Maliit) types into this TextInput. Text goes back on stderr.
Window {
    title: "kbhelper"
    visible: true
    width: 600; height: 150
    flags: Qt.FramelessWindowHint
    color: "white"

    TextInput {
        id: t
        anchors.fill: parent; anchors.margins: 16
        font.pixelSize: 44; color: "#1b1b1f"
        verticalAlignment: TextInput.AlignVCenter
        onTextChanged: console.log("T:" + encodeURIComponent(t.text))
    }
    Connections { target: Qt.inputMethod; onVisibleChanged: console.log("V:" + Qt.inputMethod.visible) }
    Component.onCompleted: console.log("READY")
}
