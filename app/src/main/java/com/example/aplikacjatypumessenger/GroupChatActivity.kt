package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.MessageAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityGroupChatBinding
import com.example.aplikacjatypumessenger.models.Message
import com.example.aplikacjatypumessenger.services.MessagingService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class GroupChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupChatBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var messageAdapter: MessageAdapter
    private var messageList = mutableListOf<Message>()
    private var chatId: String = ""
    private var isGroup: Boolean = false
    private var messageListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "GroupChatActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        chatId = intent.getStringExtra("chatId") ?: ""
        isGroup = intent.getBooleanExtra("isGroup", false)

        if (chatId.isEmpty()) {
            Log.e(TAG, "Brak chatId!")
            Toast.makeText(this, "Błąd: brak ID czatu", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews()
        loadGroupInfo()
        loadMessages()
        setupClickListeners()
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
                    binding.tvGroupMembers.text = "Grupa: $groupName"
                }
        }
    }

    private fun loadMessages() {
        messageListener?.remove()
        messageListener = db.collection("messages")
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Błąd ładowania wiadomości", error)
                    Toast.makeText(this, "Błąd ładowania wiadomości", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    val message = change.document.toObject(Message::class.java)
                    when (change.type) {
                        DocumentChange.Type.ADDED -> {
                            val index = messageList.indexOfFirst { it.id == message.id }
                            if (index == -1) {
                                messageList.add(message)
                                messageAdapter.notifyItemInserted(messageList.size - 1)
                                binding.messagesRecyclerView.scrollToPosition(messageList.size - 1)
                            } else {
                                messageList[index] = message
                                messageAdapter.notifyItemChanged(index)
                            }
                        }
                        DocumentChange.Type.MODIFIED -> {
                            val index = messageList.indexOfFirst { it.id == message.id }
                            if (index != -1) {
                                messageList[index] = message
                                messageAdapter.notifyItemChanged(index)
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            val index = messageList.indexOfFirst { it.id == message.id }
                            if (index != -1) {
                                messageList.removeAt(index)
                                messageAdapter.notifyItemRemoved(index)
                            }
                        }
                    }
                }
            }
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            val messageText = binding.getMessage.text?.toString()?.trim()
            if (!messageText.isNullOrEmpty()) {
                sendMessage(messageText)
                binding.getMessage.text?.clear()
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.toolbar.inflateMenu(R.menu.group_chat_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_manage_group -> {
                    startActivity(Intent(this, GroupManagementActivity::class.java).putExtra("chatId", chatId))
                    true
                }
                R.id.action_group_info -> {
                    showGroupInfo()
                    true
                }
                else -> false
            }
        }
    }

    private fun sendMessage(text: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val messageId = db.collection("messages").document().id
        val timestamp = System.currentTimeMillis()

        val message = Message(
            id = messageId,
            senderId = currentUserId,
            chatId = chatId,
            text = text,
            timestamp = timestamp,
            type = "text",
            status = "sent",
            isGroupMessage = isGroup
        )

        messageList.add(message)
        messageAdapter.notifyItemInserted(messageList.size - 1)
        binding.messagesRecyclerView.scrollToPosition(messageList.size - 1)

        db.collection("messages").document(messageId)
            .set(message)
            .addOnSuccessListener {
                updateChatLastMessage(message)
                sendPushNotifications(message)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Błąd wysyłania wiadomości", e)
                Toast.makeText(this, "Błąd wysyłania wiadomości", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendPushNotifications(message: Message) {
        db.collection("chats").document(chatId)
            .get()
            .addOnSuccessListener { doc ->
                val participants = doc.get("participants") as? List<String> ?: emptyList()
                val currentUserId = auth.currentUser?.uid ?: return@addOnSuccessListener

                participants.forEach { userId ->
                    if (userId != currentUserId) {
                        // Tutaj wywołaj MessagingService lub backend do wysyłki FCM
                    }
                }
            }
    }

    private fun showGroupInfo() {
        val intent = Intent(this, GroupInfoActivity::class.java).apply {
            putExtra("GROUP_ID", chatId)
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

    override fun onDestroy() {
        super.onDestroy()
        messageListener?.remove()
    }
}
