/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           BPSync — Wrist Module Firmware                    ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Board   : Seeed XIAO nRF52840                              ║
 * ║  PIO     : platform=nordicnrf52, board=adafruit_feather_    ║
 * ║            nrf52840  (same chip, Adafruit core workaround)  ║
 * ║  BLE API : Adafruit Bluefruit (built-in, no extra lib)      ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  SENSÖRLER (all I2C — SDA=D4, SCL=D5)                      ║
 * ║    MAX30102  @ 0x57  →  PPG (IR + Red)                     ║
 * ║    MPU6050   @ 0x68  →  Wrist Accelerometer + Gyroscope    ║
 * ║    MCP9808   @ 0x18  →  Skin Temperature                   ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  BLE PERIPHERAL  →  Telefon bağlanır                       ║
 * ║    Service  : 19B10000-E8F2-537E-4F6C-D104768A1214         ║
 * ║    DataChar : 19B10001-…  (Notify, 256 B) — JSON gönderir  ║
 * ║    CmdChar  : 19B10002-…  (Write,   20 B) — START/STOP     ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  BLE CENTRAL  →  Chest modülüne bağlanır                   ║
 * ║    Cihaz adı: "BPSync-Chest"                               ║
 * ║    Service  : 29B10000-E8F2-537E-4F6C-D104768A1214         ║
 * ║    DataChar : 29B10001-…  (Notify) — ChestPacket alır      ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  JSON FRAME (~175 bytes)                                    ║
 * ║  {"ts":ms,"sq":n,                                           ║
 * ║   "pi":IR,"pr":Red,                                         ║
 * ║   "ax":,"ay":,"az":,"gx":,"gy":,"gz":,                     ║
 * ║   "tp":°C,                                                  ║
 * ║   "ep":0|1,                     ← ECG R-peak (chest)       ║
 * ║   "cx":,"cy":,"cz":,            ← Chest IMU (chest)        ║
 * ║   "qi_w":0|1,                   ← Wrist SQI               ║
 * ║   "qi_c":0|1,                   ← Chest SQI (chest)       ║
 * ║   "qi":0|1,                     ← Kombine (qi_w AND qi_c) ║
 * ║   "bt":100}                                                 ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  NOT: Chest bağlı değilken ep/cx/cy/cz/qi_c = 0,           ║
 * ║       qi = qi_w  (wrist-only mod)                           ║
 * ╚══════════════════════════════════════════════════════════════╝
 */

// ──────────────────────────────────────────────────────────────
//  INCLUDES
// ──────────────────────────────────────────────────────────────
#include <Wire.h>
#include <bluefruit.h>
#include <MAX30105.h>
#include <MPU6050.h>
#include <Adafruit_MCP9808.h>

// ──────────────────────────────────────────────────────────────
//  BLE PERIPHERAL — Telefon
// ──────────────────────────────────────────────────────────────
#define DEVICE_NAME        "BPSync-Wrist"
#define WRIST_SERVICE_UUID "19B10000-E8F2-537E-4F6C-D104768A1214"
#define DATA_CHAR_UUID     "19B10001-E8F2-537E-4F6C-D104768A1214"
#define CMD_CHAR_UUID      "19B10002-E8F2-537E-4F6C-D104768A1214"

// ──────────────────────────────────────────────────────────────
//  BLE CENTRAL — Chest Modülü
// ──────────────────────────────────────────────────────────────
#define CHEST_DEVICE_NAME  "BPSync-Chest"
#define CHEST_SERVICE_UUID "29B10000-E8F2-537E-4F6C-D104768A1214"
#define CHEST_DATA_UUID    "29B10001-E8F2-537E-4F6C-D104768A1214"

// ──────────────────────────────────────────────────────────────
//  ÖRNEKLEME ARALIKLARI
// ──────────────────────────────────────────────────────────────
#define PPG_INTERVAL_MS    10      // 100 Hz
#define IMU_INTERVAL_MS    20      //  50 Hz
#define TEMP_INTERVAL_MS   1000    //   1 Hz
#define BLE_TX_INTERVAL_MS 100     //  10 Hz

// ──────────────────────────────────────────────────────────────
//  SQI EŞİKLERİ
// ──────────────────────────────────────────────────────────────
#define SQI_MOTION_THRESHOLD  4096   // ~0.25 g sapma (16384 = 1g)
#define SQI_PPG_MIN           1000   // IR < bu → bilek/parmak yok

