package com.example.aplikacjatypumessenger

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.aplikacjatypumessenger.di.AppContainer
import com.example.aplikacjatypumessenger.services.PresenceObserver

class MessengerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            PresenceObserver(container.authRepository, container.userRepository, container.applicationScope)
        )
    }
}
