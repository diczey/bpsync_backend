/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           BPSync — Wrist Module Firmware                    ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Board   : Seeed XIAO nRF52840                              ║
 * ║  PIO     : platform=nordicnrf52, board=adafruit_feather_    ║
 * ║            nrf52840  (same chip, Adafruit core workaround)  ║
 * ║  BLE API : Adafruit Bluefruit (built-in, no extra lib)      ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  SENSORS (all I2C — SDA=D4, SCL=D5)                        ║
 * ║    MAX30102  @ 0x57  →  PPG (IR + Red)                     ║
 * ║    MPU6050   @ 0x68  →  Wrist Accelerometer + Gyroscope    ║
 * ║    MCP9808   @ 0x18  →  Skin Temperature                   ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  HARDWARE I/O (GPIO)                                        ║
 * ║    LED @ D3  →  Status indicator (4 blink modes)           ║
 * ║               Slow blink : advertising (no phone)           ║
 * ║               Fast blink : phone connected, idle            ║
 * ║               Solid ON   : streaming + good signal          ║
 * ║               Double blink : streaming + poor signal        ║
 * ║    BTN @ D2  →  Short press : START / STOP streaming        ║
 * ║               Long press (3 s) : BLE reset + re-advertise  ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  BLE PERIPHERAL  →  Phone connects                          ║
 * ║    Service  : 19B10000-E8F2-537E-4F6C-D104768A1214         ║
 * ║    DataChar : 19B10001-…  (Notify, 256 B) — sends JSON     ║
 * ║    CmdChar  : 19B10002-…  (Write,   20 B) — START/STOP     ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  BLE CENTRAL  →  Connects to Chest module                   ║
 * ║    Device name: "BPSync-Chest"                              ║
 * ║    Service  : 29B10000-E8F2-537E-4F6C-D104768A1214         ║
 * ║    DataChar : 29B10001-…  (Notify) — receives ChestPacket  ║
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
 * ║   "qi":0|1,                     ← Combined (qi_w AND qi_c) ║
 * ║   "bt":100}                                                 ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  NOTE: When Chest is not connected ep/cx/cy/cz/qi_c = 0,   ║
 * ║        qi = qi_w  (wrist-only mode)                         ║
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
//  BLE PERIPHERAL — Phone
// ──────────────────────────────────────────────────────────────
#define DEVICE_NAME        "BPSync-Wrist"
#define WRIST_SERVICE_UUID "19B10000-E8F2-537E-4F6C-D104768A1214"
#define DATA_CHAR_UUID     "19B10001-E8F2-537E-4F6C-D104768A1214"
#define CMD_CHAR_UUID      "19B10002-E8F2-537E-4F6C-D104768A1214"

// ──────────────────────────────────────────────────────────────
//  BLE CENTRAL — Chest Module
// ──────────────────────────────────────────────────────────────
#define CHEST_DEVICE_NAME  "BPSync-Chest"
#define CHEST_SERVICE_UUID "29B10000-E8F2-537E-4F6C-D104768A1214"
#define CHEST_DATA_UUID    "29B10001-E8F2-537E-4F6C-D104768A1214"

// ──────────────────────────────────────────────────────────────
//  HARDWARE PINS
// ──────────────────────────────────────────────────────────────
#define LED_PIN   D3   // Status LED
#define BTN_PIN   D2   // User button (INPUT_PULLUP)

// ──────────────────────────────────────────────────────────────
//  SAMPLING INTERVALS
// ──────────────────────────────────────────────────────────────
#define PPG_INTERVAL_MS    10      // 100 Hz
#define IMU_INTERVAL_MS    20      //  50 Hz
#define TEMP_INTERVAL_MS   1000    //   1 Hz
#define BLE_TX_INTERVAL_MS 100     //  10 Hz

// ──────────────────────────────────────────────────────────────
//  SQI THRESHOLDS
// ──────────────────────────────────────────────────────────────
#define SQI_MOTION_THRESHOLD  4096   // ~0.25 g deviation (16384 = 1g)
#define SQI_PPG_MIN           3000   // IR < this → no wrist contact

