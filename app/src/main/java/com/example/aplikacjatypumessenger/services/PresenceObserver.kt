package com.example.aplikacjatypumessenger.services

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.aplikacjatypumessenger.repositories.AuthRepository
import com.example.aplikacjatypumessenger.repositories.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Ustawia status online, gdy aplikacja jest na pierwszym planie, i offline, gdy przechodzi w tło. */
class PresenceObserver(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) = update(online = true)

    override fun onStop(owner: LifecycleOwner) = update(online = false)

    private fun update(online: Boolean) {
        val userId = authRepository.currentUserId ?: return
        scope.launch {
            runCatching { userRepository.setOnline(userId, online) }
                .onFailure { Log.w(TAG, "Nie udało się zaktualizować statusu", it) }
        }
    }

    private companion object {
        const val TAG = "PresenceObserver"
    }
}
