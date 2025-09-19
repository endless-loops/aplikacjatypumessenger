package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aplikacjatypumessenger.databinding.ActivityRegisterBinding
import com.example.aplikacjatypumessenger.models.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        db = Firebase.firestore

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.registerButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()
            val username = binding.usernameEditText.text.toString().trim()

            if (validateInput(email, password, username)) {
                registerUser(email, password, username)
            }
        }

        binding.loginTextView.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(email: String, password: String, username: String): Boolean {
        if (username.length < 3) {
            binding.usernameEditText.error = "Nazwa użytkownika musi mieć co najmniej 3 znaki"
            return false
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailEditText.error = "Wprowadź poprawny email"
            return false
        }

        if (password.length < 6) {
            binding.passwordEditText.error = "Hasło musi mieć co najmniej 6 znaków"
            return false
        }

        return true
    }

    private fun registerUser(email: String, password: String, username: String) {
        binding.progressBar.visibility = View.VISIBLE

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let { saveUserToFirestore(it.uid, email, username) }
                } else {
                    binding.progressBar.visibility = View.GONE
                    showError("Rejestracja nieudana: ${task.exception?.message}")
                }
            }
    }

    private fun saveUserToFirestore(uid: String, email: String, username: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { tokenTask ->
            val fcmToken = if (tokenTask.isSuccessful) tokenTask.result else ""

            val user = User(
                id = uid,
                email = email,
                username = username,
                profileImage = "",
                status = "online",
                lastSeen = System.currentTimeMillis(),
                fcmToken = fcmToken
            )

            db.collection("users").document(uid)
                .set(user)
                .addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    navigateToMainActivity()
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    showError("Błąd zapisu użytkownika: ${e.message}")
                    auth.currentUser?.delete()
                }
        }
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
