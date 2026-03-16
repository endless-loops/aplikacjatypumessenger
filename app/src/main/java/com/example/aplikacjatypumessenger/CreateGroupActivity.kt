package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.SelectableUserAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityCreateGroupBinding
import com.example.aplikacjatypumessenger.viewmodels.ChatListViewModel
import kotlinx.coroutines.launch

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private val viewModel: ChatListViewModel by viewModels()
    private lateinit var userAdapter: SelectableUserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        updateCreateButtonState(0)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.users.collect { users ->
                userAdapter.submitList(users)
            }
        }
    }

    private fun setupRecyclerView() {
        userAdapter = SelectableUserAdapter { selectedIds ->
            updateCreateButtonState(selectedIds.size)
        }

        binding.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CreateGroupActivity)
            adapter = userAdapter
        }

        binding.searchView.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                val filtered = viewModel.getFilteredUsers(newText?.trim()?.lowercase() ?: "")
                userAdapter.submitList(filtered)
                return true
            }
        })
    }

    private fun updateCreateButtonState(selectedCount: Int) {
        binding.selectedCountText.text = when (selectedCount) {
            0 -> "Wybierz przynajmniej 1 użytkownika"
            1 -> "Wybrano: 1 użytkownik"
            else -> "Wybrano: $selectedCount użytkowników"
        }
        binding.createGroupButton.isEnabled = selectedCount >= 1
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.createGroupButton.setOnClickListener { createGroup() }
    }

    private fun createGroup() {
        val groupName = binding.groupNameEditText.text.toString().trim()

        if (groupName.isEmpty()) {
            binding.groupNameEditText.error = "Wprowadź nazwę grupy"
            binding.groupNameEditText.requestFocus()
            return
        }

        val selectedIds = userAdapter.getSelectedIds()
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Wybierz przynajmniej 1 użytkownika", Toast.LENGTH_SHORT).show()
            return
        }

        binding.createGroupButton.isEnabled = false
        binding.createGroupButton.text = "Tworzenie..."

        viewModel.createGroup(
            name = groupName,
            participantIds = selectedIds.toList(),
            onSuccess = { groupId ->
                Toast.makeText(this, "Grupa \"$groupName\" utworzona!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, GroupChatActivity::class.java).apply {
                    putExtra("groupId", groupId)
                    putExtra("groupName", groupName)
                }
                startActivity(intent)
                finish()
            },
            onError = { error ->
                binding.createGroupButton.isEnabled = true
                binding.createGroupButton.text = "Utwórz"
                Toast.makeText(this, "Błąd: $error", Toast.LENGTH_LONG).show()
            }
        )
    }
}
