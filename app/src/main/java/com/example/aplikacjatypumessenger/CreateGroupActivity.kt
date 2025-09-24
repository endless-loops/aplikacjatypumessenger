package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.UserAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityCreateGroupBinding
import com.example.aplikacjatypumessenger.viewmodels.ChatListViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private val viewModel: ChatListViewModel by viewModels()
    private lateinit var userAdapter: UserAdapter
    private val auth = Firebase.auth
    private val selectedUsers = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        // ✅ UŻYJ ViewModel zamiast bezpośrednio Firestore
        lifecycleScope.launch {
            viewModel.users.collect { users ->
                userAdapter.submitList(users)
            }
        }
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter { user ->
            toggleUserSelection(user.id)
        }

        binding.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CreateGroupActivity)
            adapter = userAdapter
        }

        // ✅ DODAJ WYSZUKIWANIE jak w MainActivity
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText?.trim()?.lowercase() ?: ""
                val filteredUsers = viewModel.getFilteredUsers(query)
                userAdapter.submitList(filteredUsers)
                return true
            }
        })
    }

    private fun toggleUserSelection(userId: String) {
        if (selectedUsers.contains(userId)) {
            selectedUsers.remove(userId)
        } else {
            selectedUsers.add(userId)
        }
        updateSelectedCount()
    }

    private fun setupClickListeners() {
        // ✅ DOSTOSUJ DO LAYOUTU Z TOOLBAR
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.createGroupButton.setOnClickListener {
            createGroup()
        }
    }

    private fun createGroup() {
        val groupName = binding.groupNameEditText.text.toString().trim()

        if (groupName.isEmpty()) {
            showError("Wprowadź nazwę grupy")
            return
        }

        if (selectedUsers.size < 2) {
            showError("Wybierz przynajmniej 2 użytkowników")
            return
        }

        // ✅ UŻYJ ViewModel zamiast bezpośrednio Firestore
        viewModel.createGroup(
            name = groupName,
            participantIds = selectedUsers.toList(),
            onSuccess = { groupId ->
                Toast.makeText(this, "Grupa utworzona pomyślnie!", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, GroupChatActivity::class.java).apply {
                    putExtra("groupId", groupId)
                    putExtra("groupName", groupName)
                }
                startActivity(intent)
                finish()
            },
            onError = { error ->
                showError("Błąd tworzenia grupy: $error")
            }
        )
    }

    private fun updateSelectedCount() {
        binding.selectedCountText.text = "Wybrano: ${selectedUsers.size} użytkowników"
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}