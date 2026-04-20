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
        "alert" -> "Uyari"
        "reminder" -> "Hatirlatma"
        "achievement" -> "Basarim"
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
        "elevated" -> "Yukselmis"
        "stage 1 high" -> "Evre 1 Yuksek"
        "stage 2 high" -> "Evre 2 Yuksek"
        "stage 1 hypertension" -> "Evre 1 Hipertansiyon"
        "stage 2 hypertension" -> "Evre 2 Hipertansiyon"
        "attention required" -> "Dikkat Gerekli"
        "needs attention" -> "Dikkat Gerekli"
        "excellent" -> "Mukemmel"
        "good" -> "Iyi"
        "fair" -> "Orta"
        "optimal" -> "Optimal"
        "calibrating" -> "Kalibrasyon"
        "personalized tracking" -> "Kisisellestirilmis Takip"
        "critical" -> "Kritik"
        "low" -> "Dusuk"
        "high" -> "Yuksek"
        "no data" -> "Veri Yok"
        "alert" -> "Uyari"
        else -> status
    }
}

fun translateGender(gender: String, isTurkish: Boolean): String {
    if (!isTurkish) return gender

    return when (gender.lowercase()) {
        "male" -> "Erkek"
        "female" -> "Kadin"
        "other" -> "Diger"
        else -> gender
    }
}

fun translateMessage(message: String, isTurkish: Boolean): String {
    if (!isTurkish) return message

    return when {
        message == "No notifications yet." -> "Henuz bildirim yok."
        message == "No data for this week." -> "Bu hafta icin veri yok."
        message == "No data for this month." -> "Bu ay icin veri yok."
        message == "No data for this period." -> "Bu donem icin veri yok."
        message == "No readings yet. Connect your BPSync wristband to start measuring." ->
            "Henuz olcum yok. Olcume baslamak icin BPSync bilekligini bagla."
        message == "Failed to generate signal." -> "Sinyal olusturulamadi."
        message == "Profile saved successfully!" -> "Profil basariyla kaydedildi!"
        message == "Connect your wristband to start the 3-day calibration countdown." ->
            "3 gunluk kalibrasyon geri sayimini baslatmak icin bilekligini bagla."
        message == "3-day BLE calibration is in progress." ->
            "3 gunluk BLE kalibrasyonu devam ediyor."
        message == "Calibration completed. Personalized daily labels are active until day 7." ->
            "Kalibrasyon tamamlandi. 7. gune kadar kisisellestirilmis gunluk etiketler aktif."
        message == "7-day personalized health status is ready." ->
            "7 gunluk kisisellestirilmis saglik durumu hazir."
        message == "Collect readings across 7 distinct days to unlock weekly status." ->
            "Haftalik durumu acmak icin 7 farkli gunde olcum topla."
        message == "Not enough weekly readings to calculate health status." ->
            "Saglik durumunu hesaplamak icin haftalik olcum verisi yeterli degil."
        message == "This device does not support Bluetooth LE." ->
            "Bu cihaz Bluetooth LE desteklemiyor."
        message == "Bluetooth is turned off. Turn it on and try again." ->
            "Bluetooth kapali. Acip tekrar deneyin."
        message == "Bluetooth scanner is unavailable on this device." ->
            "Bu cihazda Bluetooth tarayici kullanilamiyor."
        message == "No Bluetooth LE devices found nearby." ->
            "Yakinda Bluetooth LE cihazi bulunamadi."
        message == "Couldn't resolve the selected Bluetooth device." ->
            "Secilen Bluetooth cihazi cozumlenemedi."
        message == "Connection timed out. Try again closer to the device." ->
            "Baglanti zaman asimina ugradi. Cihaza daha yakinken tekrar deneyin."
        message == "The device disconnected before streaming started." ->
            "Akis baslamadan once cihaz baglantisi kesildi."
        message.startsWith("Couldn't connect to ") ->
            "Cihaza baglanilamadi. Yakinda oldugundan ve baska bir yere bagli olmadigindan emin olun."
        message == "BPSync wrist service or characteristics were not found." ->
            "BPSync bileklik servisi veya ozellikleri bulunamadi."
        message == "Backend is ready to use CNN-LSTM for waveform BLE batches." ->
            "Backend, waveform BLE paketleri icin CNN-LSTM kullanmaya hazir."
        message == "Using Goksu's CNN-LSTM waveform model." ->
            "Goksu'nun CNN-LSTM waveform modeli kullaniliyor."
        message.startsWith("CNN-LSTM requires complete waveform BLE batches:") ->
            "CNN-LSTM icin tam waveform BLE paketleri gerekiyor: ECG, PPG_RED ve PPG_IR verisi eksik ya da uyumsuz."
        else -> message
    }
}