// ──────────────────────────────────────────────────────────────
//  CHEST VERİ PAKETİ
//  Bu struct chest firmware ile eşleşmeli (8 byte, packed)
// ──────────────────────────────────────────────────────────────
struct __attribute__((packed)) ChestPacket {
    uint8_t  ep;            // ECG R-peak algılandı (0/1)
    int16_t  cx, cy, cz;   // Chest ivme (ham, ±2g → 16384=1g)
    uint8_t  qi_c;          // Chest SQI (0/1)
};  // Toplam: 8 byte

// ──────────────────────────────────────────────────────────────
//  BLE NESNELERİ
// ──────────────────────────────────────────────────────────────
// Peripheral (telefon)
BLEService        wristSvc(WRIST_SERVICE_UUID);
BLECharacteristic dataChar(DATA_CHAR_UUID);
BLECharacteristic cmdChar(CMD_CHAR_UUID);

// Central (chest modülü)
BLEClientService        chestSvc(CHEST_SERVICE_UUID);
BLEClientCharacteristic chestDataChar(CHEST_DATA_UUID);

// ──────────────────────────────────────────────────────────────
//  SENSÖR NESNELERİ
// ──────────────────────────────────────────────────────────────
MAX30105         ppgSensor;
MPU6050          imu;
Adafruit_MCP9808 tempSensor;

// ──────────────────────────────────────────────────────────────
//  WRIST SENSÖR VERİSİ
// ──────────────────────────────────────────────────────────────
uint32_t ppg_ir  = 0;
uint32_t ppg_red = 0;
int16_t  w_ax = 0, w_ay = 0, w_az = 0;
int16_t  w_gx = 0, w_gy = 0, w_gz = 0;
float    temperature = 0.0f;
uint8_t  qi_w = 0;          // Wrist SQI

// ──────────────────────────────────────────────────────────────
//  CHEST VERİSİ  (chest bağlanınca dolar, yoksa 0)
// ──────────────────────────────────────────────────────────────
uint8_t  ep   = 0;          // ECG R-peak
int16_t  cx   = 0, cy = 0, cz = 0;  // Chest ivme
uint8_t  qi_c = 0;          // Chest SQI

// ──────────────────────────────────────────────────────────────
//  OTURUM DURUMU
// ──────────────────────────────────────────────────────────────
uint16_t seqNum         = 0;
bool     streaming      = false;
bool     chestConnected = false;

// ──────────────────────────────────────────────────────────────
//  ZAMANLAYICILAR
// ──────────────────────────────────────────────────────────────
unsigned long tPPG  = 0;
unsigned long tIMU  = 0;
unsigned long tTemp = 0;
unsigned long tBLE  = 0;

// ──────────────────────────────────────────────────────────────
//  FONKSİYON PROTOTPLER
// ──────────────────────────────────────────────────────────────
void initSensors();
void initBLE();
void readPPG();
void readIMU();
void readTemperature();
void calculateSQI();
void buildJSON(char* buf, int bufSize);
void sendToPhone();

// Peripheral callbacks (telefon)
void periph_connect_callback(uint16_t conn_handle);
void periph_disconnect_callback(uint16_t conn_handle, uint8_t reason);
void onCmdWrite(uint16_t conn_handle, BLECharacteristic* chr,
                uint8_t* data, uint16_t len);

// Central callbacks (chest)
bool adv_has_name(ble_gap_evt_adv_report_t* report, const char* name);
void scan_callback(ble_gap_evt_adv_report_t* report);
void central_connect_callback(uint16_t conn_handle);
void central_disconnect_callback(uint16_t conn_handle, uint8_t reason);
void chest_data_callback(BLEClientCharacteristic* chr,
                         uint8_t* data, uint16_t len);


// ══════════════════════════════════════════════════════════════
//  SETUP
// ══════════════════════════════════════════════════════════════
void setup() {
    Serial.begin(115200);
    delay(500);

    Serial.println("==============================================");
    Serial.println("  BPSync Wrist Module");
    Serial.println("  Seeed XIAO nRF52840");
    Serial.println("==============================================");

    Wire.begin();       // SDA=D4, SCL=D5

    initSensors();
    initBLE();

    Serial.println("[READY] Advertising as '" DEVICE_NAME "'...");
    Serial.println("[READY] Scanning for  '" CHEST_DEVICE_NAME "'...");
    Serial.println("==============================================");
}


