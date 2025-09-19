package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.ChatAdapter
import com.example.aplikacjatypumessenger.adapters.UserAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityMainBinding
import com.example.aplikacjatypumessenger.models.Chat
import com.example.aplikacjatypumessenger.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var userAdapter: UserAdapter
    private var chatList = mutableListOf<Chat>()
    private var userList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        db = Firebase.firestore

        setupPushNotifications() // powiadomienia push

        setupViews()
        setupNavigation()
        setupSearch()
        updateUserStatus("online")
        loadUserData()
        loadChats()
        loadUsers()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateUserStatus("offline")
    }

    private fun setupViews() {
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

    private fun setupNavigation() {
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
                val filtered = if (query.isEmpty()) {
                    userList
                } else {
                    userList.filter { it.username.lowercase().contains(query) }
                }
                userAdapter.submitList(filtered)
                return true
            }
        })
    }

    private fun loadUserData() {
        auth.currentUser?.let {
            binding.userNameTextView.text = it.email
        }
    }

    private fun loadChats() {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                chatList.clear()

                snapshot?.documents?.forEach { doc ->
                    val chat = doc.toObject(Chat::class.java)
                    if (chat != null) {
                        // Pobieramy ID drugiego użytkownika
                        val otherUserId = chat.participants.firstOrNull { it != currentUserId }
                        if (otherUserId != null) {
                            // Pobieramy dane drugiego użytkownika
                            db.collection("users").document(otherUserId)
                                .get()
                                .addOnSuccessListener { userDoc ->
                                    val user = userDoc.toObject(User::class.java)
                                    if (user != null) {
                                        chat.chatName =
                                            user.username // używamy dodatkowego pola w Chat.kt
                                        chatList.add(chat)
                                        chatAdapter.notifyDataSetChanged()
                                    }
                                }
                                .addOnFailureListener {
                                    // Jeśli nie uda się pobrać usera, dodajemy chat bez nazwy
                                    chatList.add(chat)
                                    chatAdapter.notifyDataSetChanged()
                                }
                        } else {
                            // Jeśli nie znaleziono drugiego użytkownika, dodajemy chat bez nazwy
                            chatList.add(chat)
                            chatAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
    }

    private fun loadUsers() {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("users")
            .whereNotEqualTo("id", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                userList.clear()
                val users = snapshot?.documents?.mapNotNull { it.toObject(User::class.java) }
                    ?: emptyList()
                userList.addAll(users)
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
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chatId", chat.id)
            putExtra("otherUserId", chat.participants.find { it != auth.currentUser?.uid })
        }
        startActivity(intent)
    }

    private fun startChatWithUser(user: User) {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .get()
            .addOnSuccessListener { snapshot ->
                val existingChat =
                    snapshot.documents.mapNotNull { it.toObject(Chat::class.java) }
                        .firstOrNull { it.participants.contains(user.id) }
                if (existingChat != null) openChat(existingChat)
                else createNewChat(user)
            }
    }

    private fun createNewChat(user: User) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = db.collection("chats").document().id
        val chat = Chat(id = chatId, participants = listOf(currentUserId, user.id))

        db.collection("chats").document(chatId)
            .set(chat)
            .addOnSuccessListener { openChat(chat) }
            .addOnFailureListener { showError("Błąd tworzenia czatu") }
    }

    private fun logout() {
        updateUserStatus("offline")
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun updateUserStatus(status: String) {
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid)
                .update("status", status)
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setupPushNotifications() {
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101
                )
            }
        }

        // Get FCM token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener

            val token = task.result
            saveTokenToFirestore(token)
        }
    }

    private fun saveTokenToFirestore(token: String) {
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid)
                .update("fcmToken", token)
                .addOnFailureListener {
                    showError("Failed to save notification token")
                }
        }
    }
}