// ──────────────────────────────────────────────────────────────
//  CHEST DATA PACKET
//  Must match chest firmware struct (8 byte, packed)
// ──────────────────────────────────────────────────────────────
struct __attribute__((packed)) ChestPacket {
    uint8_t  ep;            // ECG R-peak detected (0/1)
    int16_t  cx, cy, cz;   // Chest acceleration (raw, ±2g → 16384=1g)
    uint8_t  qi_c;          // Chest SQI (0/1)
};  // Total: 8 bytes

// ──────────────────────────────────────────────────────────────
//  BLE OBJECTS
// ──────────────────────────────────────────────────────────────
// Peripheral (phone)
BLEService        wristSvc(WRIST_SERVICE_UUID);
BLECharacteristic dataChar(DATA_CHAR_UUID);
BLECharacteristic cmdChar(CMD_CHAR_UUID);

// Central (chest module)
BLEClientService        chestSvc(CHEST_SERVICE_UUID);
BLEClientCharacteristic chestDataChar(CHEST_DATA_UUID);

// ──────────────────────────────────────────────────────────────
//  SENSOR OBJECTS
// ──────────────────────────────────────────────────────────────
MAX30105         ppgSensor;
MPU6050          imu;
Adafruit_MCP9808 tempSensor;

// ──────────────────────────────────────────────────────────────
//  WRIST SENSOR DATA
// ──────────────────────────────────────────────────────────────
uint32_t ppg_ir  = 0;
uint32_t ppg_red = 0;
int16_t  w_ax = 0, w_ay = 0, w_az = 0;
int16_t  w_gx = 0, w_gy = 0, w_gz = 0;
float    temperature = 0.0f;
uint8_t  qi_w = 0;          // Wrist SQI

// ──────────────────────────────────────────────────────────────
//  CHEST DATA  (filled when chest connects, else 0)
// ──────────────────────────────────────────────────────────────
uint8_t  ep   = 0;          // ECG R-peak
int16_t  cx   = 0, cy = 0, cz = 0;  // Chest acceleration
uint8_t  qi_c = 0;          // Chest SQI

// ──────────────────────────────────────────────────────────────
//  SESSION STATE
// ──────────────────────────────────────────────────────────────
uint16_t seqNum         = 0;
bool     streaming      = false;
bool     chestConnected = false;

// ──────────────────────────────────────────────────────────────
//  LED STATE MACHINE
// ──────────────────────────────────────────────────────────────
enum LedMode {
    LED_BLINK_SLOW,   // Advertising — 1 Hz
    LED_BLINK_FAST,   // Connected but not streaming — 4 Hz
    LED_SOLID,        // Streaming + good signal
    LED_BLINK_SQI,    // Streaming + poor signal — double blink
};

LedMode       ledMode       = LED_BLINK_SLOW;
unsigned long tLed          = 0;
bool          ledState      = false;
uint8_t       sqiBlinkPhase = 0;   // phase counter for double blink

// ──────────────────────────────────────────────────────────────
//  BUTTON DEBOUNCE & LONG PRESS
// ──────────────────────────────────────────────────────────────
#define BTN_DEBOUNCE_MS   50
#define BTN_LONG_PRESS_MS 3000

bool          btnLastRaw  = HIGH;  // pullup → normally HIGH
bool          btnStable   = HIGH;
unsigned long tBtnChange  = 0;    // last raw change timestamp
unsigned long tBtnPressed = 0;    // stable LOW transition timestamp
bool          btnHandled  = false; // ensures long press fires only once

// ──────────────────────────────────────────────────────────────
//  TIMERS
// ──────────────────────────────────────────────────────────────
unsigned long tPPG  = 0;
unsigned long tIMU  = 0;
unsigned long tTemp = 0;
unsigned long tBLE  = 0;

// ──────────────────────────────────────────────────────────────
//  FUNCTION PROTOTYPES
// ──────────────────────────────────────────────────────────────
void initSensors();
void initBLE();
void initHardware();
void readPPG();
void readIMU();
void readTemperature();
void calculateSQI();
void buildJSON(char* buf, int bufSize);
void sendToPhone();
void updateLED();
void handleButton();
void setLedMode(LedMode mode);

// Peripheral callbacks (phone)
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

    initHardware();
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

    handleButton();
    updateLED();
}


