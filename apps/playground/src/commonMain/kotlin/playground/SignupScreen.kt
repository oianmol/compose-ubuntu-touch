package playground

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@Composable
fun SignupScreen() {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    val nameError = if (name.isBlank()) "Enter your name" else null
    val emailError = if (!emailRegex.matches(email)) "Enter a valid email" else null
    val passwordError = if (password.length < 8) "At least 8 characters" else null
    val confirmError = if (confirm != password) "Passwords do not match" else null
    val valid = listOf(nameError, emailError, passwordError, confirmError).all { it == null } && accepted

    if (submitted) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Welcome, ${name.trim()}!", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Account created for $email", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { submitted = false; attempted = false; password = ""; confirm = ""; accepted = false }) { Text("Start over") }
        }
        return
    }

    @Composable
    fun field(
        label: String, value: String, onChange: (String) -> Unit, error: String?,
        type: KeyboardType = KeyboardType.Text, ime: ImeAction = ImeAction.Next, secret: Boolean = false,
        onDone: () -> Unit = {},
    ) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
            isError = attempted && error != null,
            supportingText = { if (attempted && error != null) Text(error) },
            visualTransformation = if (secret && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ime),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Create your account", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        field("Name", name, { name = it }, nameError)
        field("Email", email, { email = it }, emailError, type = KeyboardType.Email)
        field("Password", password, { password = it }, passwordError, type = KeyboardType.Password, secret = true)
        field("Confirm password", confirm, { confirm = it }, confirmError, type = KeyboardType.Password, ime = ImeAction.Done, secret = true) {
            focus.clearFocus()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = showPassword, onCheckedChange = { showPassword = it })
            Spacer(Modifier.width(8.dp))
            Text("Show passwords")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
            Text("I accept the terms")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { attempted = true; if (valid) { focus.clearFocus(); submitted = true } },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Sign up") }
        if (attempted && !accepted) Text("Accept the terms to continue", color = MaterialTheme.colorScheme.error)
    }
}
