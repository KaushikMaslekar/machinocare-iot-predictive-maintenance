# MachinoCare: Edge-to-IoT Data Contract Specification (v1.0.0)

## 1. Scope and Boundary Definition

This document defines the **first-class interface contract** between:
1. **Edge Pipeline (ESP32 Firmware & Edge ML)**: Data acquisition from sensors (ADXL345, INA219, DS18B20, LM393), signal conditioning, feature extraction, and ML classification.
2. **IoT & Backend Pipeline (Spring Boot & MongoDB 7.0)**: MQTT ingestion, payload validation, business logic, persistence, alerting, and dashboard APIs.

Any change to topics, payload structure, units, sampling frequency, or enum values constitutes a **breaking contract change** and must be versioned.

---

## 2. MQTT Topic Taxonomy and Delivery Semantics

Base Namespace: `machinocare/v1/devices/{deviceId}/{messageType}`

| Message Type | Full Topic Path | QoS | Retained | Frequency / Trigger | Purpose |
|---|---|---|---|---|---|
| **Status / Lifecycle** | `machinocare/v1/devices/{deviceId}/status` | **1** | **true** | On boot, shutdown, and ungraceful disconnect (LWT) | Device presence, IP, RSSI, and connectivity tracking |
| **Telemetry & Features** | `machinocare/v1/devices/{deviceId}/telemetry` | **0** | **false** | Periodic (Default: 1000 ms) | Time-series electrical, thermal, rotational, and vibration window features |
| **Condition Inference** | `machinocare/v1/devices/{deviceId}/condition` | **1** | **false** | On inference cycle (e.g. 2000–5000 ms) or state transition | Machine health classification label, confidence, anomaly score |

### Subscription Patterns (Backend Consumers)
* `machinocare/v1/devices/+/status` — Ingests presence events across all devices.
* `machinocare/v1/devices/+/telemetry` — Ingests time-series sensor readings.
* `machinocare/v1/devices/+/condition` — Ingests ML classification results and drives alert triggers.
* `machinocare/v1/devices/#` — Wildcard for all messages (used in staging or debugging).

---

## 3. Timestamp Semantics & Time Synchronization

* **Format**: ISO-8601 UTC with millisecond precision: `YYYY-MM-DDTHH:mm:ss.sssZ` (e.g., `2026-10-19T00:30:00.125Z`).
* **Time Source**: ESP32 synchronizes with NTP on Wi-Fi connection.
* **Fallback Behavior**:
  * If the ESP32 clock is un-synchronized at boot, `timeSynced` flag must be set to `false`.
  * The backend always records an internal ingestion timestamp `receivedAt` (UTC) upon arrival to ensure strict monotonic ordering and latency auditing.

---

## 4. Message Schemas & Field Dictionaries

### 4.1 Device Status / Lifecycle Payload (`.../status`)

#### JSON Example
```json
{
  "contractVersion": "1.0",
  "deviceId": "ESP32-001",
  "machineId": "MOTOR-001",
  "status": "ONLINE",
  "reason": "BOOT",
  "firmwareVersion": "1.0.0",
  "ipAddress": "192.168.1.105",
  "wifiRssi": -62,
  "timeSynced": true,
  "timestamp": "2026-10-19T00:30:00.000Z"
}
```

#### Last Will and Testament (LWT) Registration
Configured by the ESP32 in its initial MQTT `CONNECT` packet:
* **Topic**: `machinocare/v1/devices/{deviceId}/status`
* **QoS**: `1`
* **Retain**: `true`
* **Payload**:
```json
{
  "contractVersion": "1.0",
  "deviceId": "ESP32-001",
  "machineId": "MOTOR-001",
  "status": "OFFLINE",
  "reason": "UNEXPECTED_DISCONNECT",
  "timestamp": null
}
```

#### Field Dictionary
| Field | Type | Required | Description / Valid Values |
|---|---|---|---|
| `contractVersion` | String | Yes | Contract version identifier (e.g., `"1.0"`). |
| `deviceId` | String | Yes | Unique identifier of the ESP32 hardware. |
| `machineId` | String | Yes | Identifier of the physical motor unit (RS-555 / RS-550). |
| `status` | String | Yes | `"ONLINE"` or `"OFFLINE"`. |
| `reason` | String | Yes | `"BOOT"`, `"SHUTDOWN"`, or `"UNEXPECTED_DISCONNECT"`. |
| `firmwareVersion` | String | Yes | Semantic version string of ESP32 firmware. |
| `ipAddress` | String | No | Local IP address for diagnostic access. |
| `wifiRssi` | Integer | No | Wi-Fi signal level in **dBm** (e.g., `-65`). |
| `timeSynced` | Boolean | Yes | `true` if ESP32 RTC has been synchronized with NTP. |
| `timestamp` | String | Yes | ISO-8601 UTC timestamp. |

---

### 4.2 Sensor Telemetry & Extracted Features Payload (`.../telemetry`)

