@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteAllCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteOrder
import java.nio.channels.FileChannel

// Compose rendered headless (no window); BGRA frames go into a shared file that the Qt host (host/host.qml + bridge.py) shows.
// Frame file: seq:Long, width:Int, height:Int, then two pixel buffers (slot = seq % 2) from offset 16.
// Qt sends back over a Unix socket: touches "<d|m|u> <id> <x> <y>", text "t <urlencoded> <cursor>", keyboard "kv <0|1> <heightPx>".
// We send "show <urlencoded text> <cursor>" / "hide" so the Ubuntu Touch keyboard types into a hidden Qt TextInput.
private const val W = 1080
private const val H = 2277 // the app window Lomiri gives us (1080x2277: screen minus the status bar), see host.log
private const val BAND_ROWS = 64 // the frame is sent in horizontal bands; only changed bands are rewritten
private const val N_BANDS = (H + BAND_ROWS - 1) / BAND_ROWS
private const val HEADER = 4096L // 0: frame size info, 24: one Long sequence number per band, pixels from HEADER
private const val DENSITY = 21f / 8f // Ubuntu Touch grid unit for this phone: 21 px = 8 dp

private object Editor {
    var kbPx by mutableStateOf(0)
    var dismissTick by mutableStateOf(0) // bumped when the system keyboard is closed, so Compose drops text focus
    var send: (String) -> Unit = {}
}

// Bridges Compose's text input to Ubuntu Touch's keyboard: the focused field's request decides when the keyboard is shown,
// and the text Qt reports back goes into the field as edit commands.
private object Ime {
    @Volatile var request: PlatformTextInputMethodRequest? = null

    fun begin(r: PlatformTextInputMethodRequest) {
        request = r
        val v = r.value()
        Editor.send("show ${URLEncoder.encode(v.text, "UTF-8")} ${v.selection.start}")
    }

    fun end(r: PlatformTextInputMethodRequest) {
        if (request === r) { request = null; Editor.send("hide") }
    }

    fun fromQt(text: String, cursor: Int) {
        val r = request ?: return
        val v = r.value()
        if (v.text == text && v.selection.start == cursor && v.selection.end == cursor) return
        r.onEditCommand(listOf(DeleteAllCommand(), CommitTextCommand(text, 1), SetSelectionCommand(cursor, cursor)))
    }

    fun enter() {
        val r = request ?: return
        r.onImeAction?.invoke(r.imeOptions.imeAction)
        if (r.imeOptions.imeAction != ImeAction.Default && r.imeOptions.imeAction != ImeAction.None) Editor.send("hide")
    }
}

private val platformContext = object : PlatformContext by PlatformContext.Empty {
    override val windowInfo = object : WindowInfo {
        override val isWindowFocused = true
        override val containerSize = IntSize(W, H)
    }

    // Called when a focused text field wants input; stays suspended while it has focus.
    override suspend fun startInputMethod(request: PlatformTextInputMethodRequest): Nothing {
        Ime.begin(request)
        try { awaitCancellation() } finally { Ime.end(request) }
    }
}

@Composable
private fun Milestone() {
    var taps by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    LaunchedEffect(Editor.dismissTick) { if (Editor.dismissTick > 0) focus.clearFocus() }
    var fingers by remember { mutableStateOf(listOf<Offset>()) }
    val ink = Color(0xFF1B1B1F)
    val kbDp = with(LocalDensity.current) { Editor.kbPx.toDp() }
    Box(
        Modifier.fillMaxSize().background(Color(0xFFF7F7F7)).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) { fingers = awaitPointerEvent().changes.filter { it.pressed }.map { it.position } }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + kbDp)) {
            Text("Compose via Qt host", color = ink, fontSize = 24.sp)
            Text("fingers: ${fingers.size}  taps: $taps  keyboard: ${Editor.kbPx}px", color = ink, fontSize = 16.sp)
            Box(
                Modifier.padding(vertical = 12.dp).fillMaxWidth().height(56.dp).background(Color(0xFF3B82F6)).clickable { taps++ },
                contentAlignment = Alignment.Center,
            ) { Text("Tap me", color = Color.White, fontSize = 20.sp) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items((1..80).toList()) { Text("Row $it", color = ink, fontSize = 22.sp, modifier = Modifier.padding(10.dp)) }
            }
            OutlinedTextField(
                name, { name = it }, label = { Text("Name") }, singleLine = true,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )
        }
        Canvas(Modifier.fillMaxSize()) { fingers.forEach { drawCircle(Color(0x88FF4444), 70f, it) } }
    }
}

private class Touch(val phase: Char, val id: Long, val x: Float, val y: Float)
private val touches = java.util.concurrent.ConcurrentLinkedQueue<Touch>()
private val inbox = java.util.concurrent.ConcurrentLinkedQueue<String>()
@Volatile private var toQt: java.io.OutputStream? = null

private fun serve(path: File) {
    path.delete()
    val server = java.nio.channels.ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX)
    server.bind(java.net.UnixDomainSocketAddress.of(path.path))
    Thread {
        while (true) {
            val ch = server.accept()
            toQt = java.nio.channels.Channels.newOutputStream(ch)
            java.nio.channels.Channels.newInputStream(ch).bufferedReader().forEachLine { l ->
                val p = l.split(' ')
                when {
                    p[0] == "t" || p[0] == "kv" || p[0] == "e" -> inbox.add(l)
                    p.size == 4 -> touches.add(Touch(p[0][0], p[1].toLong(), p[2].toFloat(), p[3].toFloat()))
                }
            }
        }
    }.apply { isDaemon = true }.start()
}

