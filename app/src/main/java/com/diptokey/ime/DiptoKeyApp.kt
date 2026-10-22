package com.diptokey.ime

import android.app.Application
import com.diptokey.ime.storage.AppDatabase
import com.diptokey.ime.security.EncryptedSecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application class — initializes singletons and background scopes.
 *
 * NOTE: The IME service and the settings activity share the same process by default
 * (since both are declared in the same manifest). Singletons initialized here are
 * visible to both.
 */
class DiptoKeyApp : Application() {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set

    lateinit var secretStore: EncryptedSecretStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.get(this)
        secretStore = EncryptedSecretStore(this)
    }

    companion object {
        @Volatile
        lateinit var instance: DiptoKeyApp
            private set
    }
}
