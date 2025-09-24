package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.UserAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityGroupInfoBinding
import com.example.aplikacjatypumessenger.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class GroupInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupInfoBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var membersAdapter: UserAdapter
    private var memberList = mutableListOf<User>()

    private var groupId: String = ""
    private var groupName: String = ""
    private var isAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = Firebase.firestore

        groupId = intent.getStringExtra("GROUP_ID") ?: ""
        groupName = intent.getStringExtra("GROUP_NAME") ?: "Grupa"

        setupToolbar()
        setupRecyclerView()
        loadGroupInfo()
        setupClickListeners()
        checkAdminStatus()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = groupName
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        membersAdapter = UserAdapter { user ->
            // Obsługa kliknięcia na użytkownika
        }
        binding.rvMembers.apply {
            layoutManager = LinearLayoutManager(this@GroupInfoActivity)
            adapter = membersAdapter
        }
    }

    private fun loadGroupInfo() {
        if (groupId.isEmpty()) return

        db.collection("chats").document(groupId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val participants = document.get("participants") as? List<String> ?: emptyList()
                    binding.tvMemberCount.text = "${participants.size} członków"
                    fetchUsers(participants)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Błąd wczytywania informacji o grupie", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkAdminStatus() {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("chats").document(groupId)
            .get()
            .addOnSuccessListener { document ->
                val adminId = document.getString("groupAdmin") ?: ""
                isAdmin = adminId == currentUserId
                binding.btnAddMembers.visibility = if (isAdmin) android.view.View.VISIBLE else android.view.View.GONE
            }
    }

    private fun fetchUsers(userIds: List<String>) {
        val tempList = mutableListOf<User>()
        userIds.forEach { uid ->
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    doc.toObject(User::class.java)?.let {
                        tempList.add(it)
                        if (tempList.size == userIds.size) {
                            memberList.clear()
                            memberList.addAll(tempList)
                            membersAdapter.submitList(memberList.toList())
                        }
                    }
                }
        }
    }

    private fun setupClickListeners() {
        binding.btnAddMembers.setOnClickListener {
            val intent = Intent(this, AddMembersActivity::class.java).apply {
                putExtra("chatId", groupId)
                putExtra("GROUP_NAME", groupName)
            }
            startActivity(intent)
        }

        binding.btnLeaveGroup.setOnClickListener {
            leaveGroup()
        }
    }

    private fun leaveGroup() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("chats").document(groupId)
            .update("participants", FieldValue.arrayRemove(currentUserId))
            .addOnSuccessListener {
                Toast.makeText(this, "Opuszczono grupę", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Błąd opuszczania grupy", Toast.LENGTH_SHORT).show()
            }
    }
}