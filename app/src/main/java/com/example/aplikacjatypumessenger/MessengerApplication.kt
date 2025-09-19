package com.example.aplikacjatypumessenger

import android.app.Application
import com.google.firebase.FirebaseApp

class MessengerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}