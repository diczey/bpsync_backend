package com.example.finalapp.ui.localization

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.finalapp.data.repository.SettingsStore

@Composable
fun rememberIsTurkish(): Boolean {
    val settings = SettingsStore.settings.collectAsStateWithLifecycle()
    return settings.value.language == "tr"
}

fun isTurkishSelected(): Boolean = SettingsStore.settings.value.language == "tr"

fun translateNotificationType(type: String, isTurkish: Boolean): String {
    if (!isTurkish) return type.replaceFirstChar { it.titlecase() }

    return when (type.lowercase()) {
        "alert" -> "Uyarı"
        "reminder" -> "Hatırlatma"
        "achievement" -> "Başarım"
        "info" -> "Bilgi"
        "report" -> "Rapor"
        else -> type.replaceFirstChar { it.titlecase() }
    }
}

fun translateStatus(status: String, isTurkish: Boolean): String {
    if (!isTurkish) return status

    return when (status.lowercase()) {
        "normal" -> "Normal"
        "waiting" -> "Bekleniyor"
        "elevated" -> "Yükselmiş"
        "stage 1 high" -> "Evre 1 Yüksek"
        "stage 2 high" -> "Evre 2 Yüksek"
        "stage 1 hypertension" -> "Evre 1 Hipertansiyon"
        "stage 2 hypertension" -> "Evre 2 Hipertansiyon"
        "attention required" -> "Dikkat Gerekli"
        "needs attention" -> "Dikkat Gerekli"
        "excellent" -> "Mükemmel"
        "good" -> "İyi"
        "fair" -> "Orta"
        "optimal" -> "Optimal"
        "critical" -> "Kritik"
        "low" -> "Düşük"
        "high" -> "Yüksek"
        "no data" -> "Veri Yok"
        "alert" -> "Uyarı"
        else -> status
    }
}

fun translateGender(gender: String, isTurkish: Boolean): String {
    if (!isTurkish) return gender

    return when (gender.lowercase()) {
        "male" -> "Erkek"
        "female" -> "Kadın"
        "other" -> "Diğer"
        else -> gender
    }
}

fun translateMessage(message: String, isTurkish: Boolean): String {
    if (!isTurkish) return message

    return when {
        message == "No notifications yet." -> "Henüz bildirim yok."
        message == "No data for this week." -> "Bu hafta için veri yok."
        message == "No data for this month." -> "Bu ay için veri yok."
        message == "No data for this period." -> "Bu dönem için veri yok."
        message == "No readings yet. Connect your BPSync wristband to start measuring." ->
            "Henüz ölçüm yok. Ölçüme başlamak için BPSync bilekliğini bağla."
        message == "Failed to generate signal." -> "Sinyal oluşturulamadı."
        message == "Profile saved successfully!" -> "Profil başarıyla kaydedildi!"
        message == "This device does not support Bluetooth LE." ->
            "Bu cihaz Bluetooth LE desteklemiyor."
        message == "Bluetooth is turned off. Turn it on and try again." ->
            "Bluetooth kapalı. Açıp tekrar deneyin."
        message == "Bluetooth scanner is unavailable on this device." ->
            "Bu cihazda Bluetooth tarayıcı kullanılamıyor."
        message == "No Bluetooth LE devices found nearby." ->
            "Yakında Bluetooth LE cihazı bulunamadı."
        message == "Couldn't resolve the selected Bluetooth device." ->
            "Seçilen Bluetooth cihazı çözümlenemedi."
        message == "Connection timed out. Try again closer to the device." ->
            "Bağlantı zaman aşımına uğradı. Cihaza daha yakınken tekrar deneyin."
        message == "The device disconnected before streaming started." ->
            "Akış başlamadan önce cihaz bağlantısı kesildi."
        message.startsWith("Couldn't connect to ") ->
            "Cihaza bağlanılamadı. Yakında olduğundan ve başka bir yere bağlı olmadığından emin olun."
        message == "BPSync wrist service or characteristics were not found." ->
            "BPSync bileklik servisi veya özellikleri bulunamadı."
        else -> message
    }
}
