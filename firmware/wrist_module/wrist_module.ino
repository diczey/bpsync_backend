/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           BPSync — Wrist Module Firmware                    ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Board   : Seeed XIAO nRF52840                              ║
 * ║  Board pkg: Seeed nRF52 Boards (Arduino IDE)                ║
 * ║  BLE API : ArduinoBLE (Peripheral + Central, time-sliced)   ║
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
 * ║    Device name  : "BPSync-Wrist"                            ║
 * ║    Service  : 19B10000-E8F2-537E-4F6C-D104768A1214         ║
 * ║    DataChar : 19B10001-…  (Notify, 256 B) — sends JSON     ║
 * ║    CmdChar  : 19B10002-…  (Write,   20 B) — START/STOP     ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  BLE CENTRAL  →  Connects to Chest module                   ║
 * ║    Device name: "BPSync-Chest"                              ║
 * ║    Service  : 29B10000-E8F2-537E-4F6C-D104768A1214         ║
 * ║    DataChar : 29B10001-…  (Notify) — receives ChestPacket  ║
 * ║    NOTE: ArduinoBLE switches between Peripheral and Central  ║
 * ║          roles in time-slices (scan when phone not active)  ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  JSON FRAME (~175 bytes, sent every 100 ms)                 ║
 * ║  {"ts":ms,"sq":n,                                           ║
 * ║   "pi":IR,"pr":Red,                                         ║
 * ║   "ax":,"ay":,"az":,"gx":,"gy":,"gz":,                     ║
 * ║   "tp":°C,                                                  ║
 * ║   "ep":0|1,        ← ECG R-peak (chest)                    ║
 * ║   "cx":,"cy":,"cz":, ← Chest IMU (chest)                  ║
 * ║   "qi_w":0|1,      ← Wrist SQI                             ║
 * ║   "qi_c":0|1,      ← Chest SQI (chest)                    ║
 * ║   "qi":0|1,        ← Combined (qi_w AND qi_c)              ║
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
#include <ArduinoBLE.h>
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
//  BLE CENTRAL SCAN — Chest scan happens between TX windows
// ──────────────────────────────────────────────────────────────
#define CHEST_SCAN_INTERVAL_MS  5000   // Try connecting chest every 5 s
#define CHEST_SCAN_DURATION_MS  500    // Each scan window is 500 ms

// ──────────────────────────────────────────────────────────────
//  SQI THRESHOLDS
// ──────────────────────────────────────────────────────────────
#define SQI_MOTION_THRESHOLD  4096   // ~0.25 g deviation (16384 = 1g)
#define SQI_PPG_MIN           10000  // IR < this → no wrist contact

// ──────────────────────────────────────────────────────────────
//  CHEST DATA PACKET — must match chest firmware (8 bytes packed)
// ──────────────────────────────────────────────────────────────
struct __attribute__((packed)) ChestPacket {
    uint8_t  ep;            // ECG R-peak detected (0/1)
    int16_t  cx, cy, cz;   // Chest acceleration (raw, ±2g → 16384=1g)
    uint8_t  qi_c;          // Chest SQI (0/1)
};  // Total: 8 bytes

// ──────────────────────────────────────────────────────────────
//  BLE OBJECTS — Peripheral (phone)
// ──────────────────────────────────────────────────────────────
BLEService        wristSvc(WRIST_SERVICE_UUID);
BLECharacteristic dataChar(DATA_CHAR_UUID, BLENotify, 244);
BLECharacteristic cmdChar(CMD_CHAR_UUID,  BLEWrite | BLEWriteWithoutResponse, 20);

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
uint8_t  qi_w = 0;

// ──────────────────────────────────────────────────────────────
//  CHEST DATA  (zeroed until chest connects)
// ──────────────────────────────────────────────────────────────
uint8_t  ep   = 0;
int16_t  cx   = 0, cy = 0, cz = 0;
uint8_t  qi_c = 0;
bool     chestConnected = false;

// ──────────────────────────────────────────────────────────────
//  SESSION STATE
// ──────────────────────────────────────────────────────────────
uint16_t seqNum    = 0;
bool     streaming = false;

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
uint8_t       sqiBlinkPhase = 0;

// ──────────────────────────────────────────────────────────────
//  BUTTON DEBOUNCE & LONG PRESS
// ──────────────────────────────────────────────────────────────
#define BTN_DEBOUNCE_MS   50
#define BTN_LONG_PRESS_MS 3000

bool          btnLastRaw  = HIGH;
bool          btnStable   = HIGH;
unsigned long tBtnChange  = 0;
unsigned long tBtnPressed = 0;
bool          btnHandled  = false;

// ──────────────────────────────────────────────────────────────
//  TIMERS
// ──────────────────────────────────────────────────────────────
unsigned long tPPG       = 0;
unsigned long tIMU       = 0;
unsigned long tTemp      = 0;
unsigned long tBLE       = 0;
unsigned long tChestScan = 0;

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
void scanForChest();
void connectToChest(BLEDevice& device);
void readChestData(BLEDevice& chest);

// BLE event callbacks
void onBLEConnect(BLEDevice central);
void onBLEDisconnect(BLEDevice central);
void onCmdWrite(BLEDevice central, BLECharacteristic characteristic);


// ══════════════════════════════════════════════════════════════
//  SETUP
// ══════════════════════════════════════════════════════════════
void setup() {
    Serial.begin(115200);
    delay(500);

    Serial.println("==============================================");
    Serial.println("  BPSync Wrist Module");
    Serial.println("  Seeed XIAO nRF52840  |  ArduinoBLE");
    Serial.println("==============================================");

    Wire.begin();       // SDA=D4, SCL=D5

    initHardware();
    initSensors();
    initBLE();

    Serial.println("[READY] Advertising as '" DEVICE_NAME "'...");
    Serial.println("[READY] Will scan for  '" CHEST_DEVICE_NAME "' periodically...");
    Serial.println("==============================================");
}


// ══════════════════════════════════════════════════════════════
//  LOOP
// ══════════════════════════════════════════════════════════════
void loop() {
    BLE.poll();   // ArduinoBLE requires polling every loop

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

    if (streaming && BLE.connected() && (now - tBLE >= BLE_TX_INTERVAL_MS)) {
        sendToPhone();
        tBLE = now;
    }

    // Scan for chest module when phone is not actively streaming
    // (avoids BLE contention during active data transmission)
    if (!chestConnected && !streaming && (now - tChestScan >= CHEST_SCAN_INTERVAL_MS)) {
        tChestScan = now;
        scanForChest();
    }

    handleButton();
    updateLED();
}


// ══════════════════════════════════════════════════════════════
//  initHardware
// ══════════════════════════════════════════════════════════════
void initHardware() {
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);
    pinMode(BTN_PIN, INPUT_PULLUP);
    Serial.println("[OK]    LED (D3) + Button (D2)");
}


