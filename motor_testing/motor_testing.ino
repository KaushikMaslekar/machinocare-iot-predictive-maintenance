/*
 * ESP32 Hardware I2C Scanner
 * Scans default I2C pins (SDA=21, SCL=22) to locate connected chip addresses.
 */

#include <Wire.h>

void setup() {
  Serial.begin(115200);
  while (!Serial); // Wait for Serial Monitor to open
  delay(1000);
  
  Serial.println("\n--- ESP32 I2C Scanner ---");
  Serial.println("Initializing I2C Bus on SDA=21, SCL=22...");
  
  Wire.begin(21, 22); // Explicitly set hardware default pins
}

void loop() {
  byte error, address;
  int nDevices = 0;

  Serial.println("Scanning bus...");

  for (address = 1; address < 127; address++ ) {
    // Probe current address on bus
    Wire.beginTransmission(address);
    error = Wire.endTransmission();

    if (error == 0) {
      Serial.print("[+] Found device at address 0x");
      if (address < 16) {
        Serial.print("0");
      }
      Serial.print(address, HEX);
      
      // Standard INA219 default factory address is 0x40
      if (address == 0x40) {
        Serial.println(" (Identified: INA219!)");
      } else {
        Serial.println("");
      }

      nDevices++;
    }
    else if (error == 4) {
      Serial.print("[-] Unknown error at address 0x");
      if (address < 16) {
        Serial.print("0");
      }
      Serial.println(address, HEX);
    }    
  }
  
  if (nDevices == 0) {
    Serial.println("[-] No I2C devices found. Check your wiring!\n");
  } else {
    Serial.println("[+] Done.\n");
  }

  delay(3000); // Wait 3 seconds and scan again
}