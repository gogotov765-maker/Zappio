package app.zappio.messenger

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FirestoreChat(
    val id: String = "",
    val members: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageAt: Timestamp? = null,
    val createdAt: Timestamp? = null
)

data class FirestoreMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
    val readBy: List<String> = emptyList()
)

fun formatTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp.seconds * 1000))
}
