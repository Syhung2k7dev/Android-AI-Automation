package com.syhung.androidaiautomation

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object CloudKeyStore {
    private const val PREFS = "encrypted_cloud_settings"
    private const val API_KEY = "gemini_api_key"

    private fun preferences(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(context: Context, key: String) {
        require(key.length in 20..256 && key.none { it.isWhitespace() })
        preferences(context).edit().putString(API_KEY, key).apply()
    }

    fun read(context: Context): String? = preferences(context).getString(API_KEY, null)

    fun clear(context: Context) {
        preferences(context).edit().remove(API_KEY).apply()
    }

    fun hasKey(context: Context): Boolean = !read(context).isNullOrBlank()
}
