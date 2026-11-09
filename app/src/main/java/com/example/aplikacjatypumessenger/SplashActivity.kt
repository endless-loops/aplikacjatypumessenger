package com.example.aplikacjatypumessenger

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.aplikacjatypumessenger.databinding.ActivitySplashBinding
import com.example.aplikacjatypumessenger.utils.applySystemBarsPadding
import com.example.aplikacjatypumessenger.utils.collectWhenStarted
import com.example.aplikacjatypumessenger.viewmodels.SplashViewModel
import kotlinx.coroutines.flow.filterNotNull

/** Ekran startowy z logo; o tym, dokąd przejść dalej, decyduje [SplashViewModel]. */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModels { SplashViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        // Animacja pojawiania się logo tylko przy pierwszym uruchomieniu ekranu
        if (savedInstanceState == null) {
            binding.logoImageView.alpha = 0f
            binding.logoImageView.animate().alpha(1f).setDuration(1000).start()
        }

        collectWhenStarted(viewModel.destination.filterNotNull()) { destination ->
            val next = when (destination) {
                SplashViewModel.Destination.MAIN -> MainActivity::class.java
                SplashViewModel.Destination.LOGIN -> LoginActivity::class.java
            }
            startActivity(Intent(this, next))
            finish() // żeby przycisk Wstecz nie wracał do ekranu startowego
        }
    }
}
