package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.MessageAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityGroupChatBinding
import com.example.aplikacjatypumessenger.services.MessagingService
import com.example.aplikacjatypumessenger.utils.Extras
import com.example.aplikacjatypumessenger.utils.UiText
import com.example.aplikacjatypumessenger.utils.applySystemBarsPadding
import com.example.aplikacjatypumessenger.utils.collectWhenStarted
import com.example.aplikacjatypumessenger.utils.scrollToNewMessages
import com.example.aplikacjatypumessenger.utils.showToast
import com.example.aplikacjatypumessenger.viewmodels.GroupChatEvent
import com.example.aplikacjatypumessenger.viewmodels.GroupChatViewModel

class GroupChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupChatBinding
    private val viewModel: GroupChatViewModel by viewModels { GroupChatViewModel.Factory }
    private val messageAdapter = MessageAdapter(showSenderNames = true)
    private lateinit var chatId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatId = intent.getStringExtra(Extras.CHAT_ID).orEmpty()
        if (chatId.isEmpty()) {
            showToast(UiText.Res(R.string.error_missing_chat_data))
            finish()
            return
        }

        binding = ActivityGroupChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messagesRecyclerView.adapter = messageAdapter

        setupToolbar()
        binding.btnSend.setOnClickListener { send() }
        binding.getMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send()
                true
            } else {
                false
            }
        }

        collectWhenStarted(viewModel.uiState) { state ->
            binding.toolbar.title = state.groupName ?: getString(R.string.default_group_name)
            binding.tvGroupMembers.text = getString(R.string.group_member_count, state.memberCount)
            val previousCount = messageAdapter.itemCount
            messageAdapter.submitList(state.messages) {
                binding.messagesRecyclerView.scrollToNewMessages(previousCount, state.messages.lastOrNull()?.isMine == true)
            }
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is GroupChatEvent.ShowMessage -> showToast(event.text, long = true)
                is GroupChatEvent.Close -> {
                    showToast(event.text)
                    finish()
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.group_chat_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_group_info -> {
                    startActivity(Intent(this, GroupManagementActivity::class.java).putExtra(Extras.CHAT_ID, chatId))
                    true
                }
                else -> false
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
        val text = binding.getMessage.text?.toString().orEmpty()
        if (text.isBlank()) return
        viewModel.sendMessage(text)
        binding.getMessage.text?.clear()
    }
}
