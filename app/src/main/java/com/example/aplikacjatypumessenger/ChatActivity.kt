package com.example.aplikacjatypumessenger

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.MessageAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityChatBinding
import com.example.aplikacjatypumessenger.models.Message
import com.example.aplikacjatypumessenger.managers.MessageStatusManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageStatusManager: MessageStatusManager
    private var messageList = mutableListOf<Message>()
    private var chatId = ""
    private var otherUserId = ""
    private var messageListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "ChatActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        db = Firebase.firestore
        messageStatusManager = MessageStatusManager(db)

        // Get data from intent
        chatId = intent.getStringExtra("chatId") ?: ""
        otherUserId = intent.getStringExtra("otherUserId") ?: ""

        // Validate required data
        if (chatId.isEmpty() || otherUserId.isEmpty()) {
            Log.e(TAG, "Missing chatId or otherUserId")
            showError("Błąd: Brak wymaganych danych czatu")
            finish()
            return
        }

        // Check if user is authenticated
        if (auth.currentUser == null) {
            Log.e(TAG, "User not authenticated")
            showError("Błąd: Użytkownik nie jest zalogowany")
            finish()
            return
        }

        Log.d(TAG, "ChatActivity started with chatId: $chatId, otherUserId: $otherUserId")

        setupViews()
        setupClickListeners()
        loadMessages()

        // Mark messages as delivered when opening chat
        messageStatusManager.markMessagesAsDelivered(chatId, auth.currentUser?.uid ?: "")
    }

    private fun setupViews() {
        val currentUserId = auth.currentUser?.uid ?: ""
        messageAdapter = MessageAdapter(messageList, currentUserId)

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).also {
                it.stackFromEnd = true // Start from bottom
            }
            adapter = messageAdapter
        }

        loadOtherUserInfo()
    }

    private fun loadOtherUserInfo() {
        if (otherUserId.isNotEmpty()) {
            db.collection("users").document(otherUserId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val user = doc.toObject(com.example.aplikacjatypumessenger.models.User::class.java)
                        user?.let {
                            binding.chatUserNameTextView.text = it.username
                            Log.d(TAG, "Loaded other user info: ${it.username}")
                        }
                    } else {
                        Log.w(TAG, "Other user document doesn't exist")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error loading other user info", exception)
                }
        }
    }

    private fun loadMessages() {
        if (chatId.isEmpty()) return

        // Usuń poprzedni listener, jeśli istnieje
        messageListener?.remove()

        messageListener = db.collection("messages")
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error loading messages", error)
                    showError("Błąd ładowania wiadomości: ${error.message}")
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    try {
                        val message = change.document.toObject(Message::class.java)

                        when (change.type) {
                            DocumentChange.Type.ADDED -> {
                                val existingIndex = messageList.indexOfFirst { it.id == message.id }
                                if (existingIndex == -1) {
                                    val insertIndex = findInsertPosition(message.timestamp)
                                    messageList.add(insertIndex, message)
                                    messageAdapter.notifyItemInserted(insertIndex)
                                    scrollToBottomIfNeeded()

                                    // Automatyczne oznaczenie jako "read", jeśli wiadomość od innego użytkownika
                                    if (message.senderId != auth.currentUser?.uid) {
                                        messageStatusManager.updateMessageStatus(message.id, "read")
                                        messageList[insertIndex] = message.copy(status = "read", seen = true)
                                        messageAdapter.notifyItemChanged(insertIndex)
                                    }
                                }
                            }

                            DocumentChange.Type.MODIFIED -> {
                                val existingIndex = messageList.indexOfFirst { it.id == message.id }
                                if (existingIndex != -1) {
                                    messageList[existingIndex] = message
                                    messageAdapter.notifyItemChanged(existingIndex)
                                }
                            }

                            DocumentChange.Type.REMOVED -> {
                                val existingIndex = messageList.indexOfFirst { it.id == message.id }
                                if (existingIndex != -1) {
                                    messageList.removeAt(existingIndex)
                                    messageAdapter.notifyItemRemoved(existingIndex)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing message document", e)
                    }
                }
            }
    }

    private fun findInsertPosition(timestamp: Long): Int {
        for (i in messageList.indices) {
            if (messageList[i].timestamp > timestamp) {
                return i
            }
        }
        return messageList.size
    }

    private fun scrollToBottomIfNeeded() {
        binding.messagesRecyclerView.post {
            val layoutManager = binding.messagesRecyclerView.layoutManager as? LinearLayoutManager
            val lastVisible = layoutManager?.findLastVisibleItemPosition() ?: -1
            val itemCount = messageAdapter.itemCount

            // Auto-scroll if user was already at bottom
            if (lastVisible >= itemCount - 2) {
                binding.messagesRecyclerView.scrollToPosition(itemCount - 1)
            }
        }
    }

    private fun setupClickListeners() {
        binding.sendButton.setOnClickListener {
            val text = binding.messageEditText.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                sendMessage(text)
                binding.messageEditText.text?.clear()
            } else {
                Log.d(TAG, "Cannot send empty message")
            }
        }

        binding.backButton.setOnClickListener { finish() }
    }

    private fun sendMessage(text: String) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.e(TAG, "Cannot send message: user not authenticated")
            showError("Błąd: Użytkownik nie jest zalogowany")
            return
        }

        if (chatId.isEmpty()) {
            Log.e(TAG, "Cannot send message: chatId is empty")
            showError("Błąd: Brak ID czatu")
            return
        }

        val messageId = db.collection("messages").document().id
        val timestamp = System.currentTimeMillis()

        val message = Message(
            id = messageId,
            senderId = currentUserId,
            receiverId = otherUserId,
            chatId = chatId,
            text = text,
            timestamp = timestamp,
            type = "text",
            status = "sending", // Start with sending status
            seen = false
        )

        // Add message to local list immediately with "sending" status
        messageList.add(message)
        messageAdapter.notifyItemInserted(messageList.size - 1)
        scrollToBottomIfNeeded()

        Log.d(TAG, "Sending message: $text to chatId: $chatId")

        db.collection("messages").document(messageId)
            .set(message)
            .addOnSuccessListener {
                Log.d(TAG, "Message sent successfully")
                // Update status to "sent"
                messageStatusManager.updateMessageStatus(messageId, "sent")
                updateChatLastMessage(message)
                ensureChatExists()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to send message", exception)
                // Update status to "failed"
                messageStatusManager.updateMessageStatus(messageId, "failed")
                showError("Nie udało się wysłać wiadomości: ${exception.message}")
            }
    }

    private fun ensureChatExists() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("chats").document(chatId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    // Create chat document if it doesn't exist
                    val chatData = hashMapOf(
                        "participants" to listOf(currentUserId, otherUserId),
                        "createdAt" to System.currentTimeMillis(),
                        "lastMessage" to null
                    )

                    db.collection("chats").document(chatId)
                        .set(chatData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Chat document created successfully")
                        }
                        .addOnFailureListener { exception ->
                            Log.e(TAG, "Failed to create chat document", exception)
                        }
                }
            }
    }

    private fun updateChatLastMessage(message: Message) {
        val lastMessageData = hashMapOf(
            "text" to message.text,
            "senderId" to message.senderId,
            "timestamp" to message.timestamp,
            "seen" to message.seen
        )

        db.collection("chats").document(chatId)
            .update("lastMessage", lastMessageData)
            .addOnSuccessListener {
                Log.d(TAG, "Chat last message updated successfully")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to update chat last message", exception)
            }
    }

    override fun onResume() {
        super.onResume()

        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null && chatId.isNotEmpty()) {

            // 1️⃣ Aktualizacja statusu w Firestore
            messageStatusManager.markMessagesAsRead(chatId, currentUserId)

            // 2️⃣ Natychmiastowa aktualizacja lokalnej listy wiadomości w UI
            var updated = false
            for ((index, msg) in messageList.withIndex()) {
                // Zmieniamy tylko wiadomości od drugiego użytkownika, które nie są jeszcze “read”
                if (msg.senderId != currentUserId && (msg.status == "sent" || msg.status == "delivered")) {
                    messageList[index] = msg.copy(status = "read", seen = true)
                    updated = true
                }
            }

            if (updated) {
                messageAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messageListener?.remove()
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        Log.e(TAG, "Error shown to user: $msg")
    }
}