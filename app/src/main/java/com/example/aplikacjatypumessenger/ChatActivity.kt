package com.example.aplikacjatypumessenger

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.MessageAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityChatBinding
import com.example.aplikacjatypumessenger.services.MessagingService
import com.example.aplikacjatypumessenger.utils.Extras
import com.example.aplikacjatypumessenger.utils.UiText
import com.example.aplikacjatypumessenger.utils.collectWhenStarted
import com.example.aplikacjatypumessenger.utils.scrollToNewMessages
import com.example.aplikacjatypumessenger.utils.showToast
import com.example.aplikacjatypumessenger.viewmodels.ChatEvent
import com.example.aplikacjatypumessenger.viewmodels.ChatViewModel

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels { ChatViewModel.Factory }
    private val messageAdapter = MessageAdapter()
    private lateinit var chatId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatId = intent.getStringExtra(Extras.CHAT_ID).orEmpty()
        if (chatId.isEmpty() || intent.getStringExtra(Extras.OTHER_USER_ID).isNullOrEmpty()) {
            showToast(UiText.Res(R.string.error_missing_chat_data))
            finish()
            return
        }

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messagesRecyclerView.adapter = messageAdapter

        binding.backButton.setOnClickListener { finish() }
        binding.sendButton.setOnClickListener { send() }
        binding.messageEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send()
                true
            } else {
                false
            }
        }

        collectWhenStarted(viewModel.uiState) { state ->
            binding.chatUserNameTextView.text = state.title ?: getString(R.string.default_user_name)
            val previousCount = messageAdapter.itemCount
            messageAdapter.submitList(state.messages) {
                binding.messagesRecyclerView.scrollToNewMessages(previousCount, state.messages.lastOrNull()?.isMine == true)
            }
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is ChatEvent.ShowMessage -> showToast(event.text, long = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenVisibilityChanged(true)
        NotificationManagerCompat.from(this).cancel(MessagingService.notificationIdFor(chatId))
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenVisibilityChanged(false)
    }

    private fun send() {
        val text = binding.messageEditText.text?.toString().orEmpty()
        if (text.isBlank()) return
        viewModel.sendMessage(text)
        binding.messageEditText.text?.clear()
    }
}
