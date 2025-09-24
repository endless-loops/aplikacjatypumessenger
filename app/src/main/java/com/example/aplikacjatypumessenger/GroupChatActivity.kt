// app/src/main/java/com/example/aplikacjatypumessenger/GroupChatActivity.kt

package com.example.aplikacjatypumessenger

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
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class GroupChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: GroupViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter

    private val auth = Firebase.auth
    private val db = Firebase.firestore

    private var groupId = ""
    private var groupName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false) // ✅ Wyłącz domyślny tytuł

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        groupId = intent.getStringExtra("groupId") ?: ""
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

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                messageAdapter.updateList(messages)
                scrollToBottomIfNeeded()

                // Oznacz wiadomości jako przeczytane
                val currentUserId = auth.currentUser?.uid ?: ""
                messages.forEach { message ->
                    if (message.senderId != currentUserId && !message.seen) {
                        // Dla grup oznaczamy jako przeczytane lokalnie
                        // (pełna implementacja wymagałaby śledzenia przez kogo zostały przeczytane)
                    }
                }
            }
        }
    }

    private fun setupViews() {
        val currentUserId = auth.currentUser?.uid ?: ""

        // ✅ POPRAWIONE: true = jest grupowy
        messageAdapter = MessageAdapter(currentUserId, true) // true = jest grupowy

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupChatActivity).also {
                it.stackFromEnd = true
            }
            adapter = messageAdapter
        }

        binding.chatUserNameTextView.text = groupName.ifEmpty { "Grupa" }
    }

    private fun setupClickListeners() {
        binding.sendButton.setOnClickListener {
            val text = binding.messageEditText.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                viewModel.sendGroupMessage(groupId, text) { success, error ->
                    if (success) {
                        binding.messageEditText.text?.clear()
                    } else {
                        showError("Błąd wysyłania: $error")
                    }
                }
            }
        }

        //binding.backButton.setOnClickListener { finish() }
    }

    private fun loadGroupInfo() {
        if (groupId.isNotEmpty()) {
            viewModel.getGroupDetails(groupId) { group ->
                group?.let {
                    binding.chatUserNameTextView.text = it.name
                    groupName = it.name
                } ?: run {
                    // Fallback: spróbuj pobrać z Firestore bezpośrednio
                    db.collection("chats").document(groupId)
                        .get()
                        .addOnSuccessListener { doc ->
                            val name = doc.getString("groupName") ?: "Grupa"
                            binding.chatUserNameTextView.text = name
                            groupName = name
                        }
                }
            }
        }
    }

    private fun scrollToBottomIfNeeded() {
        binding.messagesRecyclerView.post {
            val layoutManager = binding.messagesRecyclerView.layoutManager as? LinearLayoutManager
            val lastVisible = layoutManager?.findLastVisibleItemPosition() ?: -1
            val itemCount = messageAdapter.itemCount

            if (lastVisible >= itemCount - 2) {
                binding.messagesRecyclerView.scrollToPosition(itemCount - 1)
            }
        }
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onCleared()
    }
}