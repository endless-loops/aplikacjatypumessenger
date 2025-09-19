package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.MessageAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityGroupChatBinding
import com.example.aplikacjatypumessenger.models.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class GroupChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGroupChatBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var messageAdapter: MessageAdapter
    private var messageList = mutableListOf<Message>()
    private var chatId: String = ""
    private var isGroup: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        db = Firebase.firestore

        chatId = intent.getStringExtra("chatId") ?: ""
        isGroup = intent.getBooleanExtra("isGroup", false)

        setupViews()
        loadMessages()
        setupClickListeners()
        loadGroupInfo()
    }

    private fun setupViews() {
        messageAdapter = MessageAdapter(messageList, auth.currentUser?.uid ?: "")

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupChatActivity)
            adapter = messageAdapter
        }
    }

    private fun loadGroupInfo() {
        if (isGroup && chatId.isNotEmpty()) {
            db.collection("chats").document(chatId)
                .get()
                .addOnSuccessListener { document ->
                    val groupName = document.getString("groupName") ?: "Grupa"
                    binding.chatUserNameTextView.text = groupName
                }
        }
    }

    private fun loadMessages() {
        if (chatId.isEmpty()) return

        db.collection("messages")
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Błąd ładowania wiadomości", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                messageList.clear()
                snapshot?.documents?.forEach { document ->
                    val message = document.toObject(Message::class.java)
                    message?.let { messageList.add(it) }
                }
                messageAdapter.notifyDataSetChanged()

                if (messageList.isNotEmpty()) {
                    binding.messagesRecyclerView.smoothScrollToPosition(messageList.size - 1)
                }
            }
    }

    private fun setupClickListeners() {
        binding.sendButton.setOnClickListener {
            val messageText = binding.messageEditText.text?.toString()?.trim()
            if (!messageText.isNullOrEmpty()) {
                sendMessage(messageText)
                binding.messageEditText.text?.clear()
            }
        }

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.groupInfoButton.setOnClickListener {
            if (isGroup) {
                showGroupInfo()
            }
        }
    }

    private fun sendMessage(text: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val messageId = db.collection("messages").document().id

        val message = Message(
            id = messageId,
            senderId = currentUserId,
            chatId = chatId,
            text = text,
            timestamp = System.currentTimeMillis(),
            type = "text",
            isGroupMessage = isGroup  // 👈 Nowe pole
        )

        db.collection("messages").document(messageId)
            .set(message)
            .addOnSuccessListener {
                updateChatLastMessage(message)
                sendPushNotifications(message)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Błąd wysyłania wiadomości", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendPushNotifications(message: Message) {
        // Pobierz tokeny wszystkich uczestników czatu i wyślij powiadomienia
        db.collection("chats").document(chatId)
            .get()
            .addOnSuccessListener { document ->
                val participants = document.get("participants") as? List<String> ?: emptyList()
                val currentUser = auth.currentUser?.uid ?: return@addOnSuccessListener

                participants.forEach { userId ->
                    if (userId != currentUser) {
                        sendNotificationToUser(userId, message)
                    }
                }
            }
    }

    private fun sendNotificationToUser(userId: String, message: Message) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                val fcmToken = document.getString("fcmToken")
                val username = document.getString("username") ?: "Użytkownik"

                fcmToken?.let { token ->
                    // Tutaj wyślij powiadomienie FCM do użytkownika
                    // (wymaga backendu lub Cloud Functions)
                }
            }
    }

    private fun showGroupInfo() {
        val intent = Intent(this, GroupInfoActivity::class.java).apply {
            putExtra("chatId", chatId)
        }
        startActivity(intent)
    }

    private fun updateChatLastMessage(message: Message) {
        db.collection("chats").document(chatId)
            .update("lastMessage", message)
            .addOnFailureListener {
                Toast.makeText(this, "Błąd aktualizacji czatu", Toast.LENGTH_SHORT).show()
            }
    }
}