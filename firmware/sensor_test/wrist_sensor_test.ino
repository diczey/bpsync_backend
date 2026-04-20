/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           BPSync — Wrist Module Sensor Test                 ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Sadece sensörleri test eder. BLE yok.                      ║
 * ║  Serial Monitor'a her 500ms'de bir okuma yazar.             ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Board   : Seeed XIAO nRF52840                              ║
 * ║  Baud    : 115200                                           ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  ARDUINO IDE KURULUM (ilk kez için):                        ║
 * ║                                                             ║
 * ║  1) File > Preferences > Additional Board Manager URLs:     ║
 * ║     https://files.seeedstudio.com/arduino/               ║
 * ║     package_seeeduino_boards_index.json                     ║
 * ║                                                             ║
 * ║  2) Tools > Board Manager > "Seeed nRF52" ara > Install     ║
 * ║                                                             ║
 * ║  3) Tools > Board > Seeed nRF52 Boards >                    ║
 * ║     "Seeed XIAO nRF52840" seç                               ║
 * ║                                                             ║
 * ║  4) Library Manager'dan şunları kur:                        ║
 * ║     - "SparkFun MAX3010x Pulse and Proximity Sensor"        ║
 * ║     - "I2Cdevlib-MPU6050" (Jeff Rowberg)                    ║
 * ║     - "Adafruit MCP9808 Library"                            ║
 * ║     - "Adafruit BusIO"                                      ║
 * ║                                                             ║
 * ║  5) Upload etmeden önce XIAO'yu bootloader moduna al:       ║
 * ║     Reset butonuna hızlıca 2 kez bas                        ║
 * ║     (turuncu LED yavaş yanarsa bootloader modundasın)       ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  DONANIM BAĞLANTISI                                         ║
 * ║    Sensör VCC → XIAO 5V  (USB bağlıyken aktif)             ║
 * ║    Sensör GND → XIAO GND                                    ║
 * ║    SDA        → XIAO D4                                     ║
 * ║    SCL        → XIAO D5                                     ║
 * ║    MAX30102   @ I2C 0x57                                    ║
 * ║    MPU6050    @ I2C 0x68  (AD0=GND)                        ║
 * ║    MCP9808    @ I2C 0x18  (A0=A1=A2=GND)                   ║
 * ╚══════════════════════════════════════════════════════════════╝
 */

// Adafruit nRF52 board paketi XIAO'nun Dx pin isimlerini tanımlamıyor
// XIAO nRF52840: D2=P0.28, D3=P0.29, D4=P0.04(SDA), D5=P0.05(SCL)
#ifndef D2
  #define D0   2
  #define D1   3
  #define D2  28
  #define D3  29
  #define D4   4
  #define D5   5
#endif

#include <Wire.h>
#include <MAX30105.h>
#include <MPU6050.h>
#include <Adafruit_MCP9808.h>

// ──────────────────────────────────────────────────────────────
//  EŞIKLER
// ──────────────────────────────────────────────────────────────
#define PPG_MIN_SIGNAL   10000   // IR < bu değer → bilek sensörde değil
#define PPG_GOOD_SIGNAL  60000   // IR > bu değer → iyi bilek teması (damar üstü ~97k-100k)
#define SQI_MOTION_THR   0.25f   // g cinsinden hareket eşiği

// ──────────────────────────────────────────────────────────────
//  SENSOR NESNELERİ
// ──────────────────────────────────────────────────────────────
MAX30105         ppg;
MPU6050          imu;
Adafruit_MCP9808 tempSensor;

bool ppgOK  = false;
bool imuOK  = false;
bool tempOK = false;

