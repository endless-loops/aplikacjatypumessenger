package com.example.aplikacjatypumessenger

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.adapters.UserAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityGroupInfoBinding
import com.example.aplikacjatypumessenger.models.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore

class GroupInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupInfoBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var membersAdapter: UserAdapter
    private var memberList = mutableListOf<User>()

    private var groupId: String = ""
    private var groupName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = Firebase.firestore

        // Pobranie danych z intentu
        groupId = intent.getStringExtra("GROUP_ID") ?: ""
        groupName = intent.getStringExtra("GROUP_NAME") ?: "Grupa"

        setupToolbar()
        setupRecyclerView()
        loadGroupMembers()
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
        membersAdapter = UserAdapter { /* kliknięcie na użytkownika, np. profil */ }
        binding.rvMembers.apply {
            layoutManager = LinearLayoutManager(this@GroupInfoActivity)
            adapter = membersAdapter
        }
    }

    private fun loadGroupMembers() {
        if (groupId.isEmpty()) return

        db.collection("chats").document(groupId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val participants = document.get("participants") as? List<String> ?: emptyList()
                    fetchUsers(participants)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Błąd wczytywania członków grupy", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchUsers(userIds: List<String>) {
        memberList.clear()
        userIds.forEach { uid ->
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    doc.toObject(User::class.java)?.let {
                        memberList.add(it)
                        membersAdapter.submitList(memberList.toList()) // ListAdapter wymaga nowej listy
                    }
                }
        }
    }
}
