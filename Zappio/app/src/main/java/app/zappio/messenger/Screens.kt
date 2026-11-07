package app.zappio.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

/** Круглый аватар с первой буквой имени. */
@Composable
fun Avatar(name: String, size: Dp) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (name.hashCode().mod(3)) {
        0 -> cs.primaryContainer to cs.onPrimaryContainer
        1 -> cs.secondaryContainer to cs.onSecondaryContainer
        else -> cs.tertiaryContainer to cs.onTertiaryContainer
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.take(1).uppercase(),
            color = fg,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Профиль: данные берутся из Firestore, есть выход из аккаунта. */
@Composable
fun ProfileScreen(onSignOut: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val repository = remember { ChatRepository() }
    var name by remember { mutableStateOf("…") }
    var username by remember { mutableStateOf("") }
    val user = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        repository.getUser(uid) { profile, _ ->
            name = profile?.get("displayName") as? String ?: "Без имени"
            username = (profile?.get("username") as? String)?.let { "@$it" } ?: ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 130.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Avatar(name, 104.dp)
        Spacer(Modifier.height(14.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (username.isNotEmpty()) Text(username, color = cs.onSurfaceVariant)
        Text(user?.phoneNumber ?: "", color = cs.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = cs.errorContainer,
                contentColor = cs.onErrorContainer
            )
        ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
            Text("  Выйти из аккаунта")
        }
    }
}
