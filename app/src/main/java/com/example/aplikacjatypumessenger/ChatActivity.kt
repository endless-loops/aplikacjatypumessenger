package com.example.aplikacjatypumessenger

import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
    private lateinit var sharedPrefs: SharedPreferences

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

        sharedPrefs = getSharedPreferences("chat_prefs", MODE_PRIVATE)

        binding.toolbar.setNavigationOnClickListener { finish() }

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

        // Oznacz wiadomości jako dostarczone przy otwieraniu czatu
        viewModel.markMessagesAsDelivered(chatId, otherUserId)
    }

    override fun onResume() {
        super.onResume()
        // Informuj MessagingService że użytkownik jest w tym czacie — blokuj powiadomienia
        sharedPrefs.edit().putString("current_chat_id", chatId).apply()
    }

    override fun onPause() {
        super.onPause()
        sharedPrefs.edit().putString("current_chat_id", "").apply()
    }

    private fun setupViews() {
        val currentUserId = auth.currentUser?.uid ?: ""
        messageAdapter = MessageAdapter(currentUserId, false)

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).also {
                it.stackFromEnd = true
            }
            adapter = messageAdapter
        }

        // Przycisk wyślij domyślnie nieaktywny
        binding.sendButton.isEnabled = false
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                messageAdapter.updateList(messages)
                scrollToBottomIfNeeded()

                // Oznacz odebrane wiadomości jako przeczytane
                messages.forEach { message ->
                    if (message.senderId != auth.currentUser?.uid && !message.seen) {
                        viewModel.markMessagesAsRead(chatId, message.senderId)
                        return@forEach // Wystarczy jedno wywołanie — markuje wszystkie naraz
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.sendButton.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.sendButton.setOnClickListener {
            val text = binding.messageEditText.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                binding.sendButton.isEnabled = false
                viewModel.sendMessage(chatId, otherUserId, text) { success, error ->
                    if (success) {
                        binding.messageEditText.text?.clear()
                    } else {
                        binding.sendButton.isEnabled = true
                        showError("Błąd wysyłania: $error")
                    }
                }
            }
        }
    }

    private fun loadOtherUserInfo() {
        db.collection("users").document(otherUserId)
            .get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(com.example.aplikacjatypumessenger.models.User::class.java)
                user?.let {
                    binding.chatUserNameTextView.text = it.username
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
}