// ══════════════════════════════════════════════════════════════
//  LOOP
// ══════════════════════════════════════════════════════════════
void loop() {
    unsigned long now = millis();

    if (now - tPPG >= PPG_INTERVAL_MS) {
        readPPG();
        tPPG = now;
    }

    if (now - tIMU >= IMU_INTERVAL_MS) {
        readIMU();
        tIMU = now;
    }

    if (now - tTemp >= TEMP_INTERVAL_MS) {
        readTemperature();
        tTemp = now;
    }

    calculateSQI();

    if (streaming && Bluefruit.connected() && (now - tBLE >= BLE_TX_INTERVAL_MS)) {
        sendToPhone();
        tBLE = now;
    }
}


// ══════════════════════════════════════════════════════════════
//  initSensors
// ══════════════════════════════════════════════════════════════
void initSensors() {

    // ── MAX30102 (PPG) ───────────────────────────────────────
    if (!ppgSensor.begin(Wire, I2C_SPEED_FAST)) {
        Serial.println("[ERROR] MAX30102 — bulunamadi!"
                       "  Kontrol: VCC=3.3V, SDA=D4, SCL=D5");
    } else {
        ppgSensor.setup(0x1F, 4, 2, 100, 411, 4096);
        ppgSensor.setPulseAmplitudeRed(0x1F);
        ppgSensor.setPulseAmplitudeIR(0x1F);
        Serial.println("[OK]    MAX30102  (PPG)");
    }

    // ── MPU6050 (Wrist IMU) ──────────────────────────────────
    imu.initialize();
    if (!imu.testConnection()) {
        Serial.println("[ERROR] MPU6050 — bulunamadi!"
                       "  Kontrol: VCC=3.3V, SDA=D4, SCL=D5, AD0=GND");
    } else {
        imu.setFullScaleAccelRange(MPU6050_ACCEL_FS_2);   // ±2 g
        imu.setFullScaleGyroRange(MPU6050_GYRO_FS_250);   // ±250 °/s
        Serial.println("[OK]    MPU6050   (Wrist IMU)");
    }

    // ── MCP9808 (Temperature) ────────────────────────────────
    if (!tempSensor.begin(0x18)) {
        Serial.println("[ERROR] MCP9808 — bulunamadi!"
                       "  Kontrol: VCC=3.3V, SDA=D4, SCL=D5, A0=A1=A2=GND");
    } else {
        tempSensor.setResolution(3);
        tempSensor.wake();
        Serial.println("[OK]    MCP9808   (Temperature)");
    }
}


// ══════════════════════════════════════════════════════════════
//  initBLE
//  Bluefruit.begin(1, 1) → 1 peripheral (telefon) + 1 central (chest)
// ══════════════════════════════════════════════════════════════
void initBLE() {
    Bluefruit.begin(1, 1);   // peripheral=1, central=1
    Bluefruit.setName(DEVICE_NAME);
    Bluefruit.setTxPower(4);

    // ── Peripheral: Telefon ───────────────────────────────────
    Bluefruit.Periph.setConnectCallback(periph_connect_callback);
    Bluefruit.Periph.setDisconnectCallback(periph_disconnect_callback);

    wristSvc.begin();

    dataChar.setProperties(CHR_PROPS_NOTIFY);
    dataChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
    dataChar.setMaxLen(256);
    dataChar.begin();

    cmdChar.setProperties(CHR_PROPS_WRITE | CHR_PROPS_WRITE_WO_RESP);
    cmdChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
    cmdChar.setMaxLen(20);
    cmdChar.setWriteCallback(onCmdWrite);
    cmdChar.begin();

    Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
    Bluefruit.Advertising.addTxPower();
    Bluefruit.Advertising.addService(wristSvc);
    Bluefruit.ScanResponse.addName();
    Bluefruit.Advertising.restartOnDisconnect(true);
    Bluefruit.Advertising.setInterval(32, 244);
    Bluefruit.Advertising.setFastTimeout(30);
    Bluefruit.Advertising.start(0);

    Serial.println("[OK]    BLE Peripheral  (telefon — advertising)");

    // ── Central: Chest Modülü ─────────────────────────────────
    Bluefruit.Central.setConnectCallback(central_connect_callback);
    Bluefruit.Central.setDisconnectCallback(central_disconnect_callback);

    chestSvc.begin();

    chestDataChar.begin();
    chestDataChar.setNotifyCallback(chest_data_callback);

    // İsme göre filtrele — UUID filtresi opsiyonel
    Bluefruit.Scanner.setRxCallback(scan_callback);
    Bluefruit.Scanner.restartOnDisconnect(true);
    Bluefruit.Scanner.setInterval(160, 80);  // 100ms interval, 50ms window
    Bluefruit.Scanner.useActiveScan(false);
    Bluefruit.Scanner.start(0);              // 0 = süresiz tara

    Serial.println("[OK]    BLE Central     (chest — scanning)");
}


