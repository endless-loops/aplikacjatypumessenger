package com.example.aplikacjatypumessenger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.ChatAdapter
import com.example.aplikacjatypumessenger.adapters.UserAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityMainBinding
import com.example.aplikacjatypumessenger.utils.Extras
import com.example.aplikacjatypumessenger.utils.collectWhenStarted
import com.example.aplikacjatypumessenger.utils.showToast
import com.example.aplikacjatypumessenger.viewmodels.MainEvent
import com.example.aplikacjatypumessenger.viewmodels.MainTab
import com.example.aplikacjatypumessenger.viewmodels.MainUiState
import com.example.aplikacjatypumessenger.viewmodels.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    private val chatAdapter = ChatAdapter { chat -> openChat(chat.chatId, chat.isGroup, chat.otherUserId) }
    private val userAdapter = UserAdapter { user -> viewModel.onUserClicked(user) }

    // Na Androidzie 13+ powiadomienia wymagają zgody użytkownika
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.chatsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatsRecyclerView.adapter = chatAdapter
        binding.usersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.usersRecyclerView.adapter = userAdapter

        setupListeners()
        observeViewModel()
        if (savedInstanceState == null) requestNotificationPermission()
    }

    private fun setupListeners() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val tab = TAB_BY_MENU_ID.entries.firstOrNull { it.value == item.itemId }?.key
                ?: return@setOnItemSelectedListener false
            viewModel.selectTab(tab)
            true
        }
        binding.userSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setQuery(newText.orEmpty())
                return true
            }
        })
        binding.createGroupButton.setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
        }
        binding.logoutButton.setOnClickListener { viewModel.logout() }
    }

    private fun observeViewModel() {
        collectWhenStarted(viewModel.uiState) { render(it) }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is MainEvent.OpenChat -> openChat(event.chatId, event.isGroup, event.otherUserId)
                is MainEvent.ShowMessage -> showToast(event.text)
                MainEvent.NavigateToLogin -> {
                    startActivity(
                        Intent(this, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
            }
        }
    }

    private fun render(state: MainUiState) {
        val tab = state.selectedTab
        val menuId = TAB_BY_MENU_ID.getValue(tab)
        if (binding.bottomNavigation.selectedItemId != menuId) binding.bottomNavigation.selectedItemId = menuId

        val showChats = tab == MainTab.CHATS || tab == MainTab.GROUPS
        binding.chatsLayout.visibility = if (showChats) View.VISIBLE else View.GONE
        binding.usersLayout.visibility = if (tab == MainTab.USERS) View.VISIBLE else View.GONE
        binding.profileLayout.visibility = if (tab == MainTab.PROFILE) View.VISIBLE else View.GONE

        binding.chatsHeaderTextView.setText(if (tab == MainTab.GROUPS) R.string.header_groups else R.string.header_chats)
        binding.createGroupButton.visibility = if (tab == MainTab.GROUPS) View.VISIBLE else View.GONE
        binding.emptyChatsTextView.visibility = if (showChats && state.chats.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyChatsTextView.setText(if (tab == MainTab.GROUPS) R.string.empty_groups else R.string.empty_chats)

        chatAdapter.submitList(state.chats)
        userAdapter.submitList(state.users)

        binding.profileNameTextView.text = state.profile?.username.orEmpty()
        binding.profileEmailTextView.text = state.profile?.email ?: state.email
    }

    private fun openChat(chatId: String, isGroup: Boolean, otherUserId: String?) {
        val intent = if (isGroup) {
            Intent(this, GroupChatActivity::class.java)
        } else {
            Intent(this, ChatActivity::class.java).putExtra(Extras.OTHER_USER_ID, otherUserId)
        }
        startActivity(intent.putExtra(Extras.CHAT_ID, chatId))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        val TAB_BY_MENU_ID = mapOf(
            MainTab.CHATS to R.id.nav_chats,
            MainTab.GROUPS to R.id.navigation_groups,
            MainTab.USERS to R.id.nav_users,
            MainTab.PROFILE to R.id.nav_profile
        )
    }
}