// ══════════════════════════════════════════════════════════════
//  initHardware — LED and Button pin setup
// ══════════════════════════════════════════════════════════════
void initHardware() {
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);

    pinMode(BTN_PIN, INPUT_PULLUP);

    Serial.println("[OK]    LED (D3) + Button (D2)");
}


// ══════════════════════════════════════════════════════════════
//  setLedMode — change LED mode (called from outside)
// ══════════════════════════════════════════════════════════════
void setLedMode(LedMode mode) {
    if (ledMode == mode) return;
    ledMode       = mode;
    sqiBlinkPhase = 0;
    tLed          = 0;   // switch to new mode immediately
}


// ══════════════════════════════════════════════════════════════
//  updateLED — called every loop, non-blocking blink
//
//  LED_BLINK_SLOW : 500 ms ON / 500 ms OFF  (1 Hz) — advertising
//  LED_BLINK_FAST : 125 ms ON / 125 ms OFF  (4 Hz) — connected, idle
//  LED_SOLID      : always ON               — streaming + good signal
//  LED_BLINK_SQI  : double blink + long OFF — streaming + poor signal
//                   ON 80ms / OFF 80ms / ON 80ms / OFF 760ms
// ══════════════════════════════════════════════════════════════
void updateLED() {
    unsigned long now = millis();

    switch (ledMode) {

        case LED_BLINK_SLOW:
            if (now - tLed >= 500) {
                ledState = !ledState;
                digitalWrite(LED_PIN, ledState ? HIGH : LOW);
                tLed = now;
            }
            break;

        case LED_BLINK_FAST:
            if (now - tLed >= 125) {
                ledState = !ledState;
                digitalWrite(LED_PIN, ledState ? HIGH : LOW);
                tLed = now;
            }
            break;

        case LED_SOLID:
            digitalWrite(LED_PIN, HIGH);
            ledState = true;
            break;

        case LED_BLINK_SQI: {
            // Phase: 0=1st ON  1=1st OFF  2=2nd ON  3=long OFF
            static const uint16_t sqiTiming[4] = {80, 80, 80, 760};
            if (now - tLed >= sqiTiming[sqiBlinkPhase]) {
                sqiBlinkPhase = (sqiBlinkPhase + 1) % 4;
                bool on = (sqiBlinkPhase == 0 || sqiBlinkPhase == 2);
                digitalWrite(LED_PIN, on ? HIGH : LOW);
                tLed = now;
            }
            break;
        }
    }
}


// ══════════════════════════════════════════════════════════════
//  handleButton — called every loop
//
//  Short press (< 3 s) : Streaming toggle (START / STOP)
//  Long press  (≥ 3 s) : Disconnect BLE, re-advertise
// ══════════════════════════════════════════════════════════════
void handleButton() {
    unsigned long now = millis();
    bool          raw = digitalRead(BTN_PIN);   // LOW = pressed (pullup)

    // Debounce: consider stable if raw unchanged for 50 ms
    if (raw != btnLastRaw) {
        btnLastRaw = raw;
        tBtnChange = now;
    }

    if ((now - tBtnChange) < BTN_DEBOUNCE_MS) return;  // not yet stable

    // New stable LOW → press started
    if (raw == LOW && btnStable == HIGH) {
        btnStable   = LOW;
        tBtnPressed = now;
        btnHandled  = false;
    }

    // Check for long press while held down
    if (raw == LOW && !btnHandled) {
        if ((now - tBtnPressed) >= BTN_LONG_PRESS_MS) {
            btnHandled = true;
            Serial.println("[BTN] Long press — resetting BLE...");
            Bluefruit.disconnect(Bluefruit.connHandle());
            streaming = false;
            setLedMode(LED_BLINK_SLOW);
        }
    }

    // Release → trigger short press (skip if long press already handled)
    if (raw == HIGH && btnStable == LOW) {
        btnStable = HIGH;
        if (!btnHandled) {
            if (streaming) {
                streaming = false;
                setLedMode(Bluefruit.connected() ? LED_BLINK_FAST : LED_BLINK_SLOW);
                Serial.println("[BTN] Short press — Streaming STOPPED");
            } else {
                if (Bluefruit.connected()) {
                    streaming = true;
                    seqNum    = 0;
                    setLedMode(LED_SOLID);
                    Serial.println("[BTN] Short press — Streaming STARTED");
                } else {
                    Serial.println("[BTN] Short press — phone not connected, streaming not started");
                }
            }
        }
    }
}


