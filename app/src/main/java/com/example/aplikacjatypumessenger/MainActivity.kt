package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.ChatAdapter
import com.example.aplikacjatypumessenger.adapters.UserAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityMainBinding
import com.example.aplikacjatypumessenger.models.Chat
import com.example.aplikacjatypumessenger.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var userAdapter: UserAdapter

    private val chatList = mutableListOf<Chat>()
    private val userList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupAdapters()
        setupBottomNavigation()
        setupSearch()

        loadCurrentUserProfile()
        loadChatsAndGroups()
        loadUsers()
    }

    private fun setupAdapters() {
        chatAdapter = ChatAdapter(chatList) { chat -> openChat(chat) }
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
                    startActivity(Intent(this, GroupManagementActivity::class.java))
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
                val filtered = if (query.isEmpty()) userList else userList.filter { it.username.lowercase().contains(query) }
                userAdapter.submitList(filtered)
                return true
            }
        })
    }

    private fun loadCurrentUserProfile() {
        auth.currentUser?.let {
            binding.userNameTextView.text = it.email
        }
    }

    private fun loadChatsAndGroups() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                chatList.clear()
                val tempSet = mutableSetOf<String>()

                snapshot?.documents?.forEach { doc ->
                    val chat = doc.toObject(Chat::class.java) ?: return@forEach
                    if (!tempSet.contains(chat.id)) {
                        tempSet.add(chat.id)
                        chatList.add(chat)

                        if (!chat.isGroup) {
                            val otherUserId = chat.participants.first { it != currentUserId }
                            db.collection("users").document(otherUserId).get()
                                .addOnSuccessListener { userDoc ->
                                    val user = userDoc.toObject(User::class.java)
                                    chat.chatName = user?.username ?: "Użytkownik"
                                    chatAdapter.notifyDataSetChanged()
                                }
                        }
                    }
                }
                chatAdapter.notifyDataSetChanged()
            }
    }

    private fun loadUsers() {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("users")
            .whereNotEqualTo("id", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                userList.clear()
                snapshot?.documents?.mapNotNull { it.toObject(User::class.java) }
                    ?.distinctBy { it.id }
                    ?.let { userList.addAll(it) }
                userAdapter.submitList(userList)
            }
    }

    private fun loadProfileData() {
        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { doc ->
                doc.toObject(User::class.java)?.let {
                    binding.profileNameTextView.text = it.username
                    binding.profileEmailTextView.text = it.email
                }
            }
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
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .get()
            .addOnSuccessListener { snapshot ->
                val existingChat = snapshot.documents.mapNotNull { it.toObject(Chat::class.java) }
                    .firstOrNull { it.participants.contains(user.id) && !it.isGroup }

                if (existingChat != null) openChat(existingChat)
                else createPrivateChat(user)
            }
    }

    private fun createPrivateChat(user: User) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = db.collection("chats").document().id
        val chat = Chat(id = chatId, participants = listOf(currentUserId, user.id), isGroup = false)

        db.collection("chats").document(chatId)
            .set(chat)
            .addOnSuccessListener { openChat(chat) }
            .addOnFailureListener { Toast.makeText(this, "Błąd tworzenia czatu", Toast.LENGTH_SHORT).show() }
    }

    private fun logout() {
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
