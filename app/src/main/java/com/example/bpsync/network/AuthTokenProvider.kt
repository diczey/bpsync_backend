package com.example.bpsync.network

import android.content.Context

/**
 * JWT token ve giriş yapan kullanıcı bilgisini saklar (devfixed uyumlu).
 * SharedPreferences ile kalıcı; token her istekte Authorization header'a eklenir.
 * Kullanıcı: giriş/kayıt sonrası kaydedilir, uygulama açıldığında "Hoş geldin, X" için kullanılır.
 */
object AuthTokenProvider {

    private const val PREFS_NAME = "bpsync_auth"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"

    @Volatile
    var token: String? = null
        private set

    @Volatile
    var savedUserId: String? = null
        private set

    @Volatile
    var savedUserEmail: String? = null
        private set

    @Volatile
    var savedUserName: String? = null
        private set

    fun setToken(newToken: String?) {
        token = newToken
    }

    /** Token'ı SharedPreferences'a kaydeder */
    fun setTokenAndPersist(context: Context, newToken: String?) {
        token = newToken
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, newToken)
            .apply()
    }

    /** Giriş/kayıt sonrası kullanıcıyı hafızaya kaydeder (devfixed: backend'den dönen user). */
    fun saveUserAndPersist(context: Context, user: UserDto?) {
        if (user == null) {
            savedUserId = null
            savedUserEmail = null
            savedUserName = null
        } else {
            savedUserId = user.id
            savedUserEmail = user.email
            savedUserName = user.name
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ID, savedUserId)
            .putString(KEY_USER_EMAIL, savedUserEmail)
            .putString(KEY_USER_NAME, savedUserName)
            .apply()
    }

    /** Uygulama açılışında çağrılır; kayıtlı token ve kullanıcıyı yükler */
    fun loadFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        token = prefs.getString(KEY_TOKEN, null)
        savedUserId = prefs.getString(KEY_USER_ID, null)
        savedUserEmail = prefs.getString(KEY_USER_EMAIL, null)
        savedUserName = prefs.getString(KEY_USER_NAME, null)
    }

    fun clearToken() {
        token = null
    }

    /** Çıkış: token ve kullanıcıyı hem bellekten hem prefs'tan siler */
    fun clearTokenAndPersist(context: Context) {
        token = null
        savedUserId = null
        savedUserEmail = null
        savedUserName = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .apply()
    }

    fun hasToken(): Boolean = !token.isNullOrBlank()
}
