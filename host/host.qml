import QtQuick 2.12
import QtQuick.Window 2.12
import io.thp.pyotherside 1.5

// Qt host: owns the window; shows the frames that the headless Compose process renders.
Rectangle {
    id: root
    width: 360; height: 800; color: "#f7f7f7"
    onWidthChanged: console.log("qt: win=" + width + "x" + height)
    onHeightChanged: console.log("qt: win=" + width + "x" + height)
    property int lastShown: 0

    property int frameW: 1080
    property int frameH: 2277
    property int bandRows: 64
    property int bandCount: 0
    property int loaded: 0

    // One strip per band; each has two image layers so a strip never blanks while its next picture loads.
    Repeater {
        id: bands
        model: root.bandCount
        delegate: Item {
            id: band
            property int front: 0
            property bool loading: false
            property int pending: 0
            property real sy: root.height / root.frameH
            x: 0; y: index * root.bandRows * sy
            width: root.width
            height: Math.min(root.bandRows, root.frameH - index * root.bandRows) * sy
            function request(seq) {
                if (loading) { pending = seq; return }   // keep only the newest update for a busy band
                loading = true; pending = 0
                ;(front === 0 ? layerB : layerA).source = "image://python/" + index + "/" + seq
            }
            function done(which) {
                front = which; loading = false; root.loaded++
                if (pending > 0) request(pending)
            }
            function failed() { loading = false; if (pending > 0) request(pending) }
            Image {
                id: layerA
                anchors.fill: parent; cache: false; asynchronous: true; fillMode: Image.Stretch
                visible: band.front === 0
                onStatusChanged: if (status === Image.Ready) band.done(0); else if (status === Image.Error) band.failed()
            }
            Image {
                id: layerB
                anchors.fill: parent; cache: false; asynchronous: true; fillMode: Image.Stretch
                visible: band.front === 1
                onStatusChanged: if (status === Image.Ready) band.done(1); else if (status === Image.Error) band.failed()
            }
        }
    }
    Timer {
        interval: 1000; repeat: true; running: true
        onTriggered: { console.log("qt: bands loaded/s=" + (root.loaded - root.lastShown)); root.lastShown = root.loaded }
    }
    // Hidden input that Ubuntu Touch's keyboard types into; its text and cursor go back to Compose.
    TextInput {
        id: kbInput
        width: 1; height: 1; opacity: 0.01
        inputMethodHints: Qt.ImhNoPredictiveText | Qt.ImhNoAutoUppercase
        property bool syncing: false
        function report() { if (!syncing) py.call("bridge.text", [text, cursorPosition]) }
        onTextChanged: report()
        onCursorPositionChanged: report()
        Keys.onReturnPressed: py.call("bridge.enter", [])
    }
    Connections {
        target: Qt.inputMethod
        onVisibleChanged: root.reportKeyboard()
        onKeyboardRectangleChanged: root.reportKeyboard()
    }
    function reportKeyboard() {
        var r = Qt.inputMethod.keyboardRectangle
        console.log("qt: kb visible=" + Qt.inputMethod.visible + " rect=" + r.x + "," + r.y + " " + r.width + "x" + r.height +
                    " win=" + root.width + "x" + root.height + " screen=" + Screen.width + "x" + Screen.height +
                    " avail=" + Screen.desktopAvailableHeight + " dpr=" + Screen.devicePixelRatio)
        py.call("bridge.kbstate", [Qt.inputMethod.visible, Qt.inputMethod.keyboardRectangle.height * 1080 / root.width])
    }
    MultiPointTouchArea {
        anchors.fill: parent
        maximumTouchPoints: 10
        property real scale1: 1080 / root.width
        function send(phase, pts) {
            for (var i = 0; i < pts.length; i++)
                py.call("bridge.touch", [phase, pts[i].pointId, pts[i].x * scale1, pts[i].y * scale1])
        }
        onPressed: send("d", touchPoints)
        onUpdated: send("m", touchPoints)
        onReleased: send("u", touchPoints)
    }
    Python {
        id: py
        Component.onCompleted: {
            addImportPath(Qt.resolvedUrl("."))
            setHandler("init", function (w, h, br, n) { root.frameW = w; root.frameH = h; root.bandRows = br; root.bandCount = n })
            setHandler("bands", function (list) {
                for (var k = 0; k < list.length; k++) {
                    var it = bands.itemAt(list[k][0])
                    if (it) it.request(list[k][1])
                }
            })
            setHandler("kb", function (action, text, cursor) {
                if (action === "show") {
                    kbInput.syncing = true
                    kbInput.text = text
                    kbInput.cursorPosition = cursor
                    kbInput.syncing = false
                    kbInput.forceActiveFocus()
                    Qt.inputMethod.show()
                } else {
                    kbInput.focus = false
                    Qt.inputMethod.hide()
                }
            })
            importModule("bridge", function () { console.log("qt: bridge ready") })
        }
        onError: console.log("py error: " + traceback)
    }
}