// ──────────────────────────────────────────────────────────────
//  SETUP
// ──────────────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    delay(2000);  // USB CDC bağlantısı için bekle

    Serial.println();
    Serial.println("==============================================");
    Serial.println("  BPSync Wrist Module - Sensor Test");
    Serial.println("  Seeed XIAO nRF52840");
    Serial.println("==============================================");

    Wire.begin();  // SDA=D4, SCL=D5

    // ── MAX30102 (PPG) ────────────────────────────────────────
    Serial.print("MAX30102 test ediliyor... ");
    if (!ppg.begin(Wire, I2C_SPEED_FAST)) {
        Serial.println("FAIL!");
        Serial.println("  >> VCC=5V, SDA=D4, SCL=D5 baglantilarini kontrol et.");
        Serial.println("  >> I2C adresi: 0x57");
    } else {
        ppg.setup(0x1F, 4, 2, 100, 411, 4096);
        ppg.setPulseAmplitudeRed(0x1F);
        ppg.setPulseAmplitudeIR(0x1F);
        ppgOK = true;
        Serial.println("OK");
    }

    // ── MPU6050 (IMU) ─────────────────────────────────────────
    Serial.print("MPU6050 test ediliyor... ");
    imu.initialize();
    if (!imu.testConnection()) {
        Serial.println("FAIL!");
        Serial.println("  >> VCC=5V, SDA=D4, SCL=D5, AD0=GND baglantilarini kontrol et.");
        Serial.println("  >> I2C adresi: 0x68 (AD0=GND) veya 0x69 (AD0=3.3V)");
    } else {
        imu.setFullScaleAccelRange(MPU6050_ACCEL_FS_2);   // ±2 g
        imu.setFullScaleGyroRange(MPU6050_GYRO_FS_250);   // ±250 °/s
        imuOK = true;
        Serial.println("OK");
    }

    // ── MCP9808 (Sıcaklık) ───────────────────────────────────
    Serial.print("MCP9808 test ediliyor... ");
    if (!tempSensor.begin(0x18)) {
        Serial.println("FAIL!");
        Serial.println("  >> VCC=5V, SDA=D4, SCL=D5, A0=A1=A2=GND baglantilarini kontrol et.");
        Serial.println("  >> I2C adresi: 0x18");
    } else {
        tempSensor.setResolution(3);
        tempSensor.wake();
        tempOK = true;
        Serial.println("OK");
    }

    // ── Özet ──────────────────────────────────────────────────
    Serial.println("----------------------------------------------");
    Serial.print  ("  SONUC: MAX30102="); Serial.print(ppgOK  ? "OK" : "FAIL");
    Serial.print  ("  MPU6050=");         Serial.print(imuOK  ? "OK" : "FAIL");
    Serial.print  ("  MCP9808=");         Serial.println(tempOK ? "OK" : "FAIL");
    Serial.println("==============================================");

    // Hiçbir sensör bulunamazsa I2C tarama yap
    if (!ppgOK && !imuOK && !tempOK) {
        Serial.println();
        Serial.println("[KRITIK] Hicbir sensor bulunamadi!");
        Serial.println("I2C bus uzerindeki tum adresleri tariyorum...");
        scanI2C();
        Serial.println("Program durduruldu. Baglantilari duzeltip tekrar yukle.");
        while (true) { delay(1000); }
    }

    Serial.println();
    Serial.println("Okumalar basliyor (her 500ms)...");
    Serial.println("Sutunlar: [PPG-IR] [PPG-Red] [Sinyal] | [Ax g] [Ay g] [Az g] | [Gx] [Gy] [Gz] dps | [Sicaklik C] | [SQI]");
    Serial.println("----------------------------------------------");
}


// ──────────────────────────────────────────────────────────────
//  LOOP
// ──────────────────────────────────────────────────────────────
void loop() {
    static unsigned long lastPrint = 0;
    static unsigned long lastTemp  = 0;
    static float    latestTemp = 0.0f;
    static uint32_t ir = 0, red = 0;
    static int16_t  ax=0, ay=0, az=0, gx=0, gy=0, gz=0;

    unsigned long now = millis();

    // PPG: önce donanım FIFO'sunu oku, sonra software buffer'dan al
    if (ppgOK) {
        ppg.check();  // hardware FIFO → software buffer
        while (ppg.available()) {
            ir  = ppg.getIR();
            red = ppg.getRed();
            ppg.nextSample();
        }
    }

    // IMU: sürekli oku
    if (imuOK) {
        imu.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);
    }

    // Sıcaklık: 1 Hz
    if (tempOK && (now - lastTemp >= 1000)) {
        float t = tempSensor.readTempC();
        if (!isnan(t)) latestTemp = t;
        lastTemp = now;
    }

    // Ekrana yazdır: 500 ms
    if (now - lastPrint >= 500) {
        lastPrint = now;
        printReadings(ir, red, ax, ay, az, gx, gy, gz, latestTemp);
    }
}


