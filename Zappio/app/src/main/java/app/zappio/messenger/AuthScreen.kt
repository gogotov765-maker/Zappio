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
fun AuthScreen(
    verificationSent: Boolean,
    onSendCode: (String) -> Unit,
    onVerifyCode: (String) -> Unit,
    onChangePhone: () -> Unit,
    loading: Boolean = false,
    error: String? = null
) {
    var phone by remember { mutableStateOf("+7") }
    var code by remember { mutableStateOf("") }

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
            if (verificationSent) "Подтверждение номера"
            else "Добро пожаловать!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            if (verificationSent)
                "Введите код, отправленный вам в SMS."
            else
                "Введите номер телефона, чтобы войти или создать аккаунт.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))

        if (!verificationSent) {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Номер телефона") },
                placeholder = { Text("+7 900 000-00-00") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone
                ),
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
        } else {
            OutlinedTextField(
                value = code,
                onValueChange = {
                    if (it.length <= 6 && it.all(Char::isDigit)) {
                        code = it
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Код из SMS") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (verificationSent) {
                    onVerifyCode(code)
                } else {
                    onSendCode(phone.trim())
                }
            },
            enabled = !loading && if (verificationSent) {
                code.length == 6
            } else {
                phone.trim().length >= 8
            },
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
                Text(
                    if (verificationSent) "Подтвердить"
                    else "Получить код"
                )
            }
        }

        if (verificationSent) {
            TextButton(
                onClick = {
                    code = ""
                    onChangePhone()
                }
            ) {
                Text("Изменить номер телефона")
            }
        }
    }
}