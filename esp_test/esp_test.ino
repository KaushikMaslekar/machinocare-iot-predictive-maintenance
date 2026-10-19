/*
 * ESP32 Hardware Verification Test
 * Tests: Serial UART at 115200 baud & Onboard LED Blink
 */

#ifndef LED_BUILTIN
  #define LED_BUILTIN 2  // Standard onboard blue LED on ESP-WROOM-32
#endif

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  pinMode(LED_BUILTIN, OUTPUT);
  Serial.println("\n--- ESP32 Hardware Self-Test ---");
  Serial.println("System Core Clock: " + String(getCpuFrequencyMhz()) + " MHz");
  Serial.println("Chip Model: " + String(ESP.getChipModel()));
  Serial.println("Flash Chip Size: " + String(ESP.getFlashChipSize() / (1024 * 1024)) + " MB");
  Serial.println("Blinking sequence active...\n");
}

void loop() {
  digitalWrite(LED_BUILTIN, HIGH);
  Serial.println("LED Status: ON");
  delay(1000);

  digitalWrite(LED_BUILTIN, LOW);
  Serial.println("LED Status: OFF");
  delay(1000);
}