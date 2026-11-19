package com.mobilelinuxstudio.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePrefs(context: Context) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "dsh_linux_studio_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var dshPort: Int
        get() = prefs.getInt(KEY_DSH_PORT, 3080)
        set(value) = prefs.edit().putInt(KEY_DSH_PORT, value).apply()

    var codePort: Int
        get() = prefs.getInt(KEY_CODE_PORT, 8080)
        set(value) = prefs.edit().putInt(KEY_CODE_PORT, value).apply()

    companion object {
        private const val KEY_API_KEY = "deepseek_api_key"
        private const val KEY_DSH_PORT = "dsh_port"
        private const val KEY_CODE_PORT = "code_port"

        @Volatile
        private var instance: SecurePrefs? = null

        fun get(context: Context): SecurePrefs {
            return instance ?: synchronized(this) {
                instance ?: SecurePrefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
