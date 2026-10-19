/*
 * MachinoCare - Unified Multi-Sensor Telemetry Engine (with ADXL345 Auto-Recovery)
 * Actuator: RS-555 DC Motor via L298N (PWM Soft-Start)
 * Sensors : INA219 (I2C), DS18B20 (1-Wire), ADXL345 (Hardware SPI Mode 3)
 */

#include <Wire.h>
#include <SPI.h>
#include <Adafruit_INA219.h>
#include <OneWire.h>
#include <DallasTemperature.h>

// --- Pin Allocations ---
#define PIN_ENA 25
#define PIN_IN1 26
#define PIN_IN2 27

#define ONE_WIRE_BUS 15

#define ADXL_CS   5
#define ADXL_SCK  18
#define ADXL_MISO 19
#define ADXL_MOSI 23

// --- ADXL345 Registers ---
#define ADXL_REG_DEVID       0x00
#define ADXL_REG_BW_RATE     0x2C
#define ADXL_REG_POWER_CTL   0x2D
#define ADXL_REG_DATA_FORMAT 0x31
#define ADXL_REG_DATAX0      0x32
#define ADXL_SPI_READ        0x80

// --- PWM Config ---
const int PWM_FREQ = 5000;
const int PWM_RES  = 8;
const int PWM_CH   = 0;

Adafruit_INA219 ina219;
OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature tempSensor(&oneWire);

SPISettings adxlSpiSettings(1000000, MSBFIRST, SPI_MODE3);

// --- Direct Hardware SPI Helpers ---
void adxlWrite(uint8_t reg, uint8_t val) {
  SPI.beginTransaction(adxlSpiSettings);
  digitalWrite(ADXL_CS, LOW);
  SPI.transfer(reg);
  SPI.transfer(val);
  digitalWrite(ADXL_CS, HIGH);
  SPI.endTransaction();
}

uint8_t adxlRead(uint8_t reg) {
  SPI.beginTransaction(adxlSpiSettings);
  digitalWrite(ADXL_CS, LOW);
  SPI.transfer(reg | ADXL_SPI_READ);
  uint8_t val = SPI.transfer(0x00);
  digitalWrite(ADXL_CS, HIGH);
  SPI.endTransaction();
  return val;
}

// Auto-recovery watchdog: detects if brownout/noise reset the ADXL345 back to standby
void checkAndRecoverADXL() {
  uint8_t pwr = adxlRead(ADXL_REG_POWER_CTL);
  if (pwr != 0x08) {
    adxlWrite(ADXL_REG_BW_RATE, 0x0A);     // 100 Hz Data Rate
    adxlWrite(ADXL_REG_DATA_FORMAT, 0x0B); // Full Resolution, +/-16g
    adxlWrite(ADXL_REG_POWER_CTL, 0x00);   // Clear Standby
    delayMicroseconds(50);
    adxlWrite(ADXL_REG_POWER_CTL, 0x08);   // Re-arm Measurement Mode
    delayMicroseconds(50);
  }
}

bool initADXL345() {
  pinMode(ADXL_CS, OUTPUT);
  digitalWrite(ADXL_CS, HIGH);

  SPI.begin(ADXL_SCK, ADXL_MISO, ADXL_MOSI, ADXL_CS);
  delay(50);

  uint8_t devId = adxlRead(ADXL_REG_DEVID);
  if (devId != 0xE5) {
    Serial.printf("[-] ADXL345 ID Mismatch: 0x%02X (Expected: 0xE5)\n", devId);
    return false;
  }

  adxlWrite(ADXL_REG_BW_RATE, 0x0A);     // 100 Hz Data Rate
  adxlWrite(ADXL_REG_DATA_FORMAT, 0x0B); // Full Resolution, +/-16g
  adxlWrite(ADXL_REG_POWER_CTL, 0x00);   // Clear Standby
  delay(10);
  adxlWrite(ADXL_REG_POWER_CTL, 0x08);   // Enable Measurement Mode
  delay(10);

  return true;
}

