package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.ChatAdapter
import com.example.aplikacjatypumessenger.adapters.UserAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityMainBinding
import com.example.aplikacjatypumessenger.models.Chat
import com.example.aplikacjatypumessenger.models.User
import com.example.aplikacjatypumessenger.viewmodels.ChatListViewModel  // ← DODAJ TEN IMPORT
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChatListViewModel by viewModels()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()  // ← DODAJ AUTH

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var userAdapter: UserAdapter

    // USUŃ stare listy - teraz dane są w ViewModel
    // private val chatList = mutableListOf<Chat>()  ← USUŃ
    // private val userList = mutableListOf<User>()  ← USUŃ

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAdapters()
        setupObservers()
        setupBottomNavigation()
        setupSearch()
        loadCurrentUserProfile()
    }

    private fun setupObservers() {
        // Obserwuj czaty
        lifecycleScope.launch {
            viewModel.chats.collect { chats ->
                // Dla ChatAdapter (zwykły Adapter) - użyj własnej metody
                chatAdapter.updateList(chats) // ← Musimy dodać tę metodę w ChatAdapter

                // Dla każdego czatu 1:1 załaduj nazwę
                chats.forEach { chat ->
                    if (!chat.isGroup) {
                        viewModel.getChatName(chat) { name ->
                            chat.chatName = name
                            chatAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }

        // Obserwuj użytkowników - UserAdapter JEST ListAdapter
        lifecycleScope.launch {
            viewModel.users.collect { users ->
                userAdapter.submitList(users) // ← To działa bo UserAdapter jest ListAdapter
            }
        }
    }


    private fun setupAdapters() {
        //  duplikat - zostawić tylko jedną funkcję setupAdapters()

        chatAdapter = ChatAdapter(emptyList()) { chat -> openChat(chat) }
        userAdapter = UserAdapter { user -> startChatWithUser(user) }

        binding.chatsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        binding.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = userAdapter
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats -> {
                    binding.chatsLayout.visibility = View.VISIBLE
                    binding.usersLayout.visibility = View.GONE
                    binding.profileLayout.visibility = View.GONE
                    true
                }
                R.id.nav_users -> {
                    binding.chatsLayout.visibility = View.GONE
                    binding.usersLayout.visibility = View.VISIBLE
                    binding.profileLayout.visibility = View.GONE
                    true
                }
                R.id.nav_profile -> {
                    binding.chatsLayout.visibility = View.GONE
                    binding.usersLayout.visibility = View.GONE
                    binding.profileLayout.visibility = View.VISIBLE
                    loadProfileData()
                    true
                }
                R.id.navigation_groups -> {
                    binding.chatsLayout.visibility = View.GONE
                    binding.usersLayout.visibility = View.GONE
                    binding.profileLayout.visibility = View.GONE
                    startActivity(Intent(this, CreateGroupActivity::class.java))
                    true
                }
                else -> false
            }
        }

        binding.logoutButton.setOnClickListener { logout() }
    }

    private fun setupSearch() {
        binding.userSearchView.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText?.trim()?.lowercase() ?: ""
                val currentUsers = viewModel.users.value
                val filtered = if (query.isEmpty()) currentUsers
                else currentUsers.filter { it.username.lowercase().contains(query) }

                lifecycleScope.launch {
                    userAdapter.submitList(filtered) // ← Tylko dla UserAdapter
                }
                return true
            }
        })
    }

    private fun loadCurrentUserProfile() {
        auth.currentUser?.let {
            binding.userNameTextView.text = it.email
        }
    }

    // USUŃ stare funkcje: loadChatsAndGroups(), loadUsers()

    private fun loadProfileData() {
        val currentUser = auth.currentUser ?: return
        // TODO: Przenieś to do UserRepository
        // Na razie zostawiamy - to też można przenieść do MVVM
        /*
        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { doc ->
                doc.toObject(User::class.java)?.let {
                    binding.profileNameTextView.text = it.username
                    binding.profileEmailTextView.text = it.email
                }
            }
        */
    }

    private fun openChat(chat: Chat) {
        val intent = Intent(
            this,
            if (chat.isGroup) GroupChatActivity::class.java else ChatActivity::class.java
        ).apply {
            putExtra("chatId", chat.id)
            if (chat.isGroup) putExtra("isGroup", true)
            else putExtra("otherUserId", chat.participants.find { it != auth.currentUser?.uid })
        }
        startActivity(intent)
    }

    private fun startChatWithUser(user: User) {
        // Sprawdź czy czat już istnieje używając danych z ViewModel
        val existingChat = viewModel.chats.value.firstOrNull {
            it.participants.contains(user.id) && !it.isGroup
        }

        if (existingChat != null) {
            openChat(existingChat)
        } else {
            // Użyj ViewModel do tworzenia czatu
            viewModel.createPrivateChat(user.id,
                onSuccess = { chatId ->
                    val chat = Chat(
                        id = chatId,
                        participants = listOf(auth.currentUser!!.uid, user.id),
                        isGroup = false
                    )
                    openChat(chat)
                },
                onError = { error ->
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    // USUŃ starą funkcję createPrivateChat()

    private fun logout() {
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}