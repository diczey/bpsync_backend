/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           BPSync — Sensor Test (NO BLE)                     ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Sadece sensörleri test eder, Serial Monitor'a yazar.       ║
 * ║  BLE yok. Hızlı donanım doğrulaması için.                   ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Board   : Seeed XIAO nRF52840                              ║
 * ║  Baud    : 115200                                           ║
 * ║  SENSORS (I2C — SDA=D4, SCL=D5)                            ║
 * ║    MAX30102  @ 0x57  →  PPG (IR + Red)                     ║
 * ║    MPU6050   @ 0x68  →  Accelerometer + Gyroscope          ║
 * ║    MCP9808   @ 0x18  →  Skin Temperature                   ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 *  KULLANIM:
 *    1) platformio.ini'ye geçici olarak şunu ekle:
 *       src_dir = firmware/sensor_test
 *    2) Build + Upload
 *    3) Serial Monitor aç (115200 baud)
 *    4) Test bitince src_dir = firmware/wrist_module'e geri al
 */

#include <Wire.h>
#include <MAX30105.h>
#include <MPU6050.h>
#include <Adafruit_MCP9808.h>

// ──────────────────────────────────────────────────────────────
//  EŞIKLER
// ──────────────────────────────────────────────────────────────
#define PPG_MIN_SIGNAL    1000    // IR < bu değer → parmak yok
#define PPG_GOOD_SIGNAL   50000  // IR > bu değer → iyi sinyal
#define SQI_MOTION_THR    4096   // 16384 = 1g, ~0.25g sapma eşiği

// ──────────────────────────────────────────────────────────────
//  SENSOR NESNELERI
// ──────────────────────────────────────────────────────────────
MAX30105         ppg;
MPU6050          imu;
Adafruit_MCP9808 temp;

// Sensör başarı durumları
bool ppgOK  = false;
bool imuOK  = false;
bool tempOK = false;

// ──────────────────────────────────────────────────────────────
//  SETUP
// ──────────────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    delay(1000);   // USB CDC bağlanması için bekle

    Serial.println();
    Serial.println("==============================================");
    Serial.println("  BPSync Sensor Test");
    Serial.println("  Seeed XIAO nRF52840");
    Serial.println("==============================================");

    Wire.begin();   // SDA=D4, SCL=D5

    // ── MAX30102 ──────────────────────────────────────────────
    if (!ppg.begin(Wire, I2C_SPEED_FAST)) {
        Serial.println("[ERROR] MAX30102 — bulunamadi!");
        Serial.println("        Kontrol: VCC=3.3V, SDA=D4, SCL=D5");
    } else {
        //              ledBrightness  avgSamples  ledMode  sampleRate  pulseWidth  adcRange
        ppg.setup(0x1F, 4, 2, 100, 411, 4096);
        ppg.setPulseAmplitudeRed(0x1F);
        ppg.setPulseAmplitudeIR(0x1F);
        ppgOK = true;
        Serial.println("[OK]  MAX30102  (PPG)");
    }

    // ── MPU6050 ───────────────────────────────────────────────
    imu.initialize();
    if (!imu.testConnection()) {
        Serial.println("[ERROR] MPU6050 — bulunamadi!");
        Serial.println("        Kontrol: VCC=3.3V, SDA=D4, SCL=D5, AD0=GND");
    } else {
        imu.setFullScaleAccelRange(MPU6050_ACCEL_FS_2);   // ±2 g  → 16384 LSB/g
        imu.setFullScaleGyroRange(MPU6050_GYRO_FS_250);   // ±250 °/s → 131 LSB/°/s
        imuOK = true;
        Serial.println("[OK]  MPU6050   (IMU)");
    }

    // ── MCP9808 ───────────────────────────────────────────────
    if (!temp.begin(0x18)) {
        Serial.println("[ERROR] MCP9808 — bulunamadi!");
        Serial.println("        Kontrol: VCC=3.3V, SDA=D4, SCL=D5, A0=A1=A2=GND");
    } else {
        temp.setResolution(3);   // 0.0625 °C çözünürlük
        temp.wake();
        tempOK = true;
        Serial.println("[OK]  MCP9808   (Temp)");
    }

    // ── Özet ──────────────────────────────────────────────────
    Serial.println("----------------------------------------------");
    Serial.print  ("  Durum: MAX30102=");
    Serial.print  (ppgOK  ? "OK" : "FAIL");
    Serial.print  ("  MPU6050=");
    Serial.print  (imuOK  ? "OK" : "FAIL");
    Serial.print  ("  MCP9808=");
    Serial.println(tempOK ? "OK" : "FAIL");
    Serial.println("==============================================");
    Serial.println();

    if (!ppgOK && !imuOK && !tempOK) {
        Serial.println("[FATAL] Hicbir sensor bulunamadi. Baglantilari kontrol et.");
        Serial.println("  I2C bus tarama baslatiliyor...");
        scanI2C();
        while (true) { delay(1000); }
    }

    Serial.println("Her 500ms'de bir okuma yapilacak.");
    Serial.println("Kolon siralama:  [PPG-IR] [PPG-Red] [Signal] | "
                   "[Ax g] [Ay g] [Az g] | [Gx/s] [Gy/s] [Gz/s] | "
                   "[Temp C] | [SQI]");
    Serial.println("----------------------------------------------");
}


