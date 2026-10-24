/*
 * MachinoCare - Unified Telemetry Engine + Relay Remote Control + Blynk IoT
 * Actuator: RS-555 DC Motor via L298N & 2-Channel Relay
 * Sensors : INA219 (I2C), DS18B20 (1-Wire), ADXL345 (Hardware SPI Mode 3)
 */

#define BLYNK_TEMPLATE_ID "TMPL3B8QvHAyQ"
#define BLYNK_TEMPLATE_NAME "Machinocare 2.0"
#define BLYNK_AUTH_TOKEN "u3s1O3o9R0fQuM52EQ5lvGPwTr0JSLg1"

#define BLYNK_PRINT Serial
#include <WiFi.h>
#include <BlynkSimpleEsp32.h>
#include <Wire.h>
#include <SPI.h>
#include <Adafruit_INA219.h>
#include <OneWire.h>
#include <DallasTemperature.h>

// --- Wi-Fi Credentials ---
char ssid[] = "awsmre";
char pass[] = "thisisit";

// --- Relay Pins ---
#define RELAY1_PIN 32 // Master Motor Power Relay
#define RELAY2_PIN 33 // Auxiliary / Spare Relay

// Most relay boards are active-LOW
#define RELAY_ON  LOW
#define RELAY_OFF HIGH

// --- Motor Driver Pins ---
#define PIN_ENA 25
#define PIN_IN1 26
#define PIN_IN2 27

// --- Sensor Pins ---
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

const int PWM_FREQ = 5000;
const int PWM_RES  = 8;
const int PWM_CH   = 0;

Adafruit_INA219 ina219;
OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature tempSensor(&oneWire);
BlynkTimer timer;

SPISettings adxlSpiSettings(1000000, MSBFIRST, SPI_MODE3);

bool motorPowerState = false;

// --- Direct SPI Helpers ---
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

void checkAndRecoverADXL() {
  if (adxlRead(ADXL_REG_POWER_CTL) != 0x08) {
    adxlWrite(ADXL_REG_BW_RATE, 0x0A);
    adxlWrite(ADXL_REG_DATA_FORMAT, 0x0B);
    adxlWrite(ADXL_REG_POWER_CTL, 0x00);
    delayMicroseconds(50);
    adxlWrite(ADXL_REG_POWER_CTL, 0x08);
    delayMicroseconds(50);
  }
}

bool initADXL345() {
  pinMode(ADXL_CS, OUTPUT);
  digitalWrite(ADXL_CS, HIGH);
  SPI.begin(ADXL_SCK, ADXL_MISO, ADXL_MOSI, ADXL_CS);
  delay(50);

  if (adxlRead(ADXL_REG_DEVID) != 0xE5) return false;

  adxlWrite(ADXL_REG_BW_RATE, 0x0A);
  adxlWrite(ADXL_REG_DATA_FORMAT, 0x0B);
  adxlWrite(ADXL_REG_POWER_CTL, 0x00);
  delay(10);
  adxlWrite(ADXL_REG_POWER_CTL, 0x08);
  delay(10);
  return true;
}

void readRawAxes(float &ax, float &ay, float &az) {
  checkAndRecoverADXL();
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

// --- Blynk Remote Switch on V0 ---
BLYNK_WRITE(V0) {
  int switchVal = param.asInt();
  if (switchVal == 1) {
    motorPowerState = true;
    digitalWrite(RELAY1_PIN, RELAY_ON);
    digitalWrite(PIN_IN1, HIGH);
    digitalWrite(PIN_IN2, LOW);
    setMotorSpeed(170); // Drive motor at steady speed
    Serial.println("[BLYNK] Remote Motor Command: POWER ON");
  } else {
    motorPowerState = false;
    setMotorSpeed(0);
    digitalWrite(PIN_IN1, LOW);
    digitalWrite(PIN_IN2, LOW);
    digitalWrite(RELAY1_PIN, RELAY_OFF);
    Serial.println("[BLYNK] Remote Motor Command: POWER OFF / CUT");
  }
}

// Periodic Telemetry Transmission to Blynk & Serial Monitor
void sendTelemetry() {
  float busVoltage = ina219.getBusVoltage_V();
  float current_mA = ina219.getCurrent_mA();
  
  tempSensor.requestTemperatures();
  float tempC = tempSensor.getTempCByIndex(0);
  
  float vibRMS = getVibrationRMS(40);

  Serial.printf("[TELEMETRY] V: %5.2fV | I: %5.1fmA | T: %4.1f°C | Vib: %4.2f m/s² | Relay: %s\n",
                busVoltage, current_mA, tempC, vibRMS, motorPowerState ? "ON" : "OFF");

  // Push live numbers to Blynk Virtual Pins
  Blynk.virtualWrite(V1, busVoltage);
  Blynk.virtualWrite(V2, current_mA);
  if (tempC != DEVICE_DISCONNECTED_C) {
    Blynk.virtualWrite(V3, tempC);
  }
  Blynk.virtualWrite(V4, vibRMS);
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=== MachinoCare: Initializing IoT & Remote Actuation ===");

  // Setup Relay Pins (Start in OFF state)
  pinMode(RELAY1_PIN, OUTPUT);
  pinMode(RELAY2_PIN, OUTPUT);
  digitalWrite(RELAY1_PIN, RELAY_OFF);
  digitalWrite(RELAY2_PIN, RELAY_OFF);

  // Setup L298N pins
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
  setMotorSpeed(0);

  // Initialize Sensors
  Wire.begin(21, 22);
  ina219.begin();
  tempSensor.begin();
  initADXL345();

  // Connect to Blynk
  Blynk.begin(BLYNK_AUTH_TOKEN, ssid, pass);

  // Non-blocking telemetry timer (every 1 second)
  timer.setInterval(1000L, sendTelemetry);

  Serial.println("[+] System Ready. Controlled via Blynk App.");
}

void loop() {
  Blynk.run();
  timer.run();
}