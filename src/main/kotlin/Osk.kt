import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Maliit (the Lomiri keyboard) has no path into AWT/Xwayland, so apps draw their own keyboard.
// Edits are append/backspace only: there is no cursor.
private val letters = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val symbols = listOf("1234567890", "-/:;()$&@\"", ".,?!'#%+=*")

@Composable
fun Osk(onKey: (String) -> Unit, onBackspace: () -> Unit, onEnter: () -> Unit, onHide: () -> Unit) {
    var shift by remember { mutableStateOf(false) }
    var sym by remember { mutableStateOf(false) }
    val rows = if (sym) symbols else letters

    @Composable
    fun Key(label: String, weight: Float = 1f, bg: Color = Color.White, tap: () -> Unit) {
        Box(
            Modifier.padding(3.dp).height(56.dp).clip(RoundedCornerShape(8.dp)).background(bg)
                .clickable(onClick = tap).widthIn(min = 28.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = Color(0xFF1B1B1F), fontSize = 22.sp) }
    }

    Column(Modifier.fillMaxWidth().background(Color(0xFFE6E8EB)).padding(4.dp)) {
        rows.forEachIndexed { i, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (i == 2 && !sym) Key(if (shift) "⇩" else "⇧", bg = Color(0xFFCDD1D6)) { shift = !shift }
                row.forEach { c ->
                    val s = if (shift && !sym) c.uppercaseChar().toString() else c.toString()
                    Key(s) { onKey(s); if (shift) shift = false }
                }
                if (i == 2) Key("⌫", bg = Color(0xFFCDD1D6)) { onBackspace() }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Key(if (sym) "abc" else "123", bg = Color(0xFFCDD1D6)) { sym = !sym }
            Key("      space      ") { onKey(" ") }
            Key("⏎", bg = Color(0xFFCDD1D6)) { onEnter() }
            Key("▼", bg = Color(0xFFCDD1D6)) { onHide() }
        }
    }
}
