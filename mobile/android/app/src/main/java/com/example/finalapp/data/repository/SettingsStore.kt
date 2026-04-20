package com.example.finalapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppSettings(
    val language: String = "en",
    val notificationsEnabled: Boolean = true,
    val weeklyReportsEnabled: Boolean = false,
)

object SettingsStore {
    private const val PREFS_NAME = "bpsync_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_PUSH_NOTIFICATIONS = "push_notifications"
    private const val KEY_WEEKLY_REPORTS = "weekly_reports"

    @Volatile
    private var preferences: SharedPreferences? = null

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun initialize(context: Context) {
        if (preferences != null) return

        synchronized(this) {
            if (preferences != null) return

            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            preferences = prefs
            _settings.value = AppSettings(
                language = prefs.getString(KEY_LANGUAGE, "en") ?: "en",
                notificationsEnabled = prefs.getBoolean(KEY_PUSH_NOTIFICATIONS, true),
                weeklyReportsEnabled = prefs.getBoolean(KEY_WEEKLY_REPORTS, false)
            )
        }
    }

    fun replace(settings: AppSettings) {
        _settings.value = settings.normalize()
        persist()
    }

    fun setLanguage(language: String) {
        _settings.update { it.copy(language = language.normalizeLanguage()) }
        persist()
    }

    fun setPushNotifications(enabled: Boolean) {
        _settings.update { it.copy(notificationsEnabled = enabled) }
        persist()
    }

    fun setWeeklyReports(enabled: Boolean) {
        _settings.update { it.copy(weeklyReportsEnabled = enabled) }
        persist()
    }

    private fun persist() {
        val prefs = preferences ?: return
        val value = _settings.value
        prefs.edit()
            .putString(KEY_LANGUAGE, value.language.normalizeLanguage())
            .putBoolean(KEY_PUSH_NOTIFICATIONS, value.notificationsEnabled)
            .putBoolean(KEY_WEEKLY_REPORTS, value.weeklyReportsEnabled)
            .apply()
    }
}

private fun AppSettings.normalize(): AppSettings {
    return copy(language = language.normalizeLanguage())
}

private fun String.normalizeLanguage(): String {
    return if (lowercase() == "tr") "tr" else "en"
}
