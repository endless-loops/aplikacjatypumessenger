package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.GroupMemberAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityGroupManagementBinding
import com.example.aplikacjatypumessenger.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class GroupManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGroupManagementBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var memberAdapter: GroupMemberAdapter
    private var memberList = mutableListOf<User>()
    private var chatId: String = ""
    private var isAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        db = Firebase.firestore
        chatId = intent.getStringExtra("chatId") ?: ""

        setupViews()
        loadGroupMembers()
        checkAdminStatus()
        setupClickListeners()
    }

    private fun setupViews() {
        memberAdapter = GroupMemberAdapter(memberList, isAdmin) { user, action ->
            when (action) {
                "remove" -> removeMember(user.id)
                "make_admin" -> makeAdmin(user.id)
            }
        }

        binding.membersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupManagementActivity)
            adapter = memberAdapter
        }
    }

    private fun checkAdminStatus() {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("chats").document(chatId)
            .get()
            .addOnSuccessListener { document ->
                val adminId = document.getString("groupAdmin")
                isAdmin = adminId == currentUserId
                memberAdapter.isAdmin = isAdmin
                memberAdapter.notifyDataSetChanged()

                if (isAdmin) {
                    binding.addMembersButton.visibility = View.VISIBLE
                }
            }
    }

    private fun loadGroupMembers() {
        db.collection("chats").document(chatId)
            .get()
            .addOnSuccessListener { document ->
                val participants = document.get("participants") as? List<String> ?: emptyList()
                loadUsersDetails(participants)
            }
    }

    private fun loadUsersDetails(userIds: List<String>) {
        memberList.clear()
        userIds.forEach { userId ->
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    val user = document.toObject(User::class.java)
                    user?.let {
                        memberList.add(it)
                        memberAdapter.notifyDataSetChanged()
                    }
                }
        }
    }

    private fun removeMember(userId: String) {
        db.collection("chats").document(chatId)
            .update("participants", FieldValue.arrayRemove(userId))
            .addOnSuccessListener {
                Toast.makeText(this, "Użytkownik usunięty", Toast.LENGTH_SHORT).show()
                loadGroupMembers()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Błąd usuwania", Toast.LENGTH_SHORT).show()
            }
    }

    private fun makeAdmin(userId: String) {
        db.collection("chats").document(chatId)
            .update("groupAdmin", userId)
            .addOnSuccessListener {
                Toast.makeText(this, "Nowy administrator", Toast.LENGTH_SHORT).show()
                checkAdminStatus()
            }
    }

    private fun setupClickListeners() {
        binding.addMembersButton.setOnClickListener {
            val intent = Intent(this, AddMembersActivity::class.java).apply {
                putExtra("chatId", chatId)
            }
            startActivity(intent)
        }

        binding.leaveGroupButton.setOnClickListener {
            leaveGroup()
        }
    }

    private fun leaveGroup() {
        val currentUserId = auth.currentUser?.uid ?: return
        removeMember(currentUserId)
        finish()
    }
}