// ──────────────────────────────────────────────────────────────
//  LOOP — her 500 ms'de bir yazdır
// ──────────────────────────────────────────────────────────────
void loop() {
    static unsigned long lastPrint = 0;
    static unsigned long lastTemp  = 0;
    static float         latestTemp = 0.0f;
    static uint32_t      ir = 0, red = 0;
    static int16_t       ax=0, ay=0, az=0, gx=0, gy=0, gz=0;

    unsigned long now = millis();

    // PPG: MAX30102 FIFO'sunu sürekli boşalt
    if (ppgOK && ppg.available()) {
        ir  = ppg.getIR();
        red = ppg.getRed();
        ppg.nextSample();
    }

    // IMU: sürekli oku
    if (imuOK) {
        imu.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);
    }

    // Temp: 1 Hz yeterli
    if (tempOK && (now - lastTemp >= 1000)) {
        float t = temp.readTempC();
        if (!isnan(t)) latestTemp = t;
        lastTemp = now;
    }

    // Print: 500 ms
    if (now - lastPrint >= 500) {
        lastPrint = now;
        printReadings(ir, red, ax, ay, az, gx, gy, gz, latestTemp);
    }
}


// ──────────────────────────────────────────────────────────────
//  printReadings — okunabilir çıktı
// ──────────────────────────────────────────────────────────────
void printReadings(uint32_t ir, uint32_t red,
                   int16_t ax, int16_t ay, int16_t az,
                   int16_t gx, int16_t gy, int16_t gz,
                   float tempC) {

    // ── PPG ───────────────────────────────────────────────────
    const char* ppgLabel;
    if (!ppgOK) {
        ppgLabel = "SENSOR_FAIL";
    } else if (ir < PPG_MIN_SIGNAL) {
        ppgLabel = "NO_FINGER  ";
    } else if (ir > PPG_GOOD_SIGNAL) {
        ppgLabel = "GOOD       ";
    } else {
        ppgLabel = "WEAK       ";
    }

    // ── IMU → gerçek birimler ─────────────────────────────────
    // ±2g scale → 16384 LSB per g
    float fAx = ax / 16384.0f;
    float fAy = ay / 16384.0f;
    float fAz = az / 16384.0f;
    // ±250°/s scale → 131 LSB per °/s
    float fGx = gx / 131.0f;
    float fGy = gy / 131.0f;
    float fGz = gz / 131.0f;

    // ── SQI hesapla ───────────────────────────────────────────
    float mag = sqrt(fAx*fAx + fAy*fAy + fAz*fAz);
    float dev = fabs(mag - 1.0f);          // beklenen: 1g
    bool motionOK = (dev < 0.25f);         // 0.25g eşiği
    bool sigOK    = (ir  > PPG_MIN_SIGNAL);
    uint8_t sqi   = (motionOK && sigOK) ? 1 : 0;

    // ── Çıktı ─────────────────────────────────────────────────
    char buf[180];
    snprintf(buf, sizeof(buf),
        "PPG IR=%-6lu Red=%-6lu [%s] | "
        "Ax=%+.2fg Ay=%+.2fg Az=%+.2fg | "
        "Gx=%+6.1f Gy=%+6.1f Gz=%+6.1f dps | "
        "T=%.2fC | SQI=%d",
        (unsigned long)ir,
        (unsigned long)red,
        ppgOK ? ppgLabel : "SENSOR_FAIL",
        fAx, fAy, fAz,
        fGx, fGy, fGz,
        tempC,
        sqi
    );

    Serial.println(buf);

    // Ek uyarılar
    if (ppgOK && ir < PPG_MIN_SIGNAL) {
        Serial.println("  >> Parmagi/bilegi sensore daya.");
    }
    if (imuOK) {
        Serial.print  ("  >> Ivme buyuklugu=");
        Serial.print  (mag, 3);
        Serial.print  ("g  (beklenen ~1.0g)");
        if (!motionOK) Serial.print("  HAREKET VAR!");
        Serial.println();
    }
    if (tempOK && (tempC < 20.0f || tempC > 42.0f)) {
        Serial.println("  >> Sicaklik aralik disi — sensoru kontrol et.");
    }
    Serial.println();
}


// ──────────────────────────────────────────────────────────────
//  scanI2C — tüm I2C adreslerini tara (sensör bulunamazsa)
// ──────────────────────────────────────────────────────────────
void scanI2C() {
    Serial.println("I2C tarama (0x01 - 0x7E):");
    int found = 0;
    for (uint8_t addr = 1; addr < 127; addr++) {
        Wire.beginTransmission(addr);
        uint8_t err = Wire.endTransmission();
        if (err == 0) {
            Serial.print("  [FOUND] 0x");
            if (addr < 16) Serial.print("0");
            Serial.println(addr, HEX);
            found++;
        }
    }
    if (found == 0) {
        Serial.println("  Hicbir cihaz bulunamadi. SDA/SCL kablolarini kontrol et.");
    } else {
        Serial.print("  Toplam ");
        Serial.print(found);
        Serial.println(" cihaz bulundu.");
    }
    Serial.println("  Beklenen: MAX30102=0x57, MPU6050=0x68, MCP9808=0x18");
}
