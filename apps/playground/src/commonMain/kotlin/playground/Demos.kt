package playground

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import platformName
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun AnimationsScreen() {
    val t = rememberInfiniteTransition()
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)))
    val pulse by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse))
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 300.dp else 120.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    val color by animateColorAsState(if (expanded) Color(0xFF16A34A) else Color(0xFF3B82F6))
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Canvas(Modifier.size(260.dp)) {
            val c = center
            val r = size.minDimension / 2 * 0.8f
            for (i in 0 until 12) {
                val a = (angle + i * 30f) * PI.toFloat() / 180f
                drawCircle(Color.hsv((i * 30f + angle) % 360f, 0.7f, 0.9f), 14.dp.toPx() * pulse, Offset(c.x + r * cos(a), c.y + r * sin(a)))
            }
        }
        Box(Modifier.height(64.dp).width(width).clip(RoundedCornerShape(16.dp)).background(color).clickable { expanded = !expanded }, contentAlignment = Alignment.Center) {
            Text(if (expanded) "Tap to shrink" else "Tap me", color = Color.White)
        }
    }
}

@Composable
fun GesturesScreen() {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var box by remember { mutableStateOf(IntOffset(100, 100)) }
    var taps by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { taps++ }) { Text("Taps: $taps") }
            OutlinedButton(onClick = { strokes.clear() }) { Text("Clear") }
        }
        Text("Draw anywhere below (try two fingers). Drag the square.", Modifier.padding(horizontal = 12.dp))
        Box(Modifier.fillMaxSize().background(Color(0xFFF1F5F9)).pointerInput(Unit) {
            // One stroke per pointer, so multi-touch draws several lines at once.
            awaitPointerEventScope {
                val live = HashMap<Long, Int>() // pointer id -> index in strokes
                while (true) {
                    val ev = awaitPointerEvent()
                    for (c in ev.changes) {
                        val id = c.id.value
                        if (c.pressed) {
                            val i = live.getOrPut(id) { strokes.add(emptyList()); strokes.lastIndex }
                            strokes[i] = strokes[i] + c.position
                        } else live.remove(id)
                    }
                }
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                for (s in strokes) if (s.size > 1) drawPath(Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }, Color(0xFF1D4ED8), style = Stroke(6f))
            }
            Box(Modifier.offset { box }.size(80.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEA580C)).pointerInput(Unit) {
                detectDragGestures { change, drag -> change.consume(); box = IntOffset(box.x + drag.x.roundToInt(), box.y + drag.y.roundToInt()) }
            })
        }
    }
}

@Composable
fun PlatformScreen() {
    // platformName() is `expect` in common code (cupcake module); the JVM source set supplies the actual.
    val rows = listOf("platformName() (expect/actual)" to platformName()) + platformDetails()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows) { (k, v) ->
            Column {
                Text(k, style = MaterialTheme.typography.labelMedium)
                Text(v, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
