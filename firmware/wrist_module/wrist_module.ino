/**
 * BPSync - Wrist Module Firmware
 * Board   : Seeed XIAO nRF52840 (mbed-enabled)
 * Board pkg: Seeed nRF52 mbed-enabled Boards
 *
 * BLE Peripheral — Phone connects directly
 *   Device name : "BPSync-Wrist"
 *   Service     : 19B10000-E8F2-537E-4F6C-D104768A1214
 *   DataChar    : 19B10001  (Notify, 400 B) — JSON frame
 *   CmdChar     : 19B10002  (Write,   20 B) — START / STOP
 *
 * SENSORS (I2C — SDA=D4, SCL=D5):
 *   MAX30102 @ 0x57 — PPG (IR + Red) @ 200 Hz, batch 8
 *   MPU6050  @ 0x68 — Accel + Gyro   @ 50 Hz
 *   MCP9808  @ 0x18 — Temperature    @ 1 Hz
 *
 * JSON FRAME (25 Hz, ~350 bytes):
 *   {"ts":ms, "sq":n,
 *    "pi":[8xIR], "pr":[8xRed],
 *    "ax":, "ay":, "az":, "gx":, "gy":, "gz":,
 *    "tp":degC,
 *    "qi_w":0|1, "bt":100}
 *
 * NOTE: ep/ecg/qi_c fields removed — chest sends those directly to phone.
 *       Phone syncs wrist+chest by seq number.
 *
 * LED modes:
 *   Slow blink (500ms) : advertising, no phone
 *   Fast blink (125ms) : phone connected, idle
 *   Solid ON           : streaming + good signal
 *   Double blink       : streaming + poor signal
 *
 * Button (D2):
 *   Short press : START / STOP streaming
 *   Long press  : BLE reset + re-advertise
 */

#include <Wire.h>
#include <ArduinoBLE.h>
#include <MAX30105.h>
#include <MPU6050.h>
#include <Adafruit_MCP9808.h>

// ──────────────────────────────────────────────────────────────
//  BLE UUIDs
// ──────────────────────────────────────────────────────────────
#define DEVICE_NAME        "BPSync-Wrist"
#define WRIST_SERVICE_UUID "19B10000-E8F2-537E-4F6C-D104768A1214"
#define DATA_CHAR_UUID     "19B10001-E8F2-537E-4F6C-D104768A1214"
#define CMD_CHAR_UUID      "19B10002-E8F2-537E-4F6C-D104768A1214"

// ──────────────────────────────────────────────────────────────
//  PINS
// ──────────────────────────────────────────────────────────────
#define LED_PIN   D3
#define BTN_PIN   D2

// ──────────────────────────────────────────────────────────────
//  SAMPLING INTERVALS
// ──────────────────────────────────────────────────────────────
#define PPG_INTERVAL_MS    5       // 200 Hz
#define PPG_BATCH_SIZE     8       // 8 samples x 5ms = 40ms window
#define IMU_INTERVAL_MS    20      //  50 Hz
#define TEMP_INTERVAL_MS   1000    //   1 Hz
#define BLE_TX_INTERVAL_MS 40      //  25 Hz

// ──────────────────────────────────────────────────────────────
//  SQI THRESHOLDS
// ──────────────────────────────────────────────────────────────
#define SQI_MOTION_THRESHOLD  4096
#define SQI_PPG_MIN           10000

// ──────────────────────────────────────────────────────────────
//  BLE OBJECTS
// ──────────────────────────────────────────────────────────────
BLEService        wristSvc(WRIST_SERVICE_UUID);
BLECharacteristic dataChar(DATA_CHAR_UUID, BLENotify, 400);
BLECharacteristic cmdChar(CMD_CHAR_UUID, BLEWrite | BLEWriteWithoutResponse, 20);

// ──────────────────────────────────────────────────────────────
//  SENSOR OBJECTS
// ──────────────────────────────────────────────────────────────
MAX30105         ppgSensor;
MPU6050          imu;
Adafruit_MCP9808 tempSensor;

