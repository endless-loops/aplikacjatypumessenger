package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.aplikacjatypumessenger.databinding.ActivityRegisterBinding
import com.example.aplikacjatypumessenger.utils.applySystemBarsPadding
import com.example.aplikacjatypumessenger.utils.collectWhenStarted
import com.example.aplikacjatypumessenger.utils.showToast
import com.example.aplikacjatypumessenger.viewmodels.RegisterEvent
import com.example.aplikacjatypumessenger.viewmodels.RegisterViewModel

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by viewModels { RegisterViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.registerButton.setOnClickListener {
            viewModel.register(
                email = binding.emailEditText.text.toString(),
                password = binding.passwordEditText.text.toString(),
                username = binding.usernameEditText.text.toString()
            )
        }
        binding.loginTextView.setOnClickListener { finish() }

        collectWhenStarted(viewModel.uiState) { state ->
            binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.registerButton.isEnabled = !state.isLoading
            binding.usernameEditText.error = state.usernameError?.asString(this)
            binding.emailEditText.error = state.emailError?.asString(this)
            binding.passwordEditText.error = state.passwordError?.asString(this)
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                RegisterEvent.NavigateToMain -> {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
                is RegisterEvent.ShowMessage -> showToast(event.text, long = true)
            }
        }
    }
}
