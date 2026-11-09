package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.aplikacjatypumessenger.databinding.ActivityLoginBinding
import com.example.aplikacjatypumessenger.databinding.DialogForgotPasswordBinding
import com.example.aplikacjatypumessenger.utils.applySystemBarsPadding
import com.example.aplikacjatypumessenger.utils.collectWhenStarted
import com.example.aplikacjatypumessenger.utils.showToast
import com.example.aplikacjatypumessenger.viewmodels.LoginEvent
import com.example.aplikacjatypumessenger.viewmodels.LoginViewModel

/**
 * Ekran logowania. Activity przekazuje tylko wpisane dane do [LoginViewModel]
 * i wyświetla to, co ten zwróci (błędy przy polach, toasty, przejście dalej).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            viewModel.login(binding.emailEditText.text.toString(), binding.passwordEditText.text.toString())
        }
        binding.registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.forgotPasswordTextView.setOnClickListener { showForgotPasswordDialog() }
    }

    private fun observeViewModel() {
        collectWhenStarted(viewModel.uiState) { state ->
            binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.loginButton.isEnabled = !state.isLoading
            // null usuwa komunikat błędu z pola
            binding.emailEditText.error = state.emailError?.asString(this)
            binding.passwordEditText.error = state.passwordError?.asString(this)
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                LoginEvent.NavigateToMain -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                is LoginEvent.ShowMessage -> showToast(event.text, long = true)
            }
        }
    }

    /** Okno „Zapomniałeś hasła?”; podpowiada adres wpisany już w formularzu logowania. */
    private fun showForgotPasswordDialog() {
        val dialogBinding = DialogForgotPasswordBinding.inflate(layoutInflater)
        dialogBinding.emailEditText.setText(binding.emailEditText.text)
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_password_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_send) { _, _ ->
                viewModel.sendPasswordReset(dialogBinding.emailEditText.text.toString())
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
