package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.aplikacjatypumessenger.databinding.ActivityResetPasswordBinding
import com.example.aplikacjatypumessenger.utils.applySystemBarsPadding
import com.example.aplikacjatypumessenger.utils.collectWhenStarted
import com.example.aplikacjatypumessenger.utils.showToast
import com.example.aplikacjatypumessenger.viewmodels.ResetPasswordEvent
import com.example.aplikacjatypumessenger.viewmodels.ResetPasswordViewModel

/** Zmiana hasła zalogowanego użytkownika; otwierana też deep linkiem `yourapp://resetpassword`. */
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private val viewModel: ResetPasswordViewModel by viewModels { ResetPasswordViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.resetPasswordButton.setOnClickListener {
            viewModel.changePassword(
                binding.newPasswordEditText.text.toString(),
                binding.confirmPasswordEditText.text.toString()
            )
        }
        binding.backToLoginTextView.setOnClickListener { openLogin() }

        collectWhenStarted(viewModel.uiState) { state ->
            binding.resetPasswordButton.isEnabled = !state.isLoading
            binding.newPasswordEditText.error = state.newPasswordError?.asString(this)
            binding.confirmPasswordEditText.error = state.confirmPasswordError?.asString(this)
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is ResetPasswordEvent.PasswordChanged -> {
                    showToast(event.text)
                    openLogin()
                }
                is ResetPasswordEvent.ShowMessage -> showToast(event.text, long = true)
            }
        }
    }

    private fun openLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
