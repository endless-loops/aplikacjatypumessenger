package com.example.aplikacjatypumessenger

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.adapters.AddMembersAdapter
import com.example.aplikacjatypumessenger.models.User
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AddMembersActivity : AppCompatActivity() {

    private lateinit var groupId: String
    private lateinit var adapter: AddMembersAdapter
    private val selectedMembers = mutableSetOf<String>()
    private val allUsers = mutableListOf<User>()

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_members)

        auth = Firebase.auth
        db = Firebase.firestore

        groupId = intent.getStringExtra("chatId") ?: ""
        val groupName = intent.getStringExtra("GROUP_NAME") ?: "Grupa"

        setupToolbar(groupName)
        setupViews()
        loadAvailableUsers()
    }

    private fun setupToolbar(groupName: String) {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Dodaj członków do $groupName"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    private fun setupViews() {
        val searchView = findViewById<SearchView>(R.id.search_view)
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        val btnAddMembers = findViewById<Button>(R.id.btn_add_members)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AddMembersAdapter(allUsers) { user, isSelected ->
            if (isSelected) selectedMembers.add(user.id) else selectedMembers.remove(user.id)
            updateAddButtonState()
        }
        recyclerView.adapter = adapter

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterUsers(newText.orEmpty())
                return true
            }
        })

        btnAddMembers.setOnClickListener { addSelectedMembersToGroup() }
        updateAddButtonState()
    }

    private fun loadAvailableUsers() {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("users")
            .get()
            .addOnSuccessListener { snapshot ->
                allUsers.clear()
                snapshot.documents.forEach { doc ->
                    val user = doc.toObject(User::class.java)
                    if (user != null && user.id != currentUserId) {
                        allUsers.add(user)
                    }
                }
                adapter.updateData(allUsers)
            }
    }

    private fun filterUsers(query: String) {
        val filtered = if (query.isEmpty()) allUsers else
            allUsers.filter { it.username.contains(query, ignoreCase = true) || it.status.contains(query, ignoreCase = true) }
        adapter.updateData(filtered)
    }

    private fun updateAddButtonState() {
        val btnAddMembers = findViewById<Button>(R.id.btn_add_members)
        btnAddMembers.isEnabled = selectedMembers.isNotEmpty()
        btnAddMembers.text = if (selectedMembers.isNotEmpty()) "Dodaj (${selectedMembers.size})" else "Dodaj członków"
    }

    private fun showProgressDialog() {
        if (progressDialog?.isShowing == true) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_progress, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)
        val messageText = dialogView.findViewById<TextView>(R.id.message_text)

        messageText.text = "Dodawanie członków..."

        progressDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        progressDialog?.show()
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun addSelectedMembersToGroup() {
        if (selectedMembers.isEmpty()) return

        showProgressDialog()

        val chatRef = db.collection("chats").document(groupId)
        chatRef.update("participants", com.google.firebase.firestore.FieldValue.arrayUnion(*selectedMembers.toTypedArray()))
            .addOnSuccessListener {
                hideProgressDialog()
                Toast.makeText(this, "${selectedMembers.size} członków dodano!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener {
                hideProgressDialog()
                Toast.makeText(this, "Błąd dodawania członków", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideProgressDialog()
    }
}