// ══════════════════════════════════════════════════════════════
//  setLedMode
// ══════════════════════════════════════════════════════════════
void setLedMode(LedMode mode) {
    if (ledMode == mode) return;
    ledMode       = mode;
    sqiBlinkPhase = 0;
    tLed          = 0;
}


// ══════════════════════════════════════════════════════════════
//  updateLED
//
//  LED_BLINK_SLOW : 500 ms ON / 500 ms OFF  (1 Hz) — advertising
//  LED_BLINK_FAST : 125 ms ON / 125 ms OFF  (4 Hz) — connected, idle
//  LED_SOLID      : always ON               — streaming + good SQI
//  LED_BLINK_SQI  : double blink + long OFF — streaming + poor SQI
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
//  handleButton
//
//  Short press (< 3 s) : toggle streaming START / STOP
//  Long press  (≥ 3 s) : disconnect BLE, re-advertise
// ══════════════════════════════════════════════════════════════
void handleButton() {
    unsigned long now = millis();
    bool          raw = digitalRead(BTN_PIN);  // LOW = pressed (pullup)

    if (raw != btnLastRaw) {
        btnLastRaw = raw;
        tBtnChange = now;
    }

    if ((now - tBtnChange) < BTN_DEBOUNCE_MS) return;

    if (raw == LOW && btnStable == HIGH) {
        btnStable   = LOW;
        tBtnPressed = now;
        btnHandled  = false;
    }

    if (raw == LOW && !btnHandled) {
        if ((now - tBtnPressed) >= BTN_LONG_PRESS_MS) {
            btnHandled = true;
            Serial.println("[BTN] Long press — resetting BLE...");
            BLEDevice central = BLE.central();
            if (central) central.disconnect();
            streaming = false;
            BLE.advertise();
            setLedMode(LED_BLINK_SLOW);
        }
    }

    if (raw == HIGH && btnStable == LOW) {
        btnStable = HIGH;
        if (!btnHandled) {
            if (streaming) {
                streaming = false;
                setLedMode(BLE.connected() ? LED_BLINK_FAST : LED_BLINK_SLOW);
                Serial.println("[BTN] Short press — Streaming STOPPED");
            } else {
                if (BLE.connected()) {
                    streaming = true;
                    seqNum    = 0;
                    setLedMode(LED_SOLID);
                    Serial.println("[BTN] Short press — Streaming STARTED");
                } else {
                    Serial.println("[BTN] Short press — phone not connected");
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
        Serial.println("[ERROR] MAX30102 — not found! Check: VCC=5V, SDA=D4, SCL=D5");
    } else {
        ppgSensor.setup(0x1F, 4, 2, 100, 411, 4096);
        ppgSensor.setPulseAmplitudeRed(0x1F);
        ppgSensor.setPulseAmplitudeIR(0x1F);
        Serial.println("[OK]    MAX30102  (PPG)");
    }

    // ── MPU6050 (Wrist IMU) ──────────────────────────────────
    imu.initialize();
    if (!imu.testConnection()) {
        Serial.println("[ERROR] MPU6050 — not found! Check: VCC=5V, SDA=D4, SCL=D5, AD0=GND");
    } else {
        imu.setFullScaleAccelRange(MPU6050_ACCEL_FS_2);   // ±2 g
        imu.setFullScaleGyroRange(MPU6050_GYRO_FS_250);   // ±250 °/s
        Serial.println("[OK]    MPU6050   (Wrist IMU)");
    }

    // ── MCP9808 (Temperature) ────────────────────────────────
    if (!tempSensor.begin(0x18)) {
        Serial.println("[ERROR] MCP9808 — not found! Check: VCC=5V, SDA=D4, SCL=D5, A0=A1=A2=GND");
    } else {
        tempSensor.setResolution(3);
        tempSensor.wake();
        Serial.println("[OK]    MCP9808   (Temperature)");
    }
}


// ══════════════════════════════════════════════════════════════
//  initBLE
// ══════════════════════════════════════════════════════════════
void initBLE() {
    if (!BLE.begin()) {
        Serial.println("[ERROR] BLE initialization failed! Halting.");
        while (true) { delay(1000); }
    }

    BLE.setLocalName(DEVICE_NAME);
    BLE.setAdvertisedService(wristSvc);

    wristSvc.addCharacteristic(dataChar);
    wristSvc.addCharacteristic(cmdChar);
    BLE.addService(wristSvc);

    BLE.setEventHandler(BLEConnected,    onBLEConnect);
    BLE.setEventHandler(BLEDisconnected, onBLEDisconnect);
    cmdChar.setEventHandler(BLEWritten,  onCmdWrite);

    BLE.advertise();

    Serial.println("[OK]    BLE Peripheral  (phone — advertising)");
    Serial.println("[OK]    BLE Central     (chest — time-sliced scan)");
}


// ══════════════════════════════════════════════════════════════
//  BLE PERIPHERAL CALLBACKS — Phone
// ══════════════════════════════════════════════════════════════
void onBLEConnect(BLEDevice central) {
    Serial.print("[PHONE] Connected: ");
    Serial.println(central.address());
    streaming = true;
    seqNum    = 0;
    setLedMode(LED_SOLID);
}

void onBLEDisconnect(BLEDevice central) {
    (void)central;
    streaming = false;
    setLedMode(LED_BLINK_SLOW);
    Serial.println("[PHONE] Disconnected — re-advertising...");
    BLE.advertise();
}

void onCmdWrite(BLEDevice central, BLECharacteristic characteristic) {
    (void)central;

    int      len = characteristic.valueLength();
    if (len <= 0) return;

    char     cmd[21];
    int      n = (len < 20) ? len : 20;
    memcpy(cmd, characteristic.value(), n);
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
        Serial.print("[CMD] Unknown: ");
        Serial.println(cmd);
    }
}


// ══════════════════════════════════════════════════════════════
//  BLE CENTRAL — Chest Module
//
//  ArduinoBLE does not support true simultaneous Central+Peripheral.
//  Strategy: scan briefly (500 ms) every 5 s when phone is not
//  actively streaming. Once chest connects, subscribe to notify
//  and poll chest data in the main loop.
// ══════════════════════════════════════════════════════════════

// Persistent chest BLE objects (kept alive between scans)
static BLEDevice     chestDevice;
static BLECharacteristic chestDataCharacteristic;

void scanForChest() {
    Serial.println("[CHEST] Scanning for BPSync-Chest...");

    BLE.scanForName(CHEST_DEVICE_NAME);
    unsigned long start = millis();

    while (millis() - start < CHEST_SCAN_DURATION_MS) {
        BLE.poll();
        BLEDevice found = BLE.available();
        if (found) {
            BLE.stopScan();
            connectToChest(found);
            return;
        }
    }

    BLE.stopScan();
    Serial.println("[CHEST] Not found — will retry.");
}

void connectToChest(BLEDevice& device) {
    Serial.print("[CHEST] Found — connecting to ");
    Serial.println(device.address());

    if (!device.connect()) {
        Serial.println("[CHEST] Connection failed.");
        return;
    }

    if (!device.discoverAttributes()) {
        Serial.println("[CHEST] Attribute discovery failed.");
        device.disconnect();
        return;
    }

    BLECharacteristic chr = device.characteristic(CHEST_DATA_UUID);
    if (!chr) {
        Serial.println("[CHEST] Characteristic not found.");
        device.disconnect();
        return;
    }

    if (!chr.canSubscribe()) {
        Serial.println("[CHEST] Cannot subscribe to characteristic.");
        device.disconnect();
        return;
    }

    if (!chr.subscribe()) {
        Serial.println("[CHEST] Subscribe failed.");
        device.disconnect();
        return;
    }

    chestDevice           = device;
    chestDataCharacteristic = chr;
    chestConnected        = true;
    Serial.println("[CHEST] Connected — receiving ECG + Chest IMU");
}

void readChestData(BLEDevice& chest) {
    if (!chest.connected()) {
        chestConnected = false;
        ep = 0; cx = 0; cy = 0; cz = 0; qi_c = 0;
        Serial.println("[CHEST] Disconnected — will rescan.");
        return;
    }

    if (chestDataCharacteristic.valueUpdated()) {
        int len = chestDataCharacteristic.valueLength();
        if (len >= (int)sizeof(ChestPacket)) {
            ChestPacket pkt;
            memcpy(&pkt, chestDataCharacteristic.value(), sizeof(ChestPacket));
            ep   = pkt.ep;
            cx   = pkt.cx;
            cy   = pkt.cy;
            cz   = pkt.cz;
            qi_c = pkt.qi_c;
        }
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
//  calculateSQI
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

    // Poll chest data if connected
    if (chestConnected) {
        readChestData(chestDevice);
    }

    // Update LED when streaming
    if (streaming && BLE.connected()) {
        setLedMode(qi_w ? LED_SOLID : LED_BLINK_SQI);
    }
}


// ══════════════════════════════════════════════════════════════
//  buildJSON
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

    dataChar.writeValue((uint8_t*)json, strlen(json));
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
