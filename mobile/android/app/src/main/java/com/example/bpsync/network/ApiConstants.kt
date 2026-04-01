package com.example.bpsync.network

/**
 * Backend API adresleri.
 * bpsync-devfixed backend yapısına göre (referans: backend değiştirilmez, sadece frontend buna uyar).
 *
 * NASIL AYARLANIR:
 * - Emülatör (varsayılan): OVERRIDE_BASE_URL = "" → 10.0.2.2:8000 (bilgisayar localhost)
 * - Emülatörde bağlanamıyorsan: OVERRIDE_BASE_URL = "http://BILGISAYAR_IP:8000/" (örn. "http://192.168.1.101:8000/")
 * - Gerçek telefon: USE_REAL_DEVICE = true ve REAL_DEVICE_IP = bilgisayarın WiFi IP'si
 *
 * Test: Giriş ekranında "Backend bağlantı testi" butonuna basın.
 */
object ApiConstants {

    /** Bilgisayar IP ile zorunlu bağlan. Boş = emülatörde 10.0.2.2 kullanılır. */
    private const val OVERRIDE_BASE_URL = ""

    /** true = gerçek telefonda test, false = emülatör */
    private const val USE_REAL_DEVICE = false

    /** Gerçek cihazda: Bilgisayarın WiFi IP'si (örn. 192.168.1.100) */
    private const val REAL_DEVICE_IP = "192.168.1.100"

    private const val EMULATOR_BASE = "http://10.0.2.2:8000/"
    private const val REAL_DEVICE_BASE = "http://$REAL_DEVICE_IP:8000/"

    val BASE_URL: String
        get() = if (OVERRIDE_BASE_URL.isNotBlank()) OVERRIDE_BASE_URL else if (USE_REAL_DEVICE) REAL_DEVICE_BASE else EMULATOR_BASE

    const val AUTH_LOGIN = "auth/login"
    const val AUTH_REGISTER = "auth/register"
    const val AUTH_LOGOUT = "auth/logout"
    const val DASHBOARD_SUMMARY = "dashboard/summary"
    const val READINGS = "readings"
    const val READINGS_PREDICT_BP = "readings/predict-bp"
    const val READINGS_CALIBRATE_BP = "readings/calibrate-bp"
    const val READINGS_MODEL_INFO = "readings/model-info"
    const val TRENDS = "trends"
    const val REPORTS_WEEKLY = "reports/weekly"
    const val REPORTS_MONTHLY = "reports/monthly"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
}
