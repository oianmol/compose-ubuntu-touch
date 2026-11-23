import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import java.io.File
import java.net.URLDecoder

private interface Xlib : Library {
    fun XOpenDisplay(name: String?): Pointer?
    fun XDefaultRootWindow(d: Pointer): Long
    fun XQueryTree(d: Pointer, w: Long, root: LongByReference, parent: LongByReference, kids: PointerByReference, n: IntByReference): Int
    fun XFetchName(d: Pointer, w: Long, name: PointerByReference): Int
    fun XReparentWindow(d: Pointer, w: Long, parent: Long, x: Int, y: Int): Int
    fun XMoveResizeWindow(d: Pointer, w: Long, x: Int, y: Int, width: Int, height: Int): Int
    fun XMapRaised(d: Pointer, w: Long): Int
    fun XFlush(d: Pointer): Int
    fun XFree(p: Pointer): Int
}

// A Qt window (kbview.qml, run by qmlscene) embedded as a child of our own X11 window.
// AWT/Xwayland cannot reach Maliit, but a Qt window can; as a child of our window it shares our focus.
class QtView(private val parentTitle: String, private val onText: (String) -> Unit) {
    private val x = Native.load("X11", Xlib::class.java)
    private val dpy = x.XOpenDisplay(null) ?: error("no X display")
    private var child = 0L
    private var proc: Process? = null
    private val textLine = Regex("^(?:qml: )?T:([^:]*)$")

    private fun nameOf(w: Long): String? {
        val r = PointerByReference()
        if (x.XFetchName(dpy, w, r) == 0 || r.value == null) return null
        return r.value.getString(0).also { x.XFree(r.value) }
    }

    private fun find(root: Long, title: String, depth: Int = 3): Long {
        if (depth == 0) return 0
        val kids = PointerByReference(); val n = IntByReference()
        if (x.XQueryTree(dpy, root, LongByReference(), LongByReference(), kids, n) == 0 || kids.value == null) return 0
        val ids = kids.value.getLongArray(0, n.value)
        x.XFree(kids.value)
        for (id in ids) if (nameOf(id) == title) return id
        for (id in ids) find(id, title, depth - 1).let { if (it != 0L) return it }
        return 0
    }

    fun start() {
        val dir = File(System.getenv("XDG_RUNTIME_DIR") ?: "/tmp", "compose-kb").apply { mkdirs() }
        val qml = File(dir, "kbview.qml").apply { writeText(QtView::class.java.getResource("/kbview.qml")!!.readText()) }
        val pb = ProcessBuilder("qmlscene", qml.path).redirectErrorStream(true)
        pb.environment().apply {
            put("QT_QPA_PLATFORM", "xcb"); put("QT_IM_MODULE", "maliit"); put("DISPLAY", ":0")
            put("GLIBC_TUNABLES", "glibc.rtld.optional_static_tls=131072")
        }
        val p = pb.start().also { proc = it }
        Runtime.getRuntime().addShutdownHook(Thread { p.destroy() })
        Thread {
            p.inputStream.bufferedReader().forEachLine { line ->
                println("qt: $line")
                textLine.matchEntire(line)?.let { onText(URLDecoder.decode(it.groupValues[1], "UTF-8")) }
            }
        }.apply { isDaemon = true }.start()
    }

    /** Puts the Qt window at (px, py) with size (w, h) inside our window; reparents it on first call. */
    @Synchronized
    fun place(px: Int, py: Int, w: Int, h: Int) {
        val root = x.XDefaultRootWindow(dpy)
        if (child == 0L) {
            child = find(root, "kbhelper")
            val parent = find(root, parentTitle)
            println("embed: child=$child parent=$parent")
            if (child == 0L || parent == 0L) { child = 0; return }
            x.XReparentWindow(dpy, child, parent, px, py)
        }
        x.XMoveResizeWindow(dpy, child, px, py, w, h)
        x.XMapRaised(dpy, child)
        x.XFlush(dpy)
    }
}
