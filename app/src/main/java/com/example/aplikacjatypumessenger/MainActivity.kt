package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.example.aplikacjatypumessenger.viewmodels.ChatListViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChatListViewModel by viewModels()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Dwa osobne adaptery: jeden dla prywatnych czatów, drugi dla grup
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var groupsAdapter: ChatAdapter
    private lateinit var userAdapter: UserAdapter

    // Cache nazw czatów: chatId -> nazwa
    private val chatNames = mutableMapOf<String, String>()

    // Odświeżanie lastSeen co 2 minuty gdy app jest na pierwszym planie
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            auth.currentUser?.uid?.let { uid ->
                db.collection("users").document(uid)
                    .update("lastSeen", System.currentTimeMillis())
            }
            heartbeatHandler.postDelayed(this, 2 * 60 * 1000L)
        }
    }

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

    override fun onResume() {
        super.onResume()
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid)
                .update("status", "online", "lastSeen", System.currentTimeMillis())
        }
        heartbeatHandler.post(heartbeatRunnable)
    }

    override fun onPause() {
        super.onPause()
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        // lastSeen aktualizowane — status offline tylko przy wylogowaniu
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid)
                .update("lastSeen", System.currentTimeMillis())
        }
    }

    private fun setupAdapters() {
        val uid = auth.currentUser?.uid ?: ""
        chatAdapter = ChatAdapter(uid) { chat -> openChat(chat) }
        groupsAdapter = ChatAdapter(uid) { chat -> openChat(chat) }
        userAdapter = UserAdapter { user -> startChatWithUser(user) }

        binding.chatsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        binding.groupsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = groupsAdapter
        }

        binding.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = userAdapter
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.chats.collect { allChats ->
                val privateChats = allChats.filter { !it.isGroup }
                val groupChats = allChats.filter { it.isGroup }

                // Załaduj nazwy dla prywatnych czatów (asynchronicznie, bez pętli notifyDataSetChanged)
                loadChatNames(privateChats) { namedChats ->
                    chatAdapter.submitList(namedChats)
                    binding.emptyChatsText.visibility =
                        if (namedChats.isEmpty()) View.VISIBLE else View.GONE
                }

                // Grupy już mają nazwę (groupName) — submitList od razu
                groupsAdapter.submitList(groupChats)
                binding.emptyGroupsText.visibility =
                    if (groupChats.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.users.collect { users ->
                userAdapter.submitList(users)
                binding.emptyUsersText.visibility =
                    if (users.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * Ładuje nazwy dla listy czatów 1:1 asynchronicznie.
     * Używa cache żeby nie robić zbędnych zapytań do Firestore.
     * Po załadowaniu wszystkich nazw wywołuje callback raz — bez pętli notifyDataSetChanged.
     */
    private fun loadChatNames(chats: List<Chat>, onDone: (List<Chat>) -> Unit) {
        if (chats.isEmpty()) {
            onDone(emptyList())
            return
        }

        val currentUserId = auth.currentUser?.uid ?: return
        val result = chats.map { it.copy() }.toMutableList()
        var pending = 0

        chats.forEachIndexed { index, chat ->
            val cached = chatNames[chat.id]
            if (cached != null) {
                result[index] = chat.apply { chatName = cached }
            } else {
                pending++
                val otherUserId = chat.participants.firstOrNull { it != currentUserId } ?: ""
                if (otherUserId.isEmpty()) {
                    result[index] = chat.apply { chatName = "Czat" }
                    pending--
                    if (pending == 0) onDone(result)
                    return@forEachIndexed
                }
                db.collection("users").document(otherUserId).get()
                    .addOnSuccessListener { doc ->
                        val name = doc.getString("username") ?: "Użytkownik"
                        chatNames[chat.id] = name
                        result[index] = chat.apply { chatName = name }
                        pending--
                        if (pending == 0) onDone(result)
                    }
                    .addOnFailureListener {
                        result[index] = chat.apply { chatName = "Użytkownik" }
                        pending--
                        if (pending == 0) onDone(result)
                    }
            }
        }

        if (pending == 0) onDone(result)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats -> showTab(Tab.CHATS)
                R.id.nav_users -> showTab(Tab.USERS)
                R.id.nav_profile -> {
                    loadProfileData()
                    showTab(Tab.PROFILE)
                }
                R.id.navigation_groups -> showTab(Tab.GROUPS)
                else -> false
            }
        }

        binding.logoutButton.setOnClickListener { logout() }

        binding.createGroupFab.setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
        }
    }

    private enum class Tab { CHATS, USERS, PROFILE, GROUPS }

    private fun showTab(tab: Tab): Boolean {
        binding.chatsLayout.visibility = if (tab == Tab.CHATS) View.VISIBLE else View.GONE
        binding.usersLayout.visibility = if (tab == Tab.USERS) View.VISIBLE else View.GONE
        binding.profileLayout.visibility = if (tab == Tab.PROFILE) View.VISIBLE else View.GONE
        binding.groupsLayout.visibility = if (tab == Tab.GROUPS) View.VISIBLE else View.GONE
        return true
    }

    private fun setupSearch() {
        binding.userSearchView.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText?.trim()?.lowercase() ?: ""
                val filtered = viewModel.getFilteredUsers(query)
                userAdapter.submitList(filtered)
                binding.emptyUsersText.visibility =
                    if (filtered.isEmpty()) View.VISIBLE else View.GONE
                return true
            }
        })
    }

    private fun loadCurrentUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val username = doc.getString("username") ?: ""
                val email = doc.getString("email") ?: auth.currentUser?.email ?: ""
                binding.profileNameTextView.text = username
                binding.profileEmailTextView.text = email
                binding.userNameTextView.text = username
            }
    }

    private fun loadProfileData() {
        loadCurrentUserProfile()
    }

    private fun openChat(chat: Chat) {
        val intent = if (chat.isGroup) {
            Intent(this, GroupChatActivity::class.java).apply {
                putExtra("chatId", chat.id)
                putExtra("groupName", chat.groupName)
            }
        } else {
            val otherUserId = chat.participants.find { it != auth.currentUser?.uid } ?: ""
            Intent(this, ChatActivity::class.java).apply {
                putExtra("chatId", chat.id)
                putExtra("otherUserId", otherUserId)
            }
        }
        startActivity(intent)
    }

    private fun startChatWithUser(user: User) {
        val existingChat = viewModel.chats.value.firstOrNull {
            it.participants.contains(user.id) && !it.isGroup
        }

        if (existingChat != null) {
            openChat(existingChat)
        } else {
            viewModel.createPrivateChat(user.id,
                onSuccess = { chatId ->
                    val chat = Chat(
                        id = chatId,
                        participants = listOf(auth.currentUser!!.uid, user.id),
                        isGroup = false
                    ).apply { chatName = user.username }
                    openChat(chat)
                },
                onError = { error ->
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun logout() {
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid)
                .update("status", "offline", "lastSeen", System.currentTimeMillis())
        }
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
