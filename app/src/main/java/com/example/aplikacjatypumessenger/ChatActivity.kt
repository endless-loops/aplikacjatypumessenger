package com.example.aplikacjatypumessenger

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.MessageAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityChatBinding
import com.example.aplikacjatypumessenger.viewmodels.ChatViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter

    private val auth = Firebase.auth
    private val db = Firebase.firestore

    private var chatId = ""
    private var otherUserId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        chatId = intent.getStringExtra("chatId") ?: ""
        otherUserId = intent.getStringExtra("otherUserId") ?: ""

        if (chatId.isEmpty() || otherUserId.isEmpty()) {
            showError("Błąd: Brak wymaganych danych czatu")
            finish()
            return
        }

        if (auth.currentUser == null) {
            showError("Błąd: Użytkownik nie jest zalogowany")
            finish()
            return
        }

        setupViews()
        setupObservers()
        setupClickListeners()
        loadOtherUserInfo()

        viewModel.startListening(chatId)
    }

    private fun setupViews() {
        val currentUserId = auth.currentUser?.uid ?: ""

        // ✅ POPRAWIONE: Tylko currentUserId, bez pustej listy
        messageAdapter = MessageAdapter(currentUserId, false) // false = nie jest grupowy

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).also {
                it.stackFromEnd = true
            }
            adapter = messageAdapter
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                // ✅ POPRAWIONE: Przekazuj listę wiadomości do adaptera
                messageAdapter.updateList(messages)
                scrollToBottomIfNeeded()

                messages.forEach { message ->
                    if (message.senderId != auth.currentUser?.uid && !message.seen) {
                        viewModel.markMessagesAsRead(chatId, message.senderId)
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.sendButton.setOnClickListener {
            val text = binding.messageEditText.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                viewModel.sendMessage(chatId, otherUserId, text) { success, error ->
                    if (success) {
                        binding.messageEditText.text?.clear()
                    } else {
                        showError("Błąd wysyłania: $error")
                    }
                }
            }
        }
    }

    private fun loadOtherUserInfo() {
        if (otherUserId.isNotEmpty()) {
            db.collection("users").document(otherUserId)
                .get()
                .addOnSuccessListener { doc ->
                    val user = doc.toObject(com.example.aplikacjatypumessenger.models.User::class.java)
                    user?.let {
                        binding.chatUserNameTextView.text = it.username
                    }
                }
        }
    }

    private fun scrollToBottomIfNeeded() {
        binding.messagesRecyclerView.post {
            val itemCount = messageAdapter.itemCount
            if (itemCount > 0) {
                binding.messagesRecyclerView.scrollToPosition(itemCount - 1)
            }
        }
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // ViewModel automatycznie wywoła stopListening() w onCleared()
    }
}