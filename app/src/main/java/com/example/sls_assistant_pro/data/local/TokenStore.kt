package com.example.sls_assistant_pro.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TokenStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sls_token_store", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getSessionToken(): String? {
        return prefs.getString("saved_session", null)
    }

    fun saveSessionToken(sessionJson: String) {
        prefs.edit().putString("saved_session", sessionJson).apply()
    }

    fun clearSession() {
        prefs.edit().remove("saved_session").apply()
    }

    fun getParsedSession(): Map<String, Any?> {
        val json = getSessionToken() ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getBearerToken(): String? {
        val map = getParsedSession()
        return map["bearer"] as? String
    }

    fun getCookie(): String? {
        val map = getParsedSession()
        return map["cookie"] as? String
    }
}