// ══════════════════════════════════════════════════════════════
//  initSensors
// ══════════════════════════════════════════════════════════════
void initSensors() {

    // ── MAX30102 (PPG) ───────────────────────────────────────
    if (!ppgSensor.begin(Wire, I2C_SPEED_FAST)) {
        Serial.println("[ERROR] MAX30102 — not found!"
                       "  Check: VCC=3.3V, SDA=D4, SCL=D5");
    } else {
        ppgSensor.setup(0x1F, 4, 2, 100, 411, 4096);
        ppgSensor.setPulseAmplitudeRed(0x1F);
        ppgSensor.setPulseAmplitudeIR(0x1F);
        Serial.println("[OK]    MAX30102  (PPG)");
    }

    // ── MPU6050 (Wrist IMU) ──────────────────────────────────
    imu.initialize();
    if (!imu.testConnection()) {
        Serial.println("[ERROR] MPU6050 — not found!"
                       "  Check: VCC=3.3V, SDA=D4, SCL=D5, AD0=GND");
    } else {
        imu.setFullScaleAccelRange(MPU6050_ACCEL_FS_2);   // ±2 g
        imu.setFullScaleGyroRange(MPU6050_GYRO_FS_250);   // ±250 °/s
        Serial.println("[OK]    MPU6050   (Wrist IMU)");
    }

    // ── MCP9808 (Temperature) ────────────────────────────────
    if (!tempSensor.begin(0x18)) {
        Serial.println("[ERROR] MCP9808 — not found!"
                       "  Check: VCC=3.3V, SDA=D4, SCL=D5, A0=A1=A2=GND");
    } else {
        tempSensor.setResolution(3);
        tempSensor.wake();
        Serial.println("[OK]    MCP9808   (Temperature)");
    }
}


// ══════════════════════════════════════════════════════════════
//  initBLE
//  Bluefruit.begin(1, 1) → 1 peripheral (phone) + 1 central (chest)
// ══════════════════════════════════════════════════════════════
void initBLE() {
    Bluefruit.begin(1, 1);   // peripheral=1, central=1
    Bluefruit.setName(DEVICE_NAME);
    Bluefruit.setTxPower(4);

    // ── Peripheral: Phone ─────────────────────────────────────
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

    Serial.println("[OK]    BLE Peripheral  (phone — advertising)");

    // ── Central: Chest Module ─────────────────────────────────
    Bluefruit.Central.setConnectCallback(central_connect_callback);
    Bluefruit.Central.setDisconnectCallback(central_disconnect_callback);

    chestSvc.begin();

    chestDataChar.begin();
    chestDataChar.setNotifyCallback(chest_data_callback);

    // Filter by name — UUID filter optional
    Bluefruit.Scanner.setRxCallback(scan_callback);
    Bluefruit.Scanner.restartOnDisconnect(true);
    Bluefruit.Scanner.setInterval(160, 80);  // 100ms interval, 50ms window
    Bluefruit.Scanner.useActiveScan(false);
    Bluefruit.Scanner.start(0);              // 0 = scan indefinitely

    Serial.println("[OK]    BLE Central     (chest — scanning)");
}


// ══════════════════════════════════════════════════════════════
//  PERIPHERAL CALLBACKS — Phone
// ══════════════════════════════════════════════════════════════
void periph_connect_callback(uint16_t conn_handle) {
    BLEConnection* conn = Bluefruit.Connection(conn_handle);
    char peer[32] = {0};
    conn->getPeerName(peer, sizeof(peer));

    Serial.print("[PHONE] Connected: ");
    Serial.println(peer[0] ? peer : "(unknown)");

    streaming = true;
    seqNum    = 0;
    setLedMode(LED_SOLID);
}

void periph_disconnect_callback(uint16_t conn_handle, uint8_t reason) {
    (void)conn_handle;
    (void)reason;
    streaming = false;
    setLedMode(LED_BLINK_SLOW);
    Serial.println("[PHONE] Disconnected — re-advertising...");
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
        Serial.println("[SESSION] Streaming started");
    } else if (strcmp(cmd, "STOP") == 0) {
        streaming = false;
        Serial.println("[SESSION] Streaming stopped");
    } else {
        Serial.print("[CMD] Unknown command: ");
        Serial.println(cmd);
    }
}


