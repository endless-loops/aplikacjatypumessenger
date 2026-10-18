package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.GroupMemberAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityGroupManagementBinding
import com.example.aplikacjatypumessenger.utils.Extras
import com.example.aplikacjatypumessenger.utils.UiText
import com.example.aplikacjatypumessenger.utils.collectWhenStarted
import com.example.aplikacjatypumessenger.utils.showToast
import com.example.aplikacjatypumessenger.viewmodels.GroupManagementEvent
import com.example.aplikacjatypumessenger.viewmodels.GroupManagementViewModel

/** Informacje o grupie, lista członków i akcje administratora. */
class GroupManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupManagementBinding
    private val viewModel: GroupManagementViewModel by viewModels { GroupManagementViewModel.Factory }
    private lateinit var chatId: String

    private val memberAdapter = GroupMemberAdapter(
        onRemove = { member ->
            confirm(getString(R.string.confirm_remove_member, member.user.username)) {
                viewModel.removeMember(member.user.id)
            }
        },
        onMakeAdmin = { member ->
            confirm(getString(R.string.confirm_make_admin, member.user.username)) {
                viewModel.makeAdmin(member.user.id)
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatId = intent.getStringExtra(Extras.CHAT_ID).orEmpty()
        if (chatId.isEmpty()) {
            showToast(UiText.Res(R.string.error_missing_chat_data))
            finish()
            return
        }

        binding = ActivityGroupManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.membersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.membersRecyclerView.adapter = memberAdapter

        binding.addMembersButton.setOnClickListener {
            startActivity(Intent(this, AddMembersActivity::class.java).putExtra(Extras.CHAT_ID, chatId))
        }
        binding.leaveGroupButton.setOnClickListener {
            confirm(R.string.confirm_leave_group) { viewModel.leaveGroup() }
        }
        binding.deleteGroupButton.setOnClickListener {
            confirm(R.string.confirm_delete_group) { viewModel.deleteGroup() }
        }

        collectWhenStarted(viewModel.uiState) { state ->
            binding.toolbar.title = state.groupName ?: getString(R.string.default_group_name)
            binding.memberCountTextView.text = getString(R.string.group_member_count, state.members.size)
            memberAdapter.canManage = state.isCurrentUserAdmin
            memberAdapter.submitList(state.members)

            val adminVisibility = if (state.isCurrentUserAdmin) View.VISIBLE else View.GONE
            binding.addMembersButton.visibility = adminVisibility
            binding.deleteGroupButton.visibility = adminVisibility
            binding.addMembersButton.isEnabled = !state.isBusy
            binding.leaveGroupButton.isEnabled = !state.isBusy
            binding.deleteGroupButton.isEnabled = !state.isBusy
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is GroupManagementEvent.ShowMessage -> showToast(event.text, long = true)
                GroupManagementEvent.Close -> finish()
            }
        }
    }

    private fun confirm(@StringRes messageRes: Int, onConfirm: () -> Unit) = confirm(getString(messageRes), onConfirm)

    private fun confirm(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.action_confirm) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
