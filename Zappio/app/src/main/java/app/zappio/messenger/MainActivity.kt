package app.zappio.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZappioTheme {
                ZappioRoot(this)
            }
        }
    }
}

/** Фон с мягкими цветными пятнами — на нём стеклянный док выглядит «стеклянным». */
@Composable
private fun AppBackground(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.background)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(cs.primary.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(100f, 200f),
                    radius = 1100f
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(cs.tertiary.copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(1000f, 2000f),
                    radius = 1300f
                )
            )
        )
        content()
    }
}

/** Вход по SMS → создание профиля → основное приложение. */
@Composable
fun ZappioRoot(activity: ComponentActivity) {
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }

    var isSignedIn by remember { mutableStateOf(auth.currentUser != null) }
    var profileReady by remember { mutableStateOf(false) }
    var profileChecked by remember { mutableStateOf(false) }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    // Проверяем, заполнен ли профиль, каждый раз когда пользователь входит
    LaunchedEffect(isSignedIn) {
        if (!isSignedIn) {
            profileReady = false
            profileChecked = false
            return@LaunchedEffect
        }
        profileChecked = false
        val uid = auth.currentUser?.uid
        if (uid == null) {
            profileReady = false
            profileChecked = true
        } else {
            firestore.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    profileReady = !doc.getString("displayName").isNullOrBlank() &&
                        !doc.getString("username").isNullOrBlank()
                    profileChecked = true
                }
                .addOnFailureListener {
                    profileReady = false
                    profileChecked = true
                }
        }
    }

    fun finishSignIn(credential: PhoneAuthCredential) {
        isLoading = true
        authError = null

        auth.signInWithCredential(credential).addOnCompleteListener(activity) { task ->
            isLoading = false
            if (task.isSuccessful) {
                val user = task.result?.user
                if (user != null) {
                    // Только служебные поля. Телефон в публичный профиль не пишем.
                    firestore.collection("users").document(user.uid)
                        .set(
                            mapOf(
                                "uid" to user.uid,
                                "lastLoginAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                    isSignedIn = true
                } else {
                    authError = "Не удалось получить данные аккаунта."
                }
            } else {
                authError = task.exception?.localizedMessage ?: "Не удалось выполнить вход."
            }
        }
    }

    val sendCode: (String) -> Unit = { rawPhone ->
        isLoading = true
        authError = null
        verificationId = null

        // Firebase принимает только формат E.164: +79991234567
        val phone = rawPhone.filter { it.isDigit() || it == '+' }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                finishSignIn(credential)
            }

            override fun onVerificationFailed(exception: FirebaseException) {
                isLoading = false
                authError = exception.localizedMessage ?: "Не удалось отправить SMS."
            }

            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                isLoading = false
                authError = null
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    val verifyCode: (String) -> Unit = { code ->
        val id = verificationId
        if (id != null && code.isNotBlank()) {
            finishSignIn(PhoneAuthProvider.getCredential(id, code))
        } else {
            authError = "Введите код из SMS."
        }
    }

    AppBackground {
        when {
            !isSignedIn -> AuthScreen(
                verificationSent = verificationId != null,
                onSendCode = sendCode,
                onVerifyCode = verifyCode,
                onChangePhone = {
                    verificationId = null
                    authError = null
                },
                loading = isLoading,
                error = authError
            )

            !profileChecked -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            !profileReady -> ProfileSetupScreen(onComplete = { profileReady = true })

            else -> MainShell(
                onSignOut = {
                    auth.signOut()
                    verificationId = null
                    authError = null
                    isSignedIn = false
                }
            )
        }
    }
}

/** Основное приложение: чаты, поиск, профиль и стеклянный док. */
@Composable
fun MainShell(onSignOut: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var openChatName by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = searchOpen || openChatId != null) {
        if (searchOpen) searchOpen = false else openChatId = null
    }

    val openChat: (String, String) -> Unit = { id, name ->
        openChatId = id
        openChatName = name
        searchOpen = false
    }

    Box(Modifier.fillMaxSize()) {
        val chatId = openChatId
        when {
            searchOpen -> UserSearchScreen(
                onBack = { searchOpen = false },
                onOpenChat = openChat
            )

            chatId != null -> RealChatScreen(
                chatId = chatId,
                title = openChatName,
                onBack = { openChatId = null }
            )

            else -> {
                Crossfade(targetState = tab, label = "tabs") { selected ->
                    when (selected) {
                        0 -> RealHomeScreen(
                            onOpenChat = openChat,
                            onSearchUsers = { searchOpen = true }
                        )
                        1 -> UserSearchScreen(onBack = { tab = 0 }, onOpenChat = openChat)
                        else -> ProfileScreen(onSignOut = onSignOut)
                    }
                }

                GlassDock(
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                )
            }
        }
    }
}
