package app.zappio.messenger

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.security.MessageDigest
import java.util.Locale

class ChatRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val phoneRegex = Regex("^\\+[1-9]\\d{7,14}$")
    private val usernameRegex = Regex("^[a-z0-9_]{3,20}$")

    private fun currentUid(): String? = auth.currentUser?.uid

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun normalizePhone(phone: String): String =
        phone.filter { it.isDigit() || it == '+' }

    // Создание профиля: users + индексы username и номера (в одной транзакции)
    fun completeProfile(
        displayName: String,
        username: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false, "Сначала войдите в аккаунт")
            return
        }

        val name = displayName.trim()
        val cleanUsername = username.trim().removePrefix("@").lowercase(Locale.ROOT)
        val phone = normalizePhone(user.phoneNumber ?: "")

        if (name.length !in 2..40) {
            onResult(false, "Имя должно содержать от 2 до 40 символов")
            return
        }
        if (!cleanUsername.matches(usernameRegex)) {
            onResult(false, "Username: 3–20 символов, только латиница, цифры и _")
            return
        }
        if (!phone.matches(phoneRegex)) {
            onResult(false, "Не удалось определить номер телефона")
            return
        }

        val userRef = db.collection("users").document(user.uid)
        val usernameRef = db.collection("usernameLookup").document(hash(cleanUsername))
        val phoneRef = db.collection("phoneLookup").document(hash(phone))

        db.runTransaction { tx ->
            val usernameOwner = tx.get(usernameRef).getString("uid")
            val phoneOwner = tx.get(phoneRef).getString("uid")

            if (usernameOwner != null && usernameOwner != user.uid) {
                throw IllegalStateException("Этот username уже занят")
            }
            if (phoneOwner != null && phoneOwner != user.uid) {
                throw IllegalStateException("Этот номер уже связан с другим аккаунтом")
            }

            // Номер телефона в публичный профиль НЕ кладём — он есть только в phoneLookup (в виде хэша)
            tx.set(
                userRef,
                mapOf(
                    "uid" to user.uid,
                    "displayName" to name,
                    "username" to cleanUsername,
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            tx.set(usernameRef, mapOf("uid" to user.uid))
            tx.set(phoneRef, mapOf("uid" to user.uid))
            true
        }.addOnSuccessListener {
            onResult(true, null)
        }.addOnFailureListener {
            onResult(false, it.localizedMessage ?: "Не удалось сохранить профиль")
        }
    }

    // Поиск пользователя по точному номеру телефона
    fun searchUserByPhone(phone: String, onResult: (String?, String?) -> Unit) {
        val normalized = normalizePhone(phone)
        if (!normalized.matches(phoneRegex)) {
            onResult(null, "Введите номер с кодом страны, например +79991234567")
            return
        }

        db.collection("phoneLookup").document(hash(normalized)).get()
            .addOnSuccessListener { document ->
                val uid = document.getString("uid")
                if (uid == null || uid == currentUid()) {
                    onResult(null, "Пользователь не найден")
                } else {
                    onResult(uid, null)
                }
            }
            .addOnFailureListener { onResult(null, it.localizedMessage) }
    }

    fun getUser(uid: String, onResult: (Map<String, Any>?, String?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { onResult(it.data, null) }
            .addOnFailureListener { onResult(null, it.localizedMessage) }
    }

    // Создание или получение личного чата
    fun getOrCreatePrivateChat(otherUid: String, onResult: (String?, String?) -> Unit) {
        val myUid = currentUid()
        if (myUid == null || myUid == otherUid) {
            onResult(null, "Невозможно создать чат")
            return
        }

        val members = listOf(myUid, otherUid).sorted()
        val chatId = hash(members.joinToString(":"))
        val chatRef = db.collection("chats").document(chatId)

        db.runTransaction { tx ->
            if (!tx.get(chatRef).exists()) {
                tx.set(
                    chatRef,
                    mapOf(
                        "members" to members,
                        "lastMessage" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "lastMessageAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            chatId
        }.addOnSuccessListener {
            onResult(it, null)
        }.addOnFailureListener {
            onResult(null, it.localizedMessage)
        }
    }

    fun sendMessage(chatId: String, text: String, onResult: (Boolean, String?) -> Unit) {
        val uid = currentUid()
        val messageText = text.trim()
        if (uid == null || messageText.isEmpty()) {
            onResult(false, "Пустое сообщение")
            return
        }

        val chatRef = db.collection("chats").document(chatId)
        val messageRef = chatRef.collection("messages").document()

        val batch = db.batch()
        batch.set(
            messageRef,
            mapOf(
                "senderId" to uid,
                "text" to messageText,
                "createdAt" to FieldValue.serverTimestamp(),
                "readBy" to listOf(uid)
            )
        )
        batch.update(
            chatRef,
            mapOf(
                "lastMessage" to messageText,
                "lastMessageAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit()
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { onResult(false, it.localizedMessage) }
    }

    fun listenMessages(
        chatId: String,
        onUpdate: (List<FirestoreMessage>, String?) -> Unit
    ): ListenerRegistration =
        db.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(emptyList(), error.localizedMessage)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.map { d ->
                    FirestoreMessage(
                        id = d.id,
                        senderId = d.getString("senderId") ?: "",
                        text = d.getString("text") ?: "",
                        createdAt = d.getTimestamp("createdAt"),
                        readBy = (d.get("readBy") as? List<*>)?.filterIsInstance<String>()
                            ?: emptyList()
                    )
                } ?: emptyList()
                onUpdate(messages, null)
            }

    // Список чатов. Без orderBy в запросе — иначе Firestore потребует составной индекс;
    // сортируем на клиенте.
    fun listenChats(onUpdate: (List<FirestoreChat>, String?) -> Unit): ListenerRegistration? {
        val uid = currentUid() ?: return null

        return db.collection("chats")
            .whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(emptyList(), error.localizedMessage)
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents?.map { d ->
                    FirestoreChat(
                        id = d.id,
                        members = (d.get("members") as? List<*>)?.filterIsInstance<String>()
                            ?: emptyList(),
                        lastMessage = d.getString("lastMessage") ?: "",
                        lastMessageAt = d.getTimestamp("lastMessageAt"),
                        createdAt = d.getTimestamp("createdAt")
                    )
                }?.sortedByDescending {
                    (it.lastMessageAt ?: it.createdAt)?.seconds ?: Long.MAX_VALUE
                } ?: emptyList()
                onUpdate(chats, null)
            }
    }
}