// ══════════════════════════════════════════════════════════════
//  CENTRAL CALLBACKS — Chest Module
// ══════════════════════════════════════════════════════════════

// Manually search for device name in BLE advertisement packet
// AD structure: [length][type][data...] — name types 0x08 and 0x09
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

// Scan callback: check device name on each advertisement
void scan_callback(ble_gap_evt_adv_report_t* report) {
    if (adv_has_name(report, CHEST_DEVICE_NAME)) {
        Serial.println("[CHEST] BPSync-Chest found — connecting...");
        Bluefruit.Central.connect(report);
    } else {
        Bluefruit.Scanner.resume();   // other device, continue scanning
    }
}

// On chest connect: discover service + characteristic, enable notify
void central_connect_callback(uint16_t conn_handle) {
    Serial.println("[CHEST] Connected — discovering services...");

    if (!chestSvc.discover(conn_handle)) {
        Serial.println("[CHEST] Service not found — disconnecting");
        Bluefruit.disconnect(conn_handle);
        return;
    }

    if (!chestDataChar.discover()) {
        Serial.println("[CHEST] Characteristic not found — disconnecting");
        Bluefruit.disconnect(conn_handle);
        return;
    }

    if (!chestDataChar.enableNotify()) {
        Serial.println("[CHEST] Failed to enable notify — disconnecting");
        Bluefruit.disconnect(conn_handle);
        return;
    }

    chestConnected = true;
    Serial.println("[CHEST] Ready — receiving ECG + Chest IMU");
}

// On chest disconnect: reset data, scanner restarts automatically
void central_disconnect_callback(uint16_t conn_handle, uint8_t reason) {
    (void)conn_handle;
    (void)reason;
    chestConnected = false;
    ep = 0;  cx = 0;  cy = 0;  cz = 0;  qi_c = 0;
    Serial.println("[CHEST] Disconnected — rescanning...");
}

// On chest data received: parse ChestPacket
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
//  SENSOR READ FUNCTIONS
// ══════════════════════════════════════════════════════════════
void readPPG() {
    ppgSensor.check();  // hardware FIFO → software buffer
    while (ppgSensor.available()) {
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
//  calculateSQI — Signal Quality Index
//
//  qi_w  : Wrist  — PPG signal present + wrist steady
//  qi_c  : Chest  — received from Chest module (ChestPacket.qi_c)
//  qi    : Combined = qi_w AND qi_c
//          If Chest not connected: qi = qi_w  (wrist-only mode)
// ══════════════════════════════════════════════════════════════
void calculateSQI() {
    float mag       = sqrt((float)w_ax * w_ax +
                           (float)w_ay * w_ay +
                           (float)w_az * w_az);
    float deviation = fabs(mag - 16384.0f);

    bool motionOK = (deviation < SQI_MOTION_THRESHOLD);
    bool ppgOK    = (ppg_ir   > SQI_PPG_MIN);

    qi_w = (motionOK && ppgOK) ? 1 : 0;
    // qi_c: comes from chest callback, not computed here

    // Update LED based on signal quality when streaming
    if (streaming && Bluefruit.connected()) {
        setLedMode(qi_w ? LED_SOLID : LED_BLINK_SQI);
    }
}


// ══════════════════════════════════════════════════════════════
//  buildJSON
//  qi_w / qi_c / qi in three separate fields → for backend and ML
//  When Chest not connected: ep=0 cx=cy=cz=0 qi_c=0 qi=qi_w
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
//  sendToPhone — send JSON via BLE Notify
// ══════════════════════════════════════════════════════════════
void sendToPhone() {
    char json[256];
    buildJSON(json, sizeof(json));

    dataChar.notify((uint8_t*)json, strlen(json));
    seqNum++;

    // Print to Serial every 50 frames (~5 s)
    if (seqNum % 50 == 0) {
        Serial.print("[TX #");
        Serial.print(seqNum);
        Serial.print("] chest=");
        Serial.print(chestConnected ? "OK" : "--");
        Serial.print(" | ");
        Serial.println(json);
    }
}
