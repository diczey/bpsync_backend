/**
 * BPSync - Chest Module Firmware
 * Board   : Seeed XIAO nRF52840 (mbed-enabled)
 * Sensor  : DFRobot SEN0213 (AD8232 ECG) -> A0
 * LED     : D2
 *
 * BLE Peripheral - Phone connects directly
 *   Device name : "BPSync-Chest"
 *   Service     : 29B10000-E8F2-537E-4F6C-D104768A1214
 *   DataChar    : 29B10001  (Notify, 24 B) - ChestPacket
 *   CmdChar     : 29B10002  (Write,  20 B) - START / STOP
 *
 * CHEST PACKET (24 bytes, packed):
 *   ep      : uint8    - ECG R-peak flag (0/1)
 *   qi_c    : uint8    - Chest SQI (1=leads OK, 0=off)
 *   seq     : uint16   - Packet sequence (resets to 0 on START)
 *   ecg[10] : int16x10 - Raw ECG samples @ 250 Hz
 *
 * TX rate: 25 Hz (every 40ms) - only while streaming=true
 *
 * LED modes:
 *   Slow blink (500ms) : advertising, no phone connected
 *   Fast blink (125ms) : phone connected, idle (waiting START)
 *   Solid ON           : streaming + leads on
 *   Double blink       : streaming + leads off
 */

#include <ArduinoBLE.h>
#include <math.h>
#include <string.h>

#define DEVICE_NAME        "BPSync-Chest"
#define CHEST_SERVICE_UUID "29B10000-E8F2-537E-4F6C-D104768A1214"
#define CHEST_DATA_UUID    "29B10001-E8F2-537E-4F6C-D104768A1214"
#define CHEST_CMD_UUID     "29B10002-E8F2-537E-4F6C-D104768A1214"

#define ECG_PIN   A0
#define LED_PIN   D2

#define ECG_SAMPLE_INTERVAL_US  4000
#define SEND_INTERVAL_MS        40
#define ECG_BATCH_SIZE          10
#define VARIANCE_BUF_SIZE       50
#define LEAD_ON_VARIANCE        200
#define MWI_SIZE                30
#define REFRACTORY_MS           250
#define THRESHOLD_RATIO         0.25f

struct __attribute__((packed)) ChestPacket {
    uint8_t  ep;
    uint8_t  qi_c;
    uint16_t seq;
    int16_t  ecg[ECG_BATCH_SIZE];
};  // 24 bytes

BLEService        chestSvc(CHEST_SERVICE_UUID);
BLECharacteristic dataChar(CHEST_DATA_UUID, BLENotify, sizeof(ChestPacket));
BLECharacteristic cmdChar(CHEST_CMD_UUID, BLEWrite | BLEWriteWithoutResponse, 20);

// DSP state
static float   hp_xprev = 0, hp_yprev = 0;
static const float HP_ALPHA = 0.9937f;
static float   lp_yprev = 0;
static const float LP_ALPHA = 0.353f;
static float   derivBuf[5] = {0};
static uint8_t derivIdx = 0;
static float   mwiBuf[MWI_SIZE] = {0};
static uint8_t mwiIdx = 0;
static float   mwiSum = 0, mwiPeak = 100, mwiThresh = 25;
static int32_t varianceBuf[VARIANCE_BUF_SIZE];
static uint8_t varIdx = 0;
static bool    leadsOn = false;

// Batch buffer
static int16_t  ecgBatch[ECG_BATCH_SIZE];
static uint8_t  ecgBatchIdx = 0;

// State
static uint8_t  ep_flag   = 0;
static uint8_t  qi_c_val  = 0;
static uint16_t pktSeq    = 0;
static bool     streaming = false;

// LED
enum LedMode { LED_BLINK_SLOW, LED_BLINK_FAST, LED_SOLID, LED_BLINK_SQI };
static LedMode       ledMode       = LED_BLINK_SLOW;
static unsigned long tLed          = 0;
static bool          ledState      = false;
static uint8_t       sqiBlinkPhase = 0;

// Timers
static unsigned long lastSampleUs = 0;
static unsigned long tSend        = 0;
static unsigned long lastPeakMs   = 0;

