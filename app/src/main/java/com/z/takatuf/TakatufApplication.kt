package com.z.takatuf

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TakatufApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Debug builds made with -Ptakatuf.emulatorHost=... talk to the local emulators.
        val emulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST
        if (emulatorHost.isNotEmpty()) {
            FirebaseAuth.getInstance().useEmulator(emulatorHost, 9099)
            FirebaseFirestore.getInstance().useEmulator(emulatorHost, 8080)
        }
        container = AppContainer()
    }
}
