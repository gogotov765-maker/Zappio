
package app.zappio.messenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ProfileSetupScreen(
    onComplete: () -> Unit
) {
    val repository = remember { ChatRepository() }

    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Zappio",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Создайте профиль",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Заполните данные, чтобы другие пользователи могли вас найти.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ваше имя") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it.filter { char ->
                    char.isLetterOrDigit() || char == '_'
                }.lowercase().take(20)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Username") },
            prefix = { Text("@") },
            supportingText = {
                Text("3–20 символов: латиница, цифры и _")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii
            ),
            shape = RoundedCornerShape(18.dp)
        )

        if (error != null) {
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                loading = true
                error = null

                repository.completeProfile(
                    displayName,
                    username
                ) { success, message ->
                    loading = false

                    if (success) {
                        onComplete()
                    } else {
                        error = message
                    }
                }
            },
            enabled = !loading &&
                displayName.trim().length >= 2 &&
                username.length >= 3,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Продолжить")
            }
        }
    }
}