// The renderer once grew to ~9 GB of native memory (a Skia Image per frame that was never closed) and took the phone down.
// Stay far below that: report resident memory and quit if it ever passes the limit.
private const val RSS_LIMIT_MB = 1500

private fun residentMb(): Int =
    File("/proc/self/status").useLines { lines -> lines.firstOrNull { it.startsWith("VmRSS:") } }
        ?.filter { it.isDigit() }?.toIntOrNull()?.div(1024) ?: 0

// What Compose Desktop's window normally provides and real apps expect: a ViewModel store and a lifecycle that stays RESUMED.
private class AppOwner : androidx.lifecycle.ViewModelStoreOwner, androidx.lifecycle.LifecycleOwner {
    override val viewModelStore = androidx.lifecycle.ViewModelStore()
    private val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).also { it.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
}

@Composable
private fun WithAppOwners(content: @Composable () -> Unit) {
    val owner = remember { AppOwner() }
    CompositionLocalProvider(
        androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides owner,
        androidx.lifecycle.compose.LocalLifecycleOwner provides owner,
        content = content,
    )
}

/** Demo screen for the host itself. */
fun main() = runOnUbuntuTouch { MaterialTheme(colorScheme = lightColorScheme()) { Milestone() } }

/** Runs [content] headless and shows it on the phone through the Qt host (host/host.qml). Never returns. */
fun runOnUbuntuTouch(content: @Composable () -> Unit) {
    val dir = File(System.getenv("XDG_RUNTIME_DIR") ?: "/tmp", "compose-host").apply { mkdirs() }
    val file = File(dir, "frame.bin")
    val size = HEADER + W.toLong() * H * 4
    val ch = RandomAccessFile(file, "rw").also { it.setLength(size) }.channel
    val map = ch.map(FileChannel.MapMode.READ_WRITE, 0, size).order(ByteOrder.LITTLE_ENDIAN)
    map.putInt(8, W).putInt(12, H).putInt(16, N_BANDS).putInt(20, BAND_ROWS)

    Editor.send = { line -> toQt?.let { try { it.write((line + "\n").toByteArray()); it.flush() } catch (_: Exception) {} } }
    val scene = CanvasLayersComposeScene(density = Density(DENSITY), size = IntSize(W, H), platformContext = platformContext)
    scene.setContent { WithAppOwners(content) }
    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(W, H)
    val pm = org.jetbrains.skia.Pixmap()
    serve(File(dir, "input.sock"))
    val down = LinkedHashMap<Long, Offset>()
    var bandSeq = 0L
    val prev = java.nio.ByteBuffer.allocateDirect(W * H * 4)
    var changedBands = 0
    var touchCount = 0
    var frames = 0; var renderNs = 0L; var copyNs = 0L; var statStart = System.nanoTime()
    println("host: ${file.path} ${W}x$H")
    while (true) {
        while (true) {
            val line = inbox.poll() ?: break
            val p = line.split(' ')
            if (p[0] == "t" && p.size == 3) Ime.fromQt(URLDecoder.decode(p[1], "UTF-8"), p[2].toInt())
            if (p[0] == "e") Ime.enter()
            if (p[0] == "kv" && p.size == 3) {
                val open = p[1] == "1"
                Editor.kbPx = if (open) p[2].toFloat().toInt() else 0
                if (!open && Ime.request != null) Editor.dismissTick++
            }
        }
        while (true) {
            val t = touches.poll() ?: break
            touchCount++
            down[t.id] = Offset(t.x, t.y)
            val type = when (t.phase) { 'd' -> PointerEventType.Press; 'u' -> PointerEventType.Release; else -> PointerEventType.Move }
            val pointers = down.map { (id, pos) -> ComposeScenePointer(PointerId(id), pos, pressed = !(t.phase == 'u' && id == t.id), type = PointerType.Touch) }
            scene.sendPointerEvent(type, pointers, timeMillis = System.currentTimeMillis())
            if (t.phase == 'u') down.remove(t.id)
        }
        val t0 = System.nanoTime()
        surface.canvas.clear(0)
        scene.render(surface.canvas.asComposeCanvas(), t0)
        val t1 = System.nanoTime()
        // Zero-copy view of Skia's pixel memory (rows are W * 4 bytes, no padding).
        surface.peekPixels(pm)
        val cur = com.sun.jna.Pointer(pm.addr).getByteBuffer(0, W.toLong() * H * 4)
        for (i in 0 until N_BANDS) {
            val off = i * BAND_ROWS * W * 4
            val len = minOf(BAND_ROWS, H - i * BAND_ROWS) * W * 4
            val a = cur.slice(off, len)
            val b = prev.slice(off, len)
            if (a.mismatch(b) == -1) continue
            map.position((HEADER + off).toInt()); map.put(a.duplicate())
            b.put(a)
            map.putLong(24 + i * 8, ++bandSeq) // after the pixels, so Qt never sees a half-written band announced
            changedBands++
        }
        val t2 = System.nanoTime()
        frames++; renderNs += t1 - t0; copyNs += t2 - t1
        if (t2 - statStart >= 1_000_000_000L) {
            val rss = residentMb()
            if (rss > RSS_LIMIT_MB) { println("host: rss=${rss}MB over limit, exiting"); kotlin.system.exitProcess(3) }
            println("host: rss=${rss}MB touches=$touchCount fps=$frames bands/frame=${changedBands / frames} render=${renderNs / frames / 1_000_000}ms copy=${copyNs / frames / 1_000_000}ms")
            frames = 0; renderNs = 0; copyNs = 0; changedBands = 0; statStart = t2
        }
        val sleep = 16_000_000L - (System.nanoTime() - t0)
        if (sleep > 0) Thread.sleep(sleep / 1_000_000)
    }
}
