import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity

// Spike: does Compose Desktop render smoothly and take touch on the phone?
fun main() {
    println("screen=${java.awt.Toolkit.getDefaultToolkit().screenSize}")
    println("skiko.renderApi=${System.getProperty("skiko.renderApi")} os=${System.getProperty("os.arch")} java=${System.getProperty("java.version")}")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "compose-spike",
            undecorated = true,
            state = WindowState(
                position = WindowPosition(0.dp, 0.dp),
                size = java.awt.Toolkit.getDefaultToolkit().screenSize.let { DpSize(it.width.dp, it.height.dp) },
            ),
        ) {
            // The window is 1:1 pixels; Ubuntu Touch's grid unit for this phone is 21 px = 8 dp (kebab.yaml GridUnit).
            CompositionLocalProvider(LocalDensity provides Density(density = 21f / 8f, fontScale = 1f)) {
                MaterialTheme(colorScheme = lightColorScheme()) { Spike() }
            }
        }
    }
}

@Composable
fun Spike() {
    var fps by remember { mutableStateOf(0) }
    var typed by remember { mutableStateOf("") }
    val qt = remember { QtView("compose-spike") { typed = it }.also { it.start() } }

    // Frame counter: counts frames the compositor asked for over one second.
    LaunchedEffect(Unit) {
        var count = 0
        var start = 0L
        while (true) {
            withFrameNanos { t ->
                if (start == 0L) start = t
                count++
                if (t - start >= 1_000_000_000L) {
                    fps = count; println("fps=$count"); count = 0; start = t
                }
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFFF7F7F7))
    ) {
        // Keyboard and scroll test: does the on-screen keyboard appear, does a swipe scroll the list?
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.45f).padding(16.dp)) {
            // The Qt view is reparented over this box; Compose only reserves the space.
            Box(Modifier.fillMaxWidth().height(72.dp).onGloballyPositioned { c ->
                val p = c.positionInWindow()
                Thread { qt.place(p.x.toInt(), p.y.toInt(), c.size.width, c.size.height) }.start()
            })
            Text("typed: $typed", color = Color(0xFF1B1B1F), fontSize = 18.sp)
            // AWT delivers touch as mouse, and Compose Desktop only scrolls on the wheel, so drag by hand.
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            LazyColumn(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    val decay = splineBasedDecay<Float>(this)
                    var fling: Job? = null
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fling?.cancel()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        verticalDrag(down.id) { change ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            listState.dispatchRawDelta(-change.positionChange().y)
                            change.consume()
                        }
                        val v = tracker.calculateVelocity().y
                        fling = scope.launch {
                            var last = 0f
                            AnimationState(0f, v).animateDecay(decay) { listState.dispatchRawDelta(-(value - last)); last = value }
                        }
                    }
                },
                state = listState,
            ) {
                items((1..60).toList()) { Text("Row $it", color = Color(0xFF1B1B1F), fontSize = 22.sp, modifier = Modifier.padding(8.dp)) }
            }
        }
        Column(Modifier.align(Alignment.TopStart).padding(24.dp)) {
            Text("FPS $fps", color = Color(0xFF1B1B1F), fontSize = 32.sp)
        }
    }
}
