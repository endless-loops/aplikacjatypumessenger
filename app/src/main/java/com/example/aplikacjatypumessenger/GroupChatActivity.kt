package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
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

/**
 * Ekran czatu grupowego. Wygląda podobnie do [ChatActivity], ale przy wiadomościach
 * pokazuje nadawcę, a w menu ma przejście do zarządzania grupą.
 */
class GroupChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupChatBinding
    private val viewModel: GroupChatViewModel by viewModels { GroupChatViewModel.Factory }
    // Długie przytrzymanie własnej wiadomości pokazuje, kto ją przeczytał
    private val messageAdapter = MessageAdapter(showSenderNames = true) { viewModel.onMessageLongClick(it.id) }
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
                    // Grupa usunięta albo użytkownik usunięty z grupy
                    showToast(event.text)
                    finish()
                }
                is GroupChatEvent.ShowReadReceipts -> showReadReceipts(event.readBy, event.notReadBy)
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

    /** Okno „Informacje o odczycie” z dwiema listami: przeczytali / jeszcze nie przeczytali. */
    private fun showReadReceipts(readBy: List<String?>, notReadBy: List<String?>) {
        fun section(@StringRes title: Int, names: List<String?>): String {
            val lines = names.map { "• " + (it ?: getString(R.string.default_user_name)) }
                .ifEmpty { listOf(getString(R.string.read_receipts_nobody)) }
            return getString(title) + ":\n" + lines.joinToString("\n")
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.read_receipts_title)
            .setMessage(section(R.string.read_receipts_read, readBy) + "\n\n" + section(R.string.read_receipts_not_read, notReadBy))
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    private fun send() {
        val text = binding.getMessage.text?.toString().orEmpty()
        if (text.isBlank()) return
        viewModel.sendMessage(text)
        binding.getMessage.text?.clear()
    }
}