void readRawAxes(float &ax, float &ay, float &az) {
  checkAndRecoverADXL(); // Prevent standby lockup before reading data registers

  uint8_t x0 = adxlRead(0x32);
  uint8_t x1 = adxlRead(0x33);
  uint8_t y0 = adxlRead(0x34);
  uint8_t y1 = adxlRead(0x35);
  uint8_t z0 = adxlRead(0x36);
  uint8_t z1 = adxlRead(0x37);

  int16_t rawX = (int16_t)((x1 << 8) | x0);
  int16_t rawY = (int16_t)((y1 << 8) | y0);
  int16_t rawZ = (int16_t)((z1 << 8) | z0);

  const float scale = 0.0039 * 9.80665;
  ax = rawX * scale;
  ay = rawY * scale;
  az = rawZ * scale;
}

float getVibrationRMS(int samples) {
  float magSamples[samples];
  float meanMag = 0.0;

  for (int i = 0; i < samples; i++) {
    float ax, ay, az;
    readRawAxes(ax, ay, az);
    float mag = sqrt(ax * ax + ay * ay + az * az);
    magSamples[i] = mag;
    meanMag += mag;
    delayMicroseconds(600);
  }

  meanMag /= samples;

  float sumSq = 0.0;
  for (int i = 0; i < samples; i++) {
    float diff = magSamples[i] - meanMag;
    sumSq += (diff * diff);
  }

  return sqrt(sumSq / samples);
}

void setMotorSpeed(int duty) {
  duty = constrain(duty, 0, 255);
  #if ESP_ARDUINO_VERSION_MAJOR >= 3
    ledcWrite(PIN_ENA, duty);
  #else
    ledcWrite(PWM_CH, duty);
  #endif
}

void printTelemetry(const char* label) {
  float busVoltage = ina219.getBusVoltage_V();
  float current_mA = ina219.getCurrent_mA();

  tempSensor.requestTemperatures();
  float tempC = tempSensor.getTempCByIndex(0);

  float vibRMS = getVibrationRMS(40);

  if (tempC == DEVICE_DISCONNECTED_C) {
    Serial.printf("[%s] V: %5.2fV | I: %5.1fmA | T: ERR   | Vib RMS: %5.2f m/s²\n",
                  label, busVoltage, current_mA, vibRMS);
  } else {
    Serial.printf("[%s] V: %5.2fV | I: %5.1fmA | T: %4.1f°C | Vib RMS: %5.2f m/s²\n",
                  label, busVoltage, current_mA, tempC, vibRMS);
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=== MachinoCare: Full Telemetry System Initialization ===");

  pinMode(PIN_IN1, OUTPUT);
  pinMode(PIN_IN2, OUTPUT);
  digitalWrite(PIN_IN1, LOW);
  digitalWrite(PIN_IN2, LOW);

  #if ESP_ARDUINO_VERSION_MAJOR >= 3
    ledcAttach(PIN_ENA, PWM_FREQ, PWM_RES);
  #else
    ledcSetup(PWM_CH, PWM_FREQ, PWM_RES);
    ledcAttachPin(PIN_ENA, PWM_CH);
  #endif

  // 1. INA219 (I2C: GPIO 21 SDA, GPIO 22 SCL)
  Wire.begin(21, 22);
  if (!ina219.begin()) {
    Serial.println("[-] INA219 init failed!");
    while (1) delay(10);
  }

  // 2. DS18B20 (1-Wire: GPIO 15)
  tempSensor.begin();

  // 3. ADXL345 (Hardware SPI Mode 3)
  if (!initADXL345()) {
    Serial.println("[-] ADXL345 init failed!");
    while (1) delay(10);
  }

  Serial.println("[+] INA219, DS18B20, and ADXL345 are online.");
  Serial.println("[+] Plug in 12V adapter. Motor starts in 3 seconds...");
  delay(3000);
}

void loop() {
  digitalWrite(PIN_IN1, HIGH);
  digitalWrite(PIN_IN2, LOW);

  // Ramp Up
  for (int duty = 0; duty <= 170; duty += 15) {
    setMotorSpeed(duty);
    printTelemetry("RAMP UP  ");
    delay(150);
  }

  // Steady Run for 5 seconds
  unsigned long startSteady = millis();
  while (millis() - startSteady < 5000) {
    printTelemetry("STEADY   ");
    delay(300);
  }

  // Ramp Down
  for (int duty = 170; duty >= 0; duty -= 15) {
    setMotorSpeed(duty);
    printTelemetry("RAMP DOWN");    
    delay(150);
  }

  // Idle Pause
  digitalWrite(PIN_IN1, LOW);
  digitalWrite(PIN_IN2, LOW);
  for (int i = 0; i < 3; i++) {
    printTelemetry("IDLE STOP");
    delay(800);
  }
}