// ──────────────────────────────────────────────────────────────
//  SENSOR DATA
// ──────────────────────────────────────────────────────────────
uint32_t ppg_ir_batch[PPG_BATCH_SIZE]  = {0};
uint32_t ppg_red_batch[PPG_BATCH_SIZE] = {0};
uint8_t  ppgBatchIdx = 0;
int16_t  w_ax = 0, w_ay = 0, w_az = 0;
int16_t  w_gx = 0, w_gy = 0, w_gz = 0;
float    temperature = 0.0f;
uint8_t  qi_w = 0;

// ──────────────────────────────────────────────────────────────
//  SESSION STATE
// ──────────────────────────────────────────────────────────────
uint16_t seqNum         = 0;
bool     streaming      = false;
bool     phoneConnected = false;

// ──────────────────────────────────────────────────────────────
//  LED
// ──────────────────────────────────────────────────────────────
enum LedMode { LED_BLINK_SLOW, LED_BLINK_FAST, LED_SOLID, LED_BLINK_SQI };
LedMode       ledMode       = LED_BLINK_SLOW;
unsigned long tLed          = 0;
bool          ledState      = false;
uint8_t       sqiBlinkPhase = 0;

// ──────────────────────────────────────────────────────────────
//  BUTTON
// ──────────────────────────────────────────────────────────────
#define BTN_DEBOUNCE_MS          50
#define BTN_LONG_PRESS_MS        3000
#define PHONE_IDLE_DISCONNECT_MS 15000

bool          btnLastRaw    = HIGH;
bool          btnStable     = HIGH;
unsigned long tBtnChange    = 0;
unsigned long tBtnPressed   = 0;
bool          btnHandled    = false;
unsigned long tPhoneConnected = 0;

// ──────────────────────────────────────────────────────────────
//  TIMERS
// ──────────────────────────────────────────────────────────────
unsigned long tPPG  = 0;
unsigned long tIMU  = 0;
unsigned long tTemp = 0;
unsigned long tBLE  = 0;

// ──────────────────────────────────────────────────────────────
//  PROTOTYPES
// ──────────────────────────────────────────────────────────────
void initHardware();
void initSensors();
void initBLE();
void readPPG();
void readIMU();
void readTemperature();
void calculateSQI();
void buildJSON(char* buf, int bufSize);
void sendToPhone();
void handleButton();
void updateLED();
void setLedMode(LedMode mode);
void onPhoneConnect(BLEDevice central);
void onPhoneDisconnect(BLEDevice central);
void onCmdWrite(BLEDevice central, BLECharacteristic characteristic);


// ══════════════════════════════════════════════════════════════
//  SETUP
// ══════════════════════════════════════════════════════════════
void setup() {
    Serial.begin(115200);
    while (!Serial && millis() < 3000) delay(10);

    Serial.println("==============================================");
    Serial.println("  BPSync Wrist Module");
    Serial.println("  Seeed XIAO nRF52840  |  ArduinoBLE");
    Serial.println("  Mode: Direct Peripheral to Phone");
    Serial.println("==============================================");

    Wire.begin();
    initHardware();
    initSensors();
    initBLE();

    Serial.println("[READY] Waiting for phone connection...");
    Serial.println("==============================================");
}


