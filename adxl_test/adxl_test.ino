/*
 * MachinoCare - Standalone ADXL345 Hardware SPI Test
 * Wiring:
 *   ADXL VCC  -> ESP32 3V3
 *   ADXL GND  -> ESP32 GND
 *   ADXL CS   -> GPIO 5
 *   ADXL SCL  -> GPIO 18 (SCK)
 *   ADXL SDA  -> GPIO 23 (MOSI)
 *   ADXL SDO  -> GPIO 19 (MISO)
 */

#include <SPI.h>

#define ADXL_CS   5
#define ADXL_SCK  18
#define ADXL_MISO 19
#define ADXL_MOSI 23

// ADXL345 Registers
#define ADXL_REG_DEVID       0x00
#define ADXL_REG_BW_RATE     0x2C
#define ADXL_REG_POWER_CTL   0x2D
#define ADXL_REG_DATA_FORMAT 0x31
#define ADXL_REG_DATAX0      0x32
#define ADXL_SPI_READ        0x80

// Hardware SPI settings: 1 MHz, MSBFIRST, SPI Mode 3
SPISettings adxlSpiSettings(1000000, MSBFIRST, SPI_MODE3);

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

bool initADXL345() {
  pinMode(ADXL_CS, OUTPUT);
  digitalWrite(ADXL_CS, HIGH);

  SPI.begin(ADXL_SCK, ADXL_MISO, ADXL_MOSI, ADXL_CS);
  delay(50);

  // Read Chip ID
  uint8_t devId = adxlRead(ADXL_REG_DEVID);
  Serial.printf("Sensor ID Read: 0x%02X (Expected: 0xE5)\n", devId);
  if (devId != 0xE5) {
    return false;
  }

  // Set output data rate to 100 Hz
  adxlWrite(ADXL_REG_BW_RATE, 0x0A);

  // Set measurement range: +/-16g, Full Resolution
  adxlWrite(ADXL_REG_DATA_FORMAT, 0x0B);

  // Exit standby and enter measurement mode
  adxlWrite(ADXL_REG_POWER_CTL, 0x00);
  delay(10);
  adxlWrite(ADXL_REG_POWER_CTL, 0x08);
  delay(10);

  return true;
}

void readRawAxes(float &ax, float &ay, float &az) {
  uint8_t x0 = adxlRead(0x32);
  uint8_t x1 = adxlRead(0x33);
  uint8_t y0 = adxlRead(0x34);
  uint8_t y1 = adxlRead(0x35);
  uint8_t z0 = adxlRead(0x36);
  uint8_t z1 = adxlRead(0x37);

  int16_t rawX = (int16_t)((x1 << 8) | x0);
  int16_t rawY = (int16_t)((y1 << 8) | y0);
  int16_t rawZ = (int16_t)((z1 << 8) | z0);

  // Scale: 3.9 mg/LSB * 9.80665 m/s^2
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

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n--- ADXL345 Direct SPI Test ---");

  if (!initADXL345()) {
    Serial.println("[-] Sensor initialization failed! Check wiring & pin contact.");
    while (1) delay(10);
  }

  Serial.println("[+] Sensor active in measurement mode!\n");
}

void loop() {
  float ax, ay, az;
  readRawAxes(ax, ay, az);
  float vibRMS = getVibrationRMS(40);

  Serial.printf("X: %5.2f | Y: %5.2f | Z: %5.2f m/s² | Vib RMS: %4.2f m/s²\n",
                ax, ay, az, vibRMS);

  delay(250);
}