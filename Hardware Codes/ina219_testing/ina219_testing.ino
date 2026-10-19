#include <Wire.h>
#include <Adafruit_INA219.h>

#define PIN_ENA 25  // PWM speed pin
#define PIN_IN1 26  // Direction pin A
#define PIN_IN2 27  // Direction pin B

const int PWM_FREQ = 5000;
const int PWM_RES  = 8;
const int PWM_CH   = 0;

Adafruit_INA219 ina219;

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

  Serial.printf("[%s] Voltage: %5.2f V | Current: %6.1f mA | Power: %6.1f mW\n",
                label, busVoltage, current_mA, power_mW);
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=== MachinoCare: Motor + INA219 Telemetry ===");

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

  Wire.begin(21, 22);
  if (!ina219.begin()) {
    Serial.println("[-] INA219 initialization failed!");
    while (1) delay(10);
  }

  Serial.println("[+] INA219 connected and verified.");
  Serial.println("[+] Plug in the 12V adapter now. Motor starts in 3 seconds...");
  delay(3000);
}

void loop() {
  // Direction: Forward
  digitalWrite(PIN_IN1, HIGH);
  digitalWrite(PIN_IN2, LOW);

  // 1. Soft-start ramp up
  for (int duty = 0; duty <= 170; duty += 10) {
    setMotorSpeed(duty);
    printTelemetry("RAMP UP   ");
    delay(100);
  }

  // 2. Steady run at ~65% speed for 4 seconds
  unsigned long startSteady = millis();
  while (millis() - startSteady < 4000) {
    printTelemetry("STEADY RUN");
    delay(250);
  }

  // 3. Ramp down
  for (int duty = 170; duty >= 0; duty -= 10) {
    setMotorSpeed(duty);
    printTelemetry("RAMP DOWN ");
    delay(100);
  }

  // 4. Standstill idle
  digitalWrite(PIN_IN1, LOW);
  digitalWrite(PIN_IN2, LOW);
  for (int i = 0; i < 4; i++) {
    printTelemetry("IDLE STOP ");
    delay(500);
  }
}