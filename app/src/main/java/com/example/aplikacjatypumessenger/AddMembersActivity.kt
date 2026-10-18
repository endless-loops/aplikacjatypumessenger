package com.example.aplikacjatypumessenger

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.SelectableUserAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityAddMembersBinding
import com.example.aplikacjatypumessenger.utils.Extras
import com.example.aplikacjatypumessenger.utils.UiText
import com.example.aplikacjatypumessenger.utils.applySystemBarsPadding
import com.example.aplikacjatypumessenger.utils.collectWhenStarted
import com.example.aplikacjatypumessenger.utils.showToast
import com.example.aplikacjatypumessenger.viewmodels.AddMembersEvent
import com.example.aplikacjatypumessenger.viewmodels.AddMembersViewModel

class AddMembersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddMembersBinding
    private val viewModel: AddMembersViewModel by viewModels { AddMembersViewModel.Factory }
    private val adapter = SelectableUserAdapter { item -> viewModel.toggle(item.user.id) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(Extras.CHAT_ID).isNullOrEmpty()) {
            showToast(UiText.Res(R.string.error_missing_chat_data))
            finish()
            return
        }

        binding = ActivityAddMembersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setQuery(newText.orEmpty())
                return true
            }
        })
        binding.btnAddMembers.setOnClickListener { viewModel.addSelected() }

        collectWhenStarted(viewModel.uiState) { state ->
            binding.toolbar.title = state.groupName?.let { getString(R.string.add_members_to, it) }
                ?: getString(R.string.add_members)
            adapter.submitList(state.users)
            binding.btnAddMembers.isEnabled = state.selectedCount > 0 && !state.isSaving
            binding.btnAddMembers.text = when {
                state.isSaving -> getString(R.string.adding_members)
                state.selectedCount > 0 -> getString(R.string.add_members_count, state.selectedCount)
                else -> getString(R.string.add_members)
            }
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is AddMembersEvent.ShowMessage -> showToast(event.text, long = true)
                is AddMembersEvent.Done -> {
                    showToast(UiText.Res(R.string.members_added, listOf(event.addedCount)))
                    finish()
                }
            }
        }
    }
}