// ══════════════════════════════════════════════════════════════
//  PERİPHERAL CALLBACKS — Telefon
// ══════════════════════════════════════════════════════════════
void periph_connect_callback(uint16_t conn_handle) {
    BLEConnection* conn = Bluefruit.Connection(conn_handle);
    char peer[32] = {0};
    conn->getPeerName(peer, sizeof(peer));

    Serial.print("[PHONE] Bağlandı: ");
    Serial.println(peer[0] ? peer : "(bilinmiyor)");

    streaming = true;
    seqNum    = 0;
}

void periph_disconnect_callback(uint16_t conn_handle, uint8_t reason) {
    (void)conn_handle;
    (void)reason;
    streaming = false;
    Serial.println("[PHONE] Bağlantı kesildi — yeniden advertising...");
}

void onCmdWrite(uint16_t conn_handle, BLECharacteristic* chr,
                uint8_t* data, uint16_t len) {
    (void)conn_handle;
    (void)chr;

    char cmd[21];
    uint16_t n = (len < 20) ? len : 20;
    memcpy(cmd, data, n);
    cmd[n] = '\0';

    Serial.print("[CMD] ");
    Serial.println(cmd);

    if (strcmp(cmd, "START") == 0) {
        streaming = true;
        seqNum    = 0;
        Serial.println("[SESSION] Streaming basladi");
    } else if (strcmp(cmd, "STOP") == 0) {
        streaming = false;
        Serial.println("[SESSION] Streaming durdu");
    } else {
        Serial.print("[CMD] Bilinmiyor: ");
        Serial.println(cmd);
    }
}


// ══════════════════════════════════════════════════════════════
//  CENTRAL CALLBACKS — Chest Modülü
// ══════════════════════════════════════════════════════════════

// BLE reklam paketinde cihaz adını manuel ara
// AD yapısı: [length][type][data...] — isim tipleri 0x08 ve 0x09
bool adv_has_name(ble_gap_evt_adv_report_t* report, const char* name) {
    const uint8_t* data = report->data.p_data;
    uint16_t       dlen = report->data.len;
    size_t         nlen = strlen(name);

    for (uint16_t i = 0; i + 1 < dlen; ) {
        uint8_t adLen  = data[i];
        uint8_t adType = data[i + 1];
        if (adLen == 0) break;
        if ((adType == 0x08 || adType == 0x09) &&
            (uint16_t)(adLen - 1) == nlen &&
            memcmp(&data[i + 2], name, nlen) == 0) {
            return true;
        }
        i += adLen + 1;
    }
    return false;
}

// Scan callback: reklam paketi gelince isim kontrol edilir
void scan_callback(ble_gap_evt_adv_report_t* report) {
    if (adv_has_name(report, CHEST_DEVICE_NAME)) {
        Serial.println("[CHEST] BPSync-Chest bulundu — baglaniliyor...");
        Bluefruit.Central.connect(report);
    } else {
        Bluefruit.Scanner.resume();   // başka cihaz, taramaya devam
    }
}

// Chest'e bağlanınca: servis + karakteristik keşfet, notify aç
void central_connect_callback(uint16_t conn_handle) {
    Serial.println("[CHEST] Bağlandi — servis kesfediliyor...");

    if (!chestSvc.discover(conn_handle)) {
        Serial.println("[CHEST] Servis bulunamadi — baglanti kesiliyor");
        Bluefruit.disconnect(conn_handle);
        return;
    }

    if (!chestDataChar.discover()) {
        Serial.println("[CHEST] Karakteristik bulunamadi — baglanti kesiliyor");
        Bluefruit.disconnect(conn_handle);
        return;
    }

    if (!chestDataChar.enableNotify()) {
        Serial.println("[CHEST] Notify açilamadi — baglanti kesiliyor");
        Bluefruit.disconnect(conn_handle);
        return;
    }

    chestConnected = true;
    Serial.println("[CHEST] Hazir — ECG + Chest IMU aliniyor");
}

// Chest bağlantısı kopunca: verileri sıfırla, scanner otomatik başlar
void central_disconnect_callback(uint16_t conn_handle, uint8_t reason) {
    (void)conn_handle;
    (void)reason;
    chestConnected = false;
    ep = 0;  cx = 0;  cy = 0;  cz = 0;  qi_c = 0;
    Serial.println("[CHEST] Baglanti kesildi — yeniden tarama...");
}