void initBLE();
void initHardware();
void sampleECG();
void sendChestPacket();
void updateLED();
void setLedMode(LedMode mode);
void onBLEConnect(BLEDevice central);
void onBLEDisconnect(BLEDevice central);
void onCmdWrite(BLEDevice central, BLECharacteristic characteristic);
void resetChestStreamingState();


void setup() {
    Serial.begin(115200);
    while (!Serial && millis() < 3000) delay(10);
    Serial.println("==============================================");
    Serial.println("  BPSync Chest Module");
    Serial.println("  Seeed XIAO nRF52840  |  ArduinoBLE");
    Serial.println("  Mode: Direct Peripheral to Phone");
    Serial.println("==============================================");
    initHardware();
    initBLE();
    Serial.println("[READY] Waiting for phone connection...");
    Serial.println("==============================================");
}

void loop() {
    BLE.poll();

    unsigned long nowUs = micros();
    if ((nowUs - lastSampleUs) >= (unsigned long)ECG_SAMPLE_INTERVAL_US) {
        lastSampleUs = nowUs;
        sampleECG();
    }

    unsigned long nowMs = millis();
    if (streaming && BLE.connected() && ecgBatchIdx >= ECG_BATCH_SIZE && (nowMs - tSend) >= SEND_INTERVAL_MS) {
        tSend = nowMs;
        sendChestPacket();
        ep_flag     = 0;
        ecgBatchIdx = 0;
    }

    updateLED();
}

void initHardware() {
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);
    analogReadResolution(12);
    pinMode(ECG_PIN, INPUT);
    for (int i = 0; i < VARIANCE_BUF_SIZE; i++) varianceBuf[i] = 2048;
    Serial.println("[OK]    LED (D2) + ECG (A0)");
}

void initBLE() {
    if (!BLE.begin()) {
        Serial.println("[ERROR] BLE init failed - halting.");
        while (true) delay(1000);
    }
    BLE.setLocalName(DEVICE_NAME);
    BLE.setAdvertisedService(chestSvc);
    chestSvc.addCharacteristic(dataChar);
    chestSvc.addCharacteristic(cmdChar);
    BLE.addService(chestSvc);
    BLE.setEventHandler(BLEConnected,    onBLEConnect);
    BLE.setEventHandler(BLEDisconnected, onBLEDisconnect);
    cmdChar.setEventHandler(BLEWritten,  onCmdWrite);
    BLE.advertise();
    Serial.println("[OK]    BLE Peripheral ready");
    Serial.println("[ADV]   Advertising as 'BPSync-Chest'...");
}

void onBLEConnect(BLEDevice central) {
    Serial.println("[PHONE] Connected: " + String(central.address()));
    Serial.println("[PHONE] Send 'START' to CmdChar (29B10002) to begin streaming.");
    streaming = false;
    resetChestStreamingState();
    setLedMode(LED_BLINK_FAST);
}

void onBLEDisconnect(BLEDevice central) {
    (void)central;
    streaming = false;
    pktSeq    = 0;
    resetChestStreamingState();
    setLedMode(LED_BLINK_SLOW);
    Serial.println("[PHONE] Disconnected - re-advertising...");
    Serial.println("[ADV]   Advertising as 'BPSync-Chest'...");
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
        pktSeq    = 0;
        streaming = true;
        resetChestStreamingState();
        setLedMode(leadsOn ? LED_SOLID : LED_BLINK_SQI);
        Serial.println("[SESSION] Streaming STARTED - seq=0, TX @ 25 Hz");
    } else if (strcmp(cmd, "STOP") == 0) {
        streaming = false;
        pktSeq    = 0;
        resetChestStreamingState();
        setLedMode(LED_BLINK_FAST);
        Serial.println("[SESSION] Streaming STOPPED");
    } else {
        Serial.print("[CMD]   Unknown: '"); Serial.print(cmd); Serial.println("'");
    }
}

void resetChestStreamingState() {
    memset(ecgBatch, 0, sizeof(ecgBatch));
    ecgBatchIdx  = 0;
    ep_flag      = 0;
    lastPeakMs   = 0;
    tSend        = millis();
    lastSampleUs = micros();
}