// ══════════════════════════════════════════════════════════════
//  LOOP
// ══════════════════════════════════════════════════════════════
void loop() {
    BLE.poll();

    unsigned long now = millis();

    if (now - tPPG >= PPG_INTERVAL_MS)   { readPPG();         tPPG  = now; }
    if (now - tIMU >= IMU_INTERVAL_MS)   { readIMU();         tIMU  = now; }
    if (now - tTemp >= TEMP_INTERVAL_MS) { readTemperature(); tTemp = now; }

    calculateSQI();

    if (streaming && phoneConnected && (now - tBLE >= BLE_TX_INTERVAL_MS)) {
        sendToPhone();
        tBLE = now;
    }

    // Auto-disconnect if idle too long after STOP
    if (phoneConnected && !streaming && tPhoneConnected > 0 &&
        (now - tPhoneConnected >= PHONE_IDLE_DISCONNECT_MS)) {
        Serial.println("[PHONE] Idle timeout — disconnecting.");
        BLEDevice central = BLE.central();
        if (central) central.disconnect();
        tPhoneConnected = 0;
        setLedMode(LED_BLINK_SLOW);
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
//  initSensors
// ══════════════════════════════════════════════════════════════
void initSensors() {
    // MAX30102 — 200 Hz PPG
    if (!ppgSensor.begin(Wire, I2C_SPEED_FAST)) {
        Serial.println("[ERROR] MAX30102 not found! Check VCC, SDA=D4, SCL=D5");
    } else {
        ppgSensor.setup(0x1F, 4, 2, 200, 215, 4096);  // 200Hz, 215us pulse
        ppgSensor.setPulseAmplitudeRed(0x1F);
        ppgSensor.setPulseAmplitudeIR(0x1F);
        Serial.println("[OK]    MAX30102  (PPG @ 200 Hz)");
    }

    // MPU6050
    imu.initialize();
    if (!imu.testConnection()) {
        Serial.println("[ERROR] MPU6050 not found! Check VCC, SDA=D4, SCL=D5, AD0=GND");
    } else {
        imu.setFullScaleAccelRange(MPU6050_ACCEL_FS_2);
        imu.setFullScaleGyroRange(MPU6050_GYRO_FS_250);
        Serial.println("[OK]    MPU6050   (IMU @ 50 Hz)");
    }

    // MCP9808
    if (!tempSensor.begin(0x18)) {
        Serial.println("[ERROR] MCP9808 not found! Check VCC, SDA=D4, SCL=D5");
    } else {
        tempSensor.setResolution(3);
        tempSensor.wake();
        Serial.println("[OK]    MCP9808   (Temp @ 1 Hz)");
    }
}


// ══════════════════════════════════════════════════════════════
//  initBLE
// ══════════════════════════════════════════════════════════════
void initBLE() {
    if (!BLE.begin()) {
        Serial.println("[ERROR] BLE init failed — halting.");
        while (true) delay(1000);
    }

    BLE.setLocalName(DEVICE_NAME);
    BLE.setAdvertisedService(wristSvc);
    wristSvc.addCharacteristic(dataChar);
    wristSvc.addCharacteristic(cmdChar);
    BLE.addService(wristSvc);

    BLE.setEventHandler(BLEConnected,    onPhoneConnect);
    BLE.setEventHandler(BLEDisconnected, onPhoneDisconnect);
    cmdChar.setEventHandler(BLEWritten,  onCmdWrite);

    BLE.advertise();

    Serial.println("[OK]    BLE Peripheral ready");
    Serial.println("[ADV]   Advertising as 'BPSync-Wrist'...");
}


// ══════════════════════════════════════════════════════════════
//  BLE CALLBACKS
// ══════════════════════════════════════════════════════════════
void onPhoneConnect(BLEDevice central) {
    phoneConnected  = true;
    streaming       = false;
    seqNum          = 0;
    tPhoneConnected = millis();
    setLedMode(LED_BLINK_FAST);
    Serial.println("[PHONE] Connected: " + String(central.address()));
    Serial.println("[PHONE] Send 'START' to CmdChar (19B10002) to begin streaming.");
}

void onPhoneDisconnect(BLEDevice central) {
    (void)central;
    phoneConnected  = false;
    streaming       = false;
    tPhoneConnected = 0;
    setLedMode(LED_BLINK_SLOW);
    Serial.println("[PHONE] Disconnected — re-advertising...");
    Serial.println("[ADV]   Advertising as 'BPSync-Wrist'...");
    BLE.advertise();
}

void onCmdWrite(BLEDevice central, BLECharacteristic characteristic) {
    (void)central;
    int len = characteristic.valueLength();
    if (len <= 0) return;

    char cmd[21];
    int  n = (len < 20) ? len : 20;
    memcpy(cmd, characteristic.value(), n);
    cmd[n] = '\0';

    Serial.print("[CMD]   Received: '"); Serial.print(cmd); Serial.println("'");

    if (strcmp(cmd, "START") == 0) {
        streaming       = true;
        seqNum          = 0;
        tPhoneConnected = 0;
        setLedMode(qi_w ? LED_SOLID : LED_BLINK_SQI);
        Serial.println("[SESSION] Streaming STARTED — seq=0, TX @ 25 Hz");
    } else if (strcmp(cmd, "STOP") == 0) {
        streaming       = false;
        tPhoneConnected = millis();
        setLedMode(LED_BLINK_FAST);
        Serial.println("[SESSION] Streaming STOPPED");
    } else {
        Serial.print("[CMD]   Unknown: '"); Serial.print(cmd); Serial.println("'");
    }
}


// ══════════════════════════════════════════════════════════════
//  SENSOR READ FUNCTIONS
// ══════════════════════════════════════════════════════════════
void readPPG() {
    ppgSensor.check();
    while (ppgSensor.available()) {
        uint8_t idx = ppgBatchIdx % PPG_BATCH_SIZE;
        ppg_ir_batch[idx]  = ppgSensor.getIR();
        ppg_red_batch[idx] = ppgSensor.getRed();
        ppgBatchIdx++;
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

void calculateSQI() {
    float mag       = sqrt((float)w_ax*w_ax + (float)w_ay*w_ay + (float)w_az*w_az);
    float deviation = fabs(mag - 16384.0f);
    bool  motionOK  = (deviation < SQI_MOTION_THRESHOLD);
    bool  ppgOK     = (ppg_ir_batch[ppgBatchIdx % PPG_BATCH_SIZE] > SQI_PPG_MIN);
    qi_w = (motionOK && ppgOK) ? 1 : 0;

    if (streaming && phoneConnected) {
        setLedMode(qi_w ? LED_SOLID : LED_BLINK_SQI);
    }
}


// ══════════════════════════════════════════════════════════════
//  buildJSON
// ══════════════════════════════════════════════════════════════
void buildJSON(char* buf, int bufSize) {
    // PPG IR array [8]
    char piStr[80];
    int  pos = 0;
    piStr[pos++] = '[';
    uint8_t startIdx = ppgBatchIdx % PPG_BATCH_SIZE;
    for (int i = 0; i < PPG_BATCH_SIZE; i++) {
        uint8_t idx = (startIdx + i) % PPG_BATCH_SIZE;
        pos += snprintf(piStr + pos, sizeof(piStr) - pos,
                        "%lu%s", (unsigned long)ppg_ir_batch[idx],
                        (i < PPG_BATCH_SIZE - 1) ? "," : "");
    }
    piStr[pos++] = ']'; piStr[pos] = '\0';

    // PPG Red array [8]
    char prStr[80];
    pos = 0;
    prStr[pos++] = '[';
    for (int i = 0; i < PPG_BATCH_SIZE; i++) {
        uint8_t idx = (startIdx + i) % PPG_BATCH_SIZE;
        pos += snprintf(prStr + pos, sizeof(prStr) - pos,
                        "%lu%s", (unsigned long)ppg_red_batch[idx],
                        (i < PPG_BATCH_SIZE - 1) ? "," : "");
    }
    prStr[pos++] = ']'; prStr[pos] = '\0';

    snprintf(buf, bufSize,
        "{"
        "\"ts\":%lu,"
        "\"sq\":%u,"
        "\"pi\":%s,"
        "\"pr\":%s,"
        "\"ax\":%d,\"ay\":%d,\"az\":%d,"
        "\"gx\":%d,\"gy\":%d,\"gz\":%d,"
        "\"tp\":%.1f,"
        "\"qi_w\":%u,"
        "\"bt\":100"
        "}",
        (unsigned long)millis(),
        (unsigned int)seqNum,
        piStr, prStr,
        (int)w_ax, (int)w_ay, (int)w_az,
        (int)w_gx, (int)w_gy, (int)w_gz,
        temperature,
        (unsigned int)qi_w
    );
}


// ══════════════════════════════════════════════════════════════
//  sendToPhone
// ══════════════════════════════════════════════════════════════
void sendToPhone() {
    char json[400];
    buildJSON(json, sizeof(json));
    dataChar.writeValue((uint8_t*)json, strlen(json));
    seqNum++;

    // Log every 50 frames (~2 sec)
    if (seqNum % 50 == 0) {
        Serial.print("[TX]    seq="); Serial.print(seqNum);
        Serial.print("  qi_w="); Serial.print(qi_w);
        Serial.print("  tp="); Serial.print(temperature, 1);
        Serial.print("  pi[0]="); Serial.println(ppg_ir_batch[0]);
    }
}


// ══════════════════════════════════════════════════════════════
//  handleButton
// ══════════════════════════════════════════════════════════════
void handleButton() {
    unsigned long now = millis();
    bool          raw = digitalRead(BTN_PIN);

    if (raw != btnLastRaw) { btnLastRaw = raw; tBtnChange = now; }
    if ((now - tBtnChange) < BTN_DEBOUNCE_MS) return;

    if (raw == LOW && btnStable == HIGH) {
        btnStable = LOW; tBtnPressed = now; btnHandled = false;
    }

    if (raw == LOW && !btnHandled) {
        if ((now - tBtnPressed) >= BTN_LONG_PRESS_MS) {
            btnHandled = true;
            streaming  = false; tPhoneConnected = 0;
            BLEDevice central = BLE.central();
            if (central) central.disconnect();
            setLedMode(LED_BLINK_SLOW);
            Serial.println("[BTN]   Long press — BLE reset, re-advertising...");
        }
    }

    if (raw == HIGH && btnStable == LOW) {
        btnStable = HIGH;
        if (!btnHandled) {
            if (streaming) {
                streaming       = false;
                tPhoneConnected = millis();
                setLedMode(phoneConnected ? LED_BLINK_FAST : LED_BLINK_SLOW);
                Serial.println("[BTN]   Short press — Streaming STOPPED");
            } else {
                if (phoneConnected) {
                    streaming       = true;
                    seqNum          = 0;
                    tPhoneConnected = 0;
                    setLedMode(qi_w ? LED_SOLID : LED_BLINK_SQI);
                    Serial.println("[BTN]   Short press — Streaming STARTED, seq=0");
                } else {
                    Serial.println("[BTN]   Short press — phone not connected");
                }
            }
        }
    }
}


// ══════════════════════════════════════════════════════════════
//  LED STATE MACHINE
// ══════════════════════════════════════════════════════════════
void setLedMode(LedMode mode) {
    if (ledMode == mode) return;
    ledMode = mode; sqiBlinkPhase = 0; tLed = 0;
}

void updateLED() {
    unsigned long now = millis();
    switch (ledMode) {
        case LED_BLINK_SLOW:
            if (now - tLed >= 500) { ledState = !ledState; digitalWrite(LED_PIN, ledState); tLed = now; } break;
        case LED_BLINK_FAST:
            if (now - tLed >= 125) { ledState = !ledState; digitalWrite(LED_PIN, ledState); tLed = now; } break;
        case LED_SOLID:
            digitalWrite(LED_PIN, HIGH); ledState = true; break;
        case LED_BLINK_SQI: {
            static const uint16_t sqiTiming[4] = {80, 80, 80, 760};
            if (now - tLed >= sqiTiming[sqiBlinkPhase]) {
                sqiBlinkPhase = (sqiBlinkPhase + 1) % 4;
                digitalWrite(LED_PIN, (sqiBlinkPhase == 0 || sqiBlinkPhase == 2));
                tLed = now;
            }
            break;
        }
    }
}