#### JSON Example
```json
{
  "contractVersion": "1.0",
  "deviceId": "ESP32-001",
  "machineId": "MOTOR-001",
  "timestamp": "2026-10-19T00:30:01.000Z",
  "electrical": {
    "voltage": 12.08,
    "current": 1.45,
    "power": 17.52
  },
  "thermal": {
    "temperature": 43.2
  },
  "rotational": {
    "rpm": 2820.0
  },
  "vibration": {
    "rms": 0.36,
    "peak": 0.88,
    "crestFactor": 2.44,
    "dominantFrequency": 47.0
  },
  "sensorStatus": {
    "ina219": "OK",
    "ds18b20": "OK",
    "lm393": "OK",
    "adxl345": "OK"
  }
}
```

#### Field Dictionary & Physical Units
| Category | Field | Type | Unit | Nullable | Description |
|---|---|---|---|---|---|
| **Root** | `contractVersion` | String | — | No | Fixed `"1.0"` |
| | `deviceId` | String | — | No | Hardware ID |
| | `machineId` | String | — | No | Monitored physical motor ID |
| | `timestamp` | String | — | No | ISO-8601 UTC timestamp |
| **Electrical (INA219)** | `electrical.voltage` | Float | **V** | Yes | Bus supply voltage |
| | `electrical.current` | Float | **A** | Yes | Motor current (load/torque indicator) |
| | `electrical.power` | Float | **W** | Yes | Real power consumed ($V \times I$) |
| **Thermal (DS18B20)** | `thermal.temperature` | Float | **°C** | Yes | Motor surface temperature |
| **Rotational (LM393)** | `rotational.rpm` | Float | **RPM** | Yes | Shaft rotational speed (20-slot disc) |
| **Vibration (ADXL345)**| `vibration.rms` | Float | **g** | Yes | Root Mean Square acceleration |
| | `vibration.peak` | Float | **g** | Yes | Maximum peak amplitude in window |
| | `vibration.crestFactor`| Float | — | Yes | Ratio: $\text{Peak} / \text{RMS}$ (spikiness/shock) |
| | `vibration.dominantFrequency` | Float | **Hz** | Yes | Dominant peak frequency from FFT |
| **Sensor Status** | `sensorStatus.ina219` | String | — | No | `"OK"`, `"DISCONNECTED"`, or `"ERROR"` |
| | `sensorStatus.ds18b20` | String | — | No | `"OK"`, `"DISCONNECTED"`, or `"ERROR"` |
| | `sensorStatus.lm393` | String | — | No | `"OK"`, `"DISCONNECTED"`, or `"ERROR"` |
| | `sensorStatus.adxl345` | String | — | No | `"OK"`, `"DISCONNECTED"`, or `"ERROR"` |

---

### 4.3 Machine Condition Classification Payload (`.../condition`)

#### JSON Example
```json
{
  "contractVersion": "1.0",
  "deviceId": "ESP32-001",
  "machineId": "MOTOR-001",
  "timestamp": "2026-10-19T00:30:01.050Z",
  "condition": "NORMAL",
  "confidence": 0.94,
  "inferenceTimeMs": 12.5,
  "anomalyScore": 0.06
}
```

#### Field Dictionary
| Field | Type | Required | Values / Format | Description |
|---|---|---|---|---|
| `contractVersion` | String | Yes | `"1.0"` | Schema version |
| `deviceId` | String | Yes | String | Hardware ID |
| `machineId` | String | Yes | String | Monitored physical motor ID |
| `timestamp` | String | Yes | ISO-8601 UTC | Inference window end timestamp |
| `condition` | String | Yes | Enum: `"NORMAL"`, `"IMBALANCE"`, `"LOOSE_MOUNTING"`, `"LOAD_RELATED_ABNORMALITY"` | Edge ML classification result |
| `confidence` | Float | Yes | `0.00` to `1.00` | Statistical confidence of model |
| `inferenceTimeMs`| Float | No | Milliseconds | Execution time of inference on ESP32 |
| `anomalyScore` | Float | No | `0.00` to `1.00` | Normalized distance or loss metric |

---

## 5. Sensor Error Handling Protocol

When a physical sensor fails, disconnects, or encounters an I2C/1-Wire bus error:
1. The ESP32 must **NOT** transmit dummy values like `0.0`, `-1.0`, or `-999.0` for measured quantities.
2. The measurement value must be set to `null`.
3. The corresponding entry in `sensorStatus` must be set to `"DISCONNECTED"` or `"ERROR"`.

**Example:** If the DS18B20 temperature probe is disconnected:
```json
{
  "thermal": {
    "temperature": null
  },
  "sensorStatus": {
    "ina219": "OK",
    "ds18b20": "DISCONNECTED",
    "lm393": "OK",
    "adxl345": "OK"
  }
}
```
This enables the backend to safely raise a sensor hardware alert rather than recording a false $0^\circ\text{C}$ temperature reading.
