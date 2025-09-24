package com.example.aplikacjatypumessenger

import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.aplikacjatypumessenger.databinding.ActivityResetPasswordBinding
import com.example.aplikacjatypumessenger.viewmodels.LoginViewModel
import com.example.aplikacjatypumessenger.viewmodels.ResetPasswordState // DODAJ TEN IMPORT
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleIntent(intent)
        setupClickListeners()
        setupObservers() // ← DODAJ OBSERWATORÓW
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.resetPasswordState.collect { state ->
                when (state) {
                    is ResetPasswordState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.resetPasswordButton.isEnabled = false
                    }
                    is ResetPasswordState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resetPasswordButton.isEnabled = true
                        showSuccess("Hasło zostało zmienione pomyślnie")
                    }
                    is ResetPasswordState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resetPasswordButton.isEnabled = true
                        showError(state.message)
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        if (data != null && data.scheme == "yourapp" && data.host == "resetpassword") {
            TODO()
        }
    }

    private fun setupClickListeners() {
        binding.resetPasswordButton.setOnClickListener {
            val newPassword = binding.newPasswordEditText.text.toString()
            val confirmPassword = binding.confirmPasswordEditText.text.toString()

            if (validatePasswords(newPassword, confirmPassword)) {
                viewModel.resetPassword(newPassword) // ← WYWOŁAJ PRZEZ VIEWMODEL
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

    // USUŃ STARĄ FUNKCJĘ resetPassword()

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}