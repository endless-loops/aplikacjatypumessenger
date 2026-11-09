package com.example.aplikacjatypumessenger

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.aplikacjatypumessenger.di.AppContainer
import com.example.aplikacjatypumessenger.services.PresenceObserver

/**
 * Klasa aplikacji - tworzona raz na proces, przed jakimkolwiek ekranem.
 * Trzyma kontener zależności, z którego korzystają ViewModele i serwisy.
 */
class MessengerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
        // ProcessLifecycleOwner patrzy na całą aplikację, a nie na pojedyncze Activity,
        // więc przejście między ekranami nie zmienia statusu na offline
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            PresenceObserver(container.authRepository, container.userRepository, container.applicationScope)
        )
    }
}
