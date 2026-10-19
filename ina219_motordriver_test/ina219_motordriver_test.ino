/*
 * MachinoCare - Step 4: Motor + INA219 + DS18B20 Temperature
 * Logs bus voltage, current draw, and surface temperature rise.
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
  float power_mW   = ina219.getPower_mW();
  
  // Request non-blocking or standard temp read
  tempSensor.requestTemperatures();
  float tempC = tempSensor.getTempCByIndex(0);

  if (tempC == DEVICE_DISCONNECTED_C) {
    Serial.printf("[%s] V: %5.2f V | I: %6.1f mA | P: %6.1f mW | Temp: SENSOR ERROR\n",
                  label, busVoltage, current_mA, power_mW);
  } else {
    Serial.printf("[%s] V: %5.2f V | I: %6.1f mA | P: %6.1f mW | Temp: %5.2f °C\n",
                  label, busVoltage, current_mA, power_mW, tempC);
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=== MachinoCare: Multi-Sensor Bring-Up (INA219 + DS18B20) ===");

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

  // Initialize I2C for INA219
  Wire.begin(21, 22);
  if (!ina219.begin()) {
    Serial.println("[-] Error: INA219 not detected!");
    while (1) delay(10);
  }

  // Initialize DS18B20 1-Wire
  tempSensor.begin();
  Serial.println("[+] INA219 and DS18B20 initialized.");
  Serial.println("[+] Starting test cycle in 2 seconds...");
  delay(2000);
}

void loop() {
  digitalWrite(PIN_IN1, HIGH);
  digitalWrite(PIN_IN2, LOW);

  // Ramp Up
  for (int duty = 0; duty <= 170; duty += 15) {
    setMotorSpeed(duty);
    printTelemetry("RAMP UP   ");
    delay(150);
  }

  // Steady Run for 5 seconds
  unsigned long startSteady = millis();
  while (millis() - startSteady < 5000) {
    printTelemetry("STEADY RUN");
    delay(500);
  }

  // Ramp Down
  for (int duty = 170; duty >= 0; duty -= 15) {
    setMotorSpeed(duty);
    printTelemetry("RAMP DOWN ");
    delay(150);
  }

  // Idle Pause
  digitalWrite(PIN_IN1, LOW);
  digitalWrite(PIN_IN2, LOW);
  for (int i = 0; i < 4; i++) {
    printTelemetry("IDLE STOP ");
    delay(750);
  }
}