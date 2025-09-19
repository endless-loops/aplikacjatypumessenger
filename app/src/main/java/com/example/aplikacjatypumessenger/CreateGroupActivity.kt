package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.UserAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityCreateGroupBinding
import com.example.aplikacjatypumessenger.models.Chat
import com.example.aplikacjatypumessenger.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class CreateGroupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateGroupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userAdapter: UserAdapter
    private var userList = mutableListOf<User>()
    private val selectedUsers = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        db = Firebase.firestore

        setupViews()
        loadUsers()
        setupClickListeners()
    }

    private fun setupViews() {
        userAdapter = UserAdapter(userList) { user ->
            toggleUserSelection(user)
        }

        binding.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CreateGroupActivity)
            adapter = userAdapter
        }
    }

    private fun toggleUserSelection(user: User) {
        if (selectedUsers.contains(user.id)) {
            selectedUsers.remove(user.id)
        } else {
            selectedUsers.add(user.id)
        }
        updateSelectedCount()
    }

    private fun updateSelectedCount() {
        binding.selectedCountText.text = "Wybrano: ${selectedUsers.size} użytkowników"
    }

    private fun loadUsers() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("users")
            .whereNotEqualTo("id", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                userList.clear()
                snapshot?.documents?.forEach { document ->
                    val user = document.toObject(User::class.java)
                    user?.let { userList.add(it) }
                }
                userAdapter.notifyDataSetChanged()
            }
    }

    private fun setupClickListeners() {
        binding.createGroupButton.setOnClickListener {
            val groupName = binding.groupNameEditText.text?.toString()?.trim()
            if (groupName.isNullOrEmpty()) {
                Toast.makeText(this, "Wpisz nazwę grupy", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedUsers.size < 2) {
                Toast.makeText(this, "Wybierz przynajmniej 2 osoby", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            createGroupChat(groupName)
        }

        binding.cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun createGroupChat(groupName: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = db.collection("chats").document().id

        val participants = selectedUsers.toMutableList().apply {
            add(currentUserId)  // Dodaj twórcę grupy
        }

        val chat = Chat(
            id = chatId,
            participants = participants,
            isGroup = true,
            groupName = groupName,
            groupAdmin = currentUserId,
            createdAt = System.currentTimeMillis()
        )

        db.collection("chats").document(chatId)
            .set(chat)
            .addOnSuccessListener {
                Toast.makeText(this, "Grupa utworzona!", Toast.LENGTH_SHORT).show()
                openGroupChat(chatId)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Błąd tworzenia grupy", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openGroupChat(chatId: String) {
        val intent = Intent(this, GroupChatActivity::class.java).apply {
            putExtra("chatId", chatId)
            putExtra("isGroup", true)
        }
        startActivity(intent)
        finish()
    }
}