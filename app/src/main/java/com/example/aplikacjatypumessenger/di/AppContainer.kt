package com.example.aplikacjatypumessenger.di

import com.example.aplikacjatypumessenger.repositories.AuthRepository
import com.example.aplikacjatypumessenger.repositories.ChatRepository
import com.example.aplikacjatypumessenger.repositories.MessageRepository
import com.example.aplikacjatypumessenger.repositories.UserRepository
import com.example.aplikacjatypumessenger.services.ActiveChatTracker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Ręczne wstrzykiwanie zależności: jedna instancja repozytoriów na cały proces. */
class AppContainer {
    private val firestore = FirebaseFirestore.getInstance()

    /** Zakres dla operacji, które mają przetrwać zamknięcie ekranu (np. status online). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val authRepository = AuthRepository(FirebaseAuth.getInstance())
    val userRepository = UserRepository(firestore, FirebaseMessaging.getInstance())
    val chatRepository = ChatRepository(firestore)
    val messageRepository = MessageRepository(firestore)
    val activeChatTracker = ActiveChatTracker()
}
