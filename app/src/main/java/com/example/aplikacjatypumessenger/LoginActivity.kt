package com.example.aplikacjatypumessenger

import androidx.activity.viewModels
import com.example.aplikacjatypumessenger.viewmodels.LoginViewModel
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.aplikacjatypumessenger.databinding.ActivityLoginBinding
import com.example.aplikacjatypumessenger.viewmodels.LoginState
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()
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
        binding.progressBar.visibility = View.VISIBLE
        binding.loginButton.isEnabled = false

        // Użyj lifecycleScope - to działa w Activity
        lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                when (state) {
                    is LoginState.Loading -> {
                        // Loading już ustawiony
                    }
                    is LoginState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.loginButton.isEnabled = true
                        navigateToMainActivity()
                    }
                    is LoginState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.loginButton.isEnabled = true
                        showError(state.message)
                    }
                    else -> {}
                }
            }
        }

        viewModel.loginUser(email, password)
    }


    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
