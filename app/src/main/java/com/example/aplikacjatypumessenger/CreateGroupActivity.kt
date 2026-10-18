package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.SelectableUserAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityCreateGroupBinding
import com.example.aplikacjatypumessenger.utils.Extras
import com.example.aplikacjatypumessenger.utils.collectWhenStarted
import com.example.aplikacjatypumessenger.utils.showToast
import com.example.aplikacjatypumessenger.viewmodels.CreateGroupEvent
import com.example.aplikacjatypumessenger.viewmodels.CreateGroupViewModel

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private val viewModel: CreateGroupViewModel by viewModels { CreateGroupViewModel.Factory }
    private val adapter = SelectableUserAdapter { item -> viewModel.toggle(item.user.id) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.usersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.usersRecyclerView.adapter = adapter
        binding.cancelButton.setOnClickListener { finish() }
        binding.createGroupButton.setOnClickListener {
            viewModel.createGroup(binding.groupNameEditText.text.toString())
        }

        collectWhenStarted(viewModel.uiState) { state ->
            adapter.submitList(state.users)
            binding.selectedCountText.text = getString(R.string.selected_users_count, state.selectedCount)
            binding.createGroupButton.isEnabled = !state.isSaving
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is CreateGroupEvent.ShowMessage -> showToast(event.text)
                is CreateGroupEvent.OpenGroupChat -> {
                    startActivity(Intent(this, GroupChatActivity::class.java).putExtra(Extras.CHAT_ID, event.chatId))
                    finish()
                }
            }
        }
    }
}