// ──────────────────────────────────────────────────────────────
//  printReadings
// ──────────────────────────────────────────────────────────────
void printReadings(uint32_t ir, uint32_t red,
                   int16_t ax, int16_t ay, int16_t az,
                   int16_t gx, int16_t gy, int16_t gz,
                   float tempC) {

    // PPG sinyal etiketi
    const char* ppgLabel;
    if      (!ppgOK)                ppgLabel = "SENSOR_FAIL";
    else if (ir < PPG_MIN_SIGNAL)   ppgLabel = "CILTTE_DEGIL";
    else if (ir > PPG_GOOD_SIGNAL)  ppgLabel = "IYI_SINYAL  ";
    else                            ppgLabel = "ZAYIF_SINYAL";

    // IMU: gerçek birimlere çevir
    float fAx = ax / 16384.0f;   // ±2g → 16384 LSB/g
    float fAy = ay / 16384.0f;
    float fAz = az / 16384.0f;
    float fGx = gx / 131.0f;     // ±250°/s → 131 LSB/°/s
    float fGy = gy / 131.0f;
    float fGz = gz / 131.0f;

    // SQI hesapla
    float mag      = sqrt(fAx*fAx + fAy*fAy + fAz*fAz);
    float dev      = fabs(mag - 1.0f);
    bool motionOK  = (dev < SQI_MOTION_THR);
    bool sigOK     = (ir  > PPG_MIN_SIGNAL);
    uint8_t sqi    = (motionOK && sigOK) ? 1 : 0;

    // Ana satır
    char buf[200];
    snprintf(buf, sizeof(buf),
        "IR=%-7lu Red=%-7lu [%s] | "
        "Ax=%+.2fg Ay=%+.2fg Az=%+.2fg | "
        "Gx=%+6.1f Gy=%+6.1f Gz=%+6.1f | "
        "T=%.2fC | SQI=%d",
        (unsigned long)ir, (unsigned long)red, ppgLabel,
        fAx, fAy, fAz,
        fGx, fGy, fGz,
        tempC, sqi
    );
    Serial.println(buf);

    // Yardımcı uyarılar
    if (ppgOK && ir < PPG_MIN_SIGNAL) {
        Serial.println("  >> Sensoru bilegin ic tarafina (radial arter ustune) daya.");
    }
    if (imuOK) {
        Serial.print("  >> Ivme buyuklugu=");
        Serial.print(mag, 3);
        Serial.print("g  (hareketsizken ~1.00g beklenir)");
        if (!motionOK) Serial.print("  [HAREKET ALGILANDI]");
        Serial.println();
    }
    if (tempOK && (tempC < 15.0f || tempC > 45.0f)) {
        Serial.println("  >> Sicaklik beklenen aralik disinda (15-45C). Sensoru kontrol et.");
    }
    if (!tempOK) {
        Serial.println("  >> MCP9808 bagli degil.");
    }
    Serial.println();
}


// ──────────────────────────────────────────────────────────────
//  scanI2C — hiçbir sensör bulunmazsa çalışır
// ──────────────────────────────────────────────────────────────
void scanI2C() {
    int found = 0;
    for (uint8_t addr = 1; addr < 127; addr++) {
        Wire.beginTransmission(addr);
        uint8_t err = Wire.endTransmission();
        if (err == 0) {
            Serial.print("  [BULUNDU] 0x");
            if (addr < 16) Serial.print("0");
            Serial.print(addr, HEX);
            // Bilinen adresleri etiketle
            if      (addr == 0x57) Serial.println("  <- MAX30102 (PPG)");
            else if (addr == 0x68) Serial.println("  <- MPU6050 (IMU, AD0=GND)");
            else if (addr == 0x69) Serial.println("  <- MPU6050 (IMU, AD0=3.3V) — AD0'i GND'e bag!");
            else if (addr == 0x18) Serial.println("  <- MCP9808 (Sicaklik)");
            else                   Serial.println("  <- bilinmeyen cihaz");
            found++;
        }
    }
    Serial.println("----------------------------------------------");
    Serial.print("  Toplam "); Serial.print(found); Serial.println(" cihaz bulundu.");
    Serial.println("  Beklenen: 0x57 (MAX30102), 0x68 (MPU6050), 0x18 (MCP9808)");
}