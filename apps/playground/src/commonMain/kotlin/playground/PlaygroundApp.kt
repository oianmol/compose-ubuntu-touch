package playground

import App
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One entry in the chooser. Add a Demo to [demos] and it shows up on the home screen. */
class Demo(val title: String, val blurb: String, val content: @Composable () -> Unit)

val demos = listOf(
    Demo("Sign up", "Text fields, validation, password toggle, Ubuntu Touch keyboard") { SignupScreen() },
    Demo("Cupcake order", "Multi-screen navigation app (JetBrains nav_cupcake, unchanged)") { App() },
    Demo("Animations", "Infinite transitions and state-driven animation") { AnimationsScreen() },
    Demo("Touch and gestures", "Multi-touch ink, drag, tap and scroll") { GesturesScreen() },
    Demo("Platform", "expect/actual from common code, and the host's screen") { PlatformScreen() },
)

@Composable
fun PlaygroundApp() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            var open by remember { mutableStateOf<Demo?>(null) }
            val demo = open
            if (demo == null) Chooser { open = it } else DemoHost(demo) { open = null }
        }
    }
}

@Composable
private fun Chooser(onPick: (Demo) -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        Text("Compose playground", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        Text(
            "Kotlin Multiplatform running on Ubuntu Touch. Pick a demo.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(demos) { d ->
                Card(Modifier.fillMaxWidth().clickable { onPick(d) }, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(d.title, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(d.blurb, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoHost(demo: Demo, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        // No system back gesture on the host yet, so every demo gets an in-app back button.
        TopAppBar(
            title = { Text(demo.title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) { demo.content() }
    }
}
