package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.aplikacjatypumessenger.databinding.ActivityLoginBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()

            if (validateInput(email, password)) {
                loginUser(email, password)
            }
        }

        binding.registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.forgotPasswordTextView.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun showForgotPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val emailEditText = dialogView.findViewById<TextInputEditText>(R.id.emailEditText)

        AlertDialog.Builder(this)
            .setTitle("Resetowanie hasła")
            .setView(dialogView)
            .setPositiveButton("Wyślij") { dialog, _ ->
                val email = emailEditText.text.toString().trim()
                if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    sendPasswordResetEmail(email)
                } else {
                    Toast.makeText(this, "Wprowadź poprawny adres email", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Anuluj") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Email resetujący hasło został wysłany na: $email",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Błąd: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun validateInput(email: String, password: String): Boolean {
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

    private fun loginUser(email: String, password: String) {
        // Pokaż progress bar
        binding.progressBar.visibility = View.VISIBLE
        binding.loginButton.isEnabled = false

        // Utwórz task logowania
        val loginTask = auth.signInWithEmailAndPassword(email, password)

        // Ustaw timeout na 10 sekund
        val handler = android.os.Handler(mainLooper)
        val timeoutRunnable = Runnable {
            if (binding.progressBar.visibility == View.VISIBLE) {
                binding.progressBar.visibility = View.GONE
                binding.loginButton.isEnabled = true
                showError("Logowanie zajmuje zbyt długo. Sprawdź połączenie z internetem.")
            }
        }
        handler.postDelayed(timeoutRunnable, 10_000)

        loginTask
            .addOnCompleteListener { task ->
                // Usuń timeout
                handler.removeCallbacks(timeoutRunnable)
                binding.progressBar.visibility = View.GONE
                binding.loginButton.isEnabled = true

                if (task.isSuccessful) {
                    navigateToMainActivity()
                } else {
                    showError("Logowanie nieudane: ${task.exception?.message ?: "Nieznany błąd"}")
                }
            }
            .addOnFailureListener { exception ->
                // Usuń timeout
                handler.removeCallbacks(timeoutRunnable)
                binding.progressBar.visibility = View.GONE
                binding.loginButton.isEnabled = true

                showError("Błąd logowania: ${exception.message}")
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
