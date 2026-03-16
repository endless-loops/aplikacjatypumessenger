package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.activity.viewModels
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.aplikacjatypumessenger.databinding.ActivityRegisterBinding
import com.example.aplikacjatypumessenger.viewmodels.LoginViewModel
import com.example.aplikacjatypumessenger.viewmodels.RegisterState // DODAJ TEN IMPORT
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        setupObservers()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.registerState.collect { state ->
                when (state) {
                    is RegisterState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.registerButton.isEnabled = false
                    }
                    is RegisterState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.registerButton.isEnabled = true
                        navigateToMainActivity()
                    }
                    is RegisterState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.registerButton.isEnabled = true
                        showError(state.message)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.registerButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()
            val username = binding.usernameEditText.text.toString().trim()

            if (validateInput(email, password, username)) {
                viewModel.registerUser(email, password, username)
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
    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}