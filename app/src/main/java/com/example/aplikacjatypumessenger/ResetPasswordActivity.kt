package com.example.aplikacjatypumessenger

import androidx.appcompat.app.AppCompatActivity
import com.example.aplikacjatypumessenger.databinding.ActivityResetPasswordBinding
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
class ResetPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResetPasswordBinding
    private val auth = Firebase.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleIntent(intent)
        setupClickListeners()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        if (data != null && data.scheme == "yourapp" && data.host == "resetpassword") {
            TODO()
            // Możesz przetworzyć dodatkowe parametry z deep linku
        }
    }

    private fun setupClickListeners() {
        binding.resetPasswordButton.setOnClickListener {
            val newPassword = binding.newPasswordEditText.text.toString()
            val confirmPassword = binding.confirmPasswordEditText.text.toString()

            if (validatePasswords(newPassword, confirmPassword)) {
                resetPassword(newPassword)
            }
        }

        binding.backToLoginTextView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun validatePasswords(newPassword: String, confirmPassword: String): Boolean {
        if (newPassword.length < 6) {
            binding.newPasswordEditText.error = "Hasło musi mieć co najmniej 6 znaków"
            return false
        }

        if (newPassword != confirmPassword) {
            binding.confirmPasswordEditText.error = "Hasła nie są identyczne"
            return false
        }

        return true
    }

    private fun resetPassword(newPassword: String) {
        val user = auth.currentUser
        if (user != null) {
            // Użytkownik jest zalogowany - zmiana hasła
            user.updatePassword(newPassword)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        showSuccess("Hasło zostało zmienione pomyślnie")
                    } else {
                        showError("Błąd podczas zmiany hasła: ${task.exception?.message}")
                    }
                }
        } else {
            // Użytkownik nie jest zalogowany - wymagane ponowne uwierzytelnienie
            showError("Wymagane ponowne logowanie do zmiany hasła")
        }
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}