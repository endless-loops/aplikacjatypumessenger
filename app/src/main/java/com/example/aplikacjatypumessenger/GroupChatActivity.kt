package com.example.aplikacjatypumessenger

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.MessageAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityChatBinding
import com.example.aplikacjatypumessenger.viewmodels.GroupViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class GroupChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: GroupViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var sharedPrefs: SharedPreferences

    private val auth = Firebase.auth

    private var groupId = ""
    private var groupName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        sharedPrefs = getSharedPreferences("chat_prefs", MODE_PRIVATE)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // Obsługuj zarówno "groupId" (z CreateGroupActivity) jak i "chatId" (z MainActivity)
        groupId = intent.getStringExtra("groupId")
            ?: intent.getStringExtra("chatId")
            ?: ""
        groupName = intent.getStringExtra("groupName") ?: ""

        if (groupId.isEmpty()) {
            showError("Błąd: Brak ID grupy")
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
        loadGroupInfo()

        viewModel.startListeningForGroupMessages(groupId)
    }

    override fun onResume() {
        super.onResume()
        // Informuj MessagingService że użytkownik jest w tym czacie — blokuj powiadomienia
        sharedPrefs.edit().putString("current_chat_id", groupId).apply()
    }

    override fun onPause() {
        super.onPause()
        // Wyczyść — powiadomienia znów mogą się pokazywać
        sharedPrefs.edit().putString("current_chat_id", "").apply()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                messageAdapter.updateList(messages)
                scrollToBottomIfNeeded()
            }
        }
    }

    private fun setupViews() {
        val currentUserId = auth.currentUser?.uid ?: ""
        messageAdapter = MessageAdapter(currentUserId, true)

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupChatActivity).also {
                it.stackFromEnd = true
            }
            adapter = messageAdapter
        }

        binding.chatUserNameTextView.text = groupName.ifEmpty { "Grupa" }
    }

    private fun setupClickListeners() {
        binding.sendButton.isEnabled = false

        binding.messageEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.sendButton.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.sendButton.setOnClickListener {
            val text = binding.messageEditText.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                binding.sendButton.isEnabled = false
                viewModel.sendGroupMessage(groupId, text) { success, error ->
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

    private fun loadGroupInfo() {
        if (groupName.isNotEmpty()) {
            binding.chatUserNameTextView.text = groupName
            return
        }
        viewModel.getGroupDetails(groupId) { group ->
            group?.let {
                binding.chatUserNameTextView.text = it.name
                groupName = it.name
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
