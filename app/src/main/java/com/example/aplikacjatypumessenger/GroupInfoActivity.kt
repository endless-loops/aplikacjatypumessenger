package com.example.aplikacjatypumessenger

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikacjatypumessenger.adapters.GroupMemberAdapter
import com.example.aplikacjatypumessenger.databinding.ActivityGroupInfoBinding
import com.example.aplikacjatypumessenger.viewmodels.GroupViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class GroupInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupInfoBinding
    private val viewModel: GroupViewModel by viewModels()
    private val auth = Firebase.auth

    private var groupId = ""
    private var groupName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        groupId = intent.getStringExtra("groupId") ?: ""
        groupName = intent.getStringExtra("groupName") ?: ""

        if (groupId.isEmpty()) {
            finish()
            return
        }

        binding.tvGroupName.text = groupName.ifEmpty { "Grupa" }
        loadGroupDetails()
        setupLeaveButton()
    }

    private fun loadGroupDetails() {
        viewModel.getGroupDetails(groupId) { group ->
            if (group == null) return@getGroupDetails

            binding.tvGroupName.text = group.name
            val memberCount = group.participantIds.size
            binding.tvMemberCount.text = "$memberCount ${pluralMembers(memberCount)}"

            val currentUserId = auth.currentUser?.uid ?: ""
            val isAdmin = group.adminId == currentUserId

            // Pokaż/ukryj przycisk opuszczenia dla admina
            binding.btnLeaveGroup.isEnabled = !isAdmin
            if (isAdmin) {
                binding.btnLeaveGroup.text = "Jesteś administratorem grupy"
                binding.btnLeaveGroup.alpha = 0.5f
            }

            viewModel.getGroupMembers(group.participantIds) { members ->
                val adapter = GroupMemberAdapter(
                    members = members,
                    isAdmin = isAdmin,
                    currentUserId = currentUserId,
                    onAction = { user, action ->
                        when (action) {
                            "remove" -> confirmRemoveMember(user.username) {
                                viewModel.removeMember(groupId, user.id) { success, error ->
                                    if (success) {
                                        Toast.makeText(this, "${user.username} usunięty", Toast.LENGTH_SHORT).show()
                                        loadGroupDetails()
                                    } else {
                                        Toast.makeText(this, error ?: "Błąd", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            "make_admin" -> Toast.makeText(
                                this,
                                "Funkcja przekazania roli admina jeszcze niedostępna",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                binding.rvMembers.layoutManager = LinearLayoutManager(this)
                binding.rvMembers.adapter = adapter
            }
        }
    }

    private fun setupLeaveButton() {
        binding.btnLeaveGroup.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Opuść grupę")
                .setMessage("Czy na pewno chcesz opuścić grupę \"$groupName\"?")
                .setPositiveButton("Opuść") { _, _ ->
                    viewModel.leaveGroup(groupId) { success, error ->
                        if (success) {
                            Toast.makeText(this, "Opuściłeś grupę", Toast.LENGTH_SHORT).show()
                            // Wróć do MainActivity (wyczyść back stack grup)
                            finishAffinity()
                            startActivity(android.content.Intent(this, MainActivity::class.java))
                        } else {
                            Toast.makeText(this, error ?: "Błąd", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }
    }

    private fun confirmRemoveMember(username: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Usuń z grupy")
            .setMessage("Czy na pewno chcesz usunąć $username z grupy?")
            .setPositiveButton("Usuń") { _, _ -> onConfirm() }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun pluralMembers(count: Int): String = when {
        count == 1 -> "członek"
        count in 2..4 -> "członków"
        else -> "członków"
    }
}
