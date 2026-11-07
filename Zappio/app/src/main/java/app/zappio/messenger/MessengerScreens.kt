package app.zappio.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val repository = ChatRepository()


// Главный экран с реальными чатами
@Composable
fun RealHomeScreen(
    onOpenChat: (String, String) -> Unit,
    onSearchUsers: () -> Unit
) {
    val chats = remember { mutableStateListOf<FirestoreChat>() }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = repository.listenChats { result, message ->
            chats.clear()
            chats.addAll(result)
            error = message
        }
        onDispose { listener?.remove() }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Zappio",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSearchUsers) {
                Icon(Icons.Rounded.Search, contentDescription = "Поиск пользователей")
            }
        }

        if (error != null) {
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (chats.isEmpty() && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Пока нет чатов")
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(chats, key = { it.id }) { chat ->
                    val myUid = com.google.firebase.auth.FirebaseAuth
                        .getInstance().currentUser?.uid
                    val otherUid = chat.members.firstOrNull { it != myUid }

                    if (otherUid != null) {
                        RealChatRow(chat, otherUid) { name ->
                            onOpenChat(chat.id, name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealChatRow(
    chat: FirestoreChat,
    otherUid: String,
    onClick: (String) -> Unit
) {
    var name by remember(otherUid) { mutableStateOf("Пользователь") }

    LaunchedEffect(otherUid) {
        repository.getUser(otherUid) { profile, _ ->
            name = profile?.get("displayName") as? String
                ?: profile?.get("username") as? String
                ?: "Пользователь"
        }
    }

    Row(
        Modifier.fillMaxWidth()
            .clickable { onClick(name) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name, 54.dp)

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(
                chat.lastMessage.ifBlank { "Новый чат" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Text(
            formatTimestamp(chat.lastMessageAt),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

// Поиск пользователей по номеру телефона
@Composable
fun UserSearchScreen(
    onBack: () -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
            }
            Text("Найти пользователя", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Номер телефона") },
            placeholder = { Text("+79991234567") },
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                loading = true
                message = ""

                repository.searchUserByPhone(phone) { uid, error ->
                    loading = false

                    if (uid == null) {
                        message = error ?: "Пользователь не найден"
                    } else {
                        repository.getUser(uid) { profile, profileError ->
                            if (profile == null) {
                                message = profileError ?: "Не удалось загрузить профиль"
                            } else {
                                val name = profile["displayName"] as? String
                                    ?: profile["username"] as? String
                                    ?: "Пользователь"

                                repository.getOrCreatePrivateChat(uid) { chatId, chatError ->
                                    if (chatId != null) {
                                        onOpenChat(chatId, name)
                                    } else {
                                        message = chatError ?: "Не удалось открыть чат"
                                    }
                                }
                            }
                        }
                    }
                }
            },
            enabled = phone.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Найти")
            }
        }

        if (message.isNotBlank()) {
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

// Реальный экран переписки
@Composable
fun RealChatScreen(
    chatId: String,
    title: String,
    onBack: () -> Unit
) {
    val uid = com.google.firebase.auth.FirebaseAuth
        .getInstance().currentUser?.uid ?: ""

    val messages = remember(chatId) { mutableStateListOf<FirestoreMessage>() }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    DisposableEffect(chatId) {
        val listener = repository.listenMessages(chatId) { result, message ->
            messages.clear()
            messages.addAll(result)
            error = message
        }
        onDispose { listener.remove() }
    }

    Column(
        Modifier.fillMaxSize().imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .statusBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
            }
            Text(title, fontWeight = FontWeight.SemiBold)
        }

        if (error != null) {
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val mine = message.senderId == uid

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    }
                ) {
                    Column(
                        Modifier.widthIn(max = 290.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (mine) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .padding(12.dp)
                    ) {
                        Text(message.text)
                        Text(
                            formatTimestamp(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(Modifier.width(8.dp))

            FilledIconButton(
                onClick = {
                    val content = text.trim()
                    if (content.isNotEmpty() && !sending) {
                        sending = true
                        repository.sendMessage(chatId, content) { success, sendError ->
                            sending = false
                            if (success) {
                                text = ""
                            } else {
                                error = sendError ?: "Не удалось отправить сообщение"
                            }
                        }
                    }
                },
                enabled = text.isNotBlank() && !sending
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Отправить")
            }
        }
    }
}
