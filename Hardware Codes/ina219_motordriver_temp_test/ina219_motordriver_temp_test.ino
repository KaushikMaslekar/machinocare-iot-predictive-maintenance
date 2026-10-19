/*
 * MachinoCare - Motor Driver + INA219 (Current) + DS18B20 (Temperature)
 */

#include <Wire.h>
#include <Adafruit_INA219.h>
#include <OneWire.h>
#include <DallasTemperature.h>

// Motor Driver Pins
#define PIN_ENA 25
#define PIN_IN1 26
#define PIN_IN2 27

// DS18B20 Data Pin
#define ONE_WIRE_BUS 15

// PWM Configuration
const int PWM_FREQ = 5000;
const int PWM_RES  = 8;
const int PWM_CH   = 0;

Adafruit_INA219 ina219;
OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature tempSensor(&oneWire);

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

  if (tempC == DEVICE_DISCONNECTED_C) {
    Serial.printf("[%s] V: %5.2fV | I: %5.1fmA | T: SENSOR ERROR\n",
                  label, busVoltage, current_mA);
  } else {
    Serial.printf("[%s] V: %5.2fV | I: %5.1fmA | T: %4.1f°C\n",
                  label, busVoltage, current_mA, tempC);
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=== MachinoCare: Dual-Sensor Bring-Up (INA219 + DS18B20) ===");

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

  // I2C Bus for INA219 (GPIO 21 SDA, GPIO 22 SCL)
  Wire.begin(21, 22);
  if (!ina219.begin()) {
    Serial.println("[-] INA219 failed! Check SDA/SCL wiring.");
    while (1) delay(10);
  }

  // 1-Wire Temp Sensor on GPIO 15
  tempSensor.begin();

  Serial.println("[+] INA219 and DS18B20 initialized successfully.");
  Serial.println("[+] Plug in 12V adapter now. Motor starts in 3 seconds...");
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