// Chest'ten veri gelince: ChestPacket'i parse et
void chest_data_callback(BLEClientCharacteristic* chr,
                         uint8_t* data, uint16_t len) {
    (void)chr;
    if (len >= (uint16_t)sizeof(ChestPacket)) {
        ChestPacket* pkt = (ChestPacket*)data;
        ep   = pkt->ep;
        cx   = pkt->cx;
        cy   = pkt->cy;
        cz   = pkt->cz;
        qi_c = pkt->qi_c;
    }
}


// ══════════════════════════════════════════════════════════════
//  SENSÖR OKUMA FONKSİYONLARI
// ══════════════════════════════════════════════════════════════
void readPPG() {
    if (ppgSensor.available()) {
        ppg_ir  = ppgSensor.getIR();
        ppg_red = ppgSensor.getRed();
        ppgSensor.nextSample();
    }
}

void readIMU() {
    imu.getMotion6(&w_ax, &w_ay, &w_az, &w_gx, &w_gy, &w_gz);
}

void readTemperature() {
    float t = tempSensor.readTempC();
    if (!isnan(t)) temperature = t;
}


// ══════════════════════════════════════════════════════════════
//  calculateSQI — Sinyal Kalite İndeksi
//
//  qi_w  : Wrist  — PPG sinyali var mı + bilek sabit mi
//  qi_c  : Chest  — Chest modülünden gelir (ChestPacket.qi_c)
//  qi    : Kombine = qi_w AND qi_c
//          Chest bağlı değilse: qi = qi_w  (wrist-only mod)
// ══════════════════════════════════════════════════════════════
void calculateSQI() {
    float mag       = sqrt((float)w_ax * w_ax +
                           (float)w_ay * w_ay +
                           (float)w_az * w_az);
    float deviation = fabs(mag - 16384.0f);

    bool motionOK = (deviation < SQI_MOTION_THRESHOLD);
    bool ppgOK    = (ppg_ir   > SQI_PPG_MIN);

    qi_w = (motionOK && ppgOK) ? 1 : 0;
    // qi_c: chest callback'ten geliyor, burada hesaplanmıyor
}


// ══════════════════════════════════════════════════════════════
//  buildJSON
//  qi_w / qi_c / qi üç ayrı alanda → backend ve ML için
//  Chest bağlı değilken: ep=0 cx=cy=cz=0 qi_c=0 qi=qi_w
// ══════════════════════════════════════════════════════════════
void buildJSON(char* buf, int bufSize) {
    uint8_t qi_combined = chestConnected ? (qi_w & qi_c) : qi_w;

    snprintf(buf, bufSize,
        "{"
        "\"ts\":%lu,"
        "\"sq\":%u,"
        "\"pi\":%lu,"
        "\"pr\":%lu,"
        "\"ax\":%d,\"ay\":%d,\"az\":%d,"
        "\"gx\":%d,\"gy\":%d,\"gz\":%d,"
        "\"tp\":%.1f,"
        "\"ep\":%u,"
        "\"cx\":%d,\"cy\":%d,\"cz\":%d,"
        "\"qi_w\":%u,\"qi_c\":%u,\"qi\":%u,"
        "\"bt\":100"
        "}",
        (unsigned long)millis(),
        (unsigned int)seqNum,
        (unsigned long)ppg_ir,
        (unsigned long)ppg_red,
        (int)w_ax, (int)w_ay, (int)w_az,
        (int)w_gx, (int)w_gy, (int)w_gz,
        temperature,
        (unsigned int)ep,
        (int)cx, (int)cy, (int)cz,
        (unsigned int)qi_w,
        (unsigned int)qi_c,
        (unsigned int)qi_combined
    );
}


// ══════════════════════════════════════════════════════════════
//  sendToPhone — JSON'u BLE Notify ile gönder
// ══════════════════════════════════════════════════════════════
void sendToPhone() {
    char json[256];
    buildJSON(json, sizeof(json));

    dataChar.notify((uint8_t*)json, strlen(json));
    seqNum++;

    // Her 50 frame'de bir (~5 sn) Serial'a yaz
    if (seqNum % 50 == 0) {
        Serial.print("[TX #");
        Serial.print(seqNum);
        Serial.print("] chest=");
        Serial.print(chestConnected ? "OK" : "--");
        Serial.print(" | ");
        Serial.println(json);
    }
}