void sampleECG() {
    int16_t raw = (int16_t)analogRead(ECG_PIN);
    ecgBatch[ecgBatchIdx % ECG_BATCH_SIZE] = raw;
    ecgBatchIdx++;

    varianceBuf[varIdx] = (int32_t)raw;
    varIdx = (varIdx + 1) % VARIANCE_BUF_SIZE;
    if (varIdx == 0) {
        int64_t sum = 0;
        for (int i = 0; i < VARIANCE_BUF_SIZE; i++) sum += varianceBuf[i];
        int32_t mean = (int32_t)(sum / VARIANCE_BUF_SIZE);
        int64_t varSum = 0;
        for (int i = 0; i < VARIANCE_BUF_SIZE; i++) {
            int32_t d = varianceBuf[i] - mean; varSum += d * d;
        }
        bool wasOn = leadsOn;
        leadsOn  = ((int32_t)(varSum / VARIANCE_BUF_SIZE) > LEAD_ON_VARIANCE);
        qi_c_val = leadsOn ? 1 : 0;
        if (!wasOn && leadsOn) {
            Serial.println("[ECG]   Leads ON - signal OK");
            if (streaming) setLedMode(LED_SOLID);
        }
        if (wasOn && !leadsOn) {
            Serial.println("[ECG]   Leads OFF - check electrodes");
            if (streaming) setLedMode(LED_BLINK_SQI);
        }
    }

    float rawf = (float)raw;
    float hp = HP_ALPHA * (rawf - hp_xprev) + HP_ALPHA * hp_yprev;
    hp_xprev = rawf; hp_yprev = hp;
    float lp = LP_ALPHA * hp + (1.0f - LP_ALPHA) * lp_yprev;
    lp_yprev = lp;

    derivBuf[derivIdx] = lp;
    derivIdx = (derivIdx + 1) % 5;
    float d = ( 2.0f * derivBuf[(derivIdx + 4) % 5]
               +       derivBuf[(derivIdx + 3) % 5]
               -       derivBuf[(derivIdx + 1) % 5]
               - 2.0f * derivBuf[(derivIdx    ) % 5] ) / 8.0f;

    float sq = d * d;
    mwiSum -= mwiBuf[mwiIdx];
    mwiBuf[mwiIdx] = sq; mwiSum += sq;
    mwiIdx = (mwiIdx + 1) % MWI_SIZE;
    float mwi = mwiSum / MWI_SIZE;

    if (mwi > mwiPeak) mwiPeak = 0.125f * mwi + 0.875f * mwiPeak;
    else               mwiPeak = 0.001f * mwi + 0.999f * mwiPeak;
    mwiThresh = THRESHOLD_RATIO * mwiPeak;

    unsigned long nowMs = millis();
    if (leadsOn && mwi > mwiThresh && (nowMs - lastPeakMs) > (unsigned long)REFRACTORY_MS) {
        ep_flag    = 1;
        lastPeakMs = nowMs;
        Serial.print("[ECG]   R-peak  mwi="); Serial.print(mwi, 1);
        Serial.print("  thresh="); Serial.print(mwiThresh, 1);
        Serial.print("  seq="); Serial.println(pktSeq);
    }
}

void sendChestPacket() {
    ChestPacket pkt;
    pkt.ep   = ep_flag;
    pkt.qi_c = qi_c_val;
    pkt.seq  = pktSeq++;
    uint8_t count = (ecgBatchIdx < ECG_BATCH_SIZE) ? ecgBatchIdx : ECG_BATCH_SIZE;
    uint8_t start = (ecgBatchIdx >= ECG_BATCH_SIZE) ? (ecgBatchIdx % ECG_BATCH_SIZE) : 0;
    for (int i = 0; i < ECG_BATCH_SIZE; i++)
        pkt.ecg[i] = (i < count) ? ecgBatch[(start + i) % ECG_BATCH_SIZE] : 0;
    dataChar.writeValue((uint8_t*)&pkt, sizeof(pkt));
    if (pktSeq % 50 == 0) {
        Serial.print("[TX]    seq="); Serial.print(pkt.seq);
        Serial.print("  ep="); Serial.print(pkt.ep);
        Serial.print("  qi_c="); Serial.print(pkt.qi_c);
        Serial.print("  ecg[0]="); Serial.println(pkt.ecg[0]);
    }
}

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
