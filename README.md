# MachinoCare: Edge-AI Multi-Sensor Condition Monitoring & Fault Diagnosis System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MQTT](https://img.shields.io/badge/MQTT-Eclipse%20Mosquitto-red.svg)](https://mosquitto.org/)
[![Database](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://www.docker.com/)

**MachinoCare** is an industrial condition-monitoring and predictive maintenance pipeline designed to detect electrical and mechanical degradation in **12V DC motors (RS-550 / RS-555)** in real time. Rather than relying on single-threshold alerts, MachinoCare correlates multi-sensor telemetry (vibration frequency spectra, electrical load, casing temperature, and rotational speed) with Edge-ML inference to diagnose root causes before catastrophic failure occurs.

---

## 1. Architectural Boundary & Team Ownership

MachinoCare enforces a strict, first-class contract boundary between hardware edge computing and enterprise cloud/backend infrastructure:

```text
========================================================================================
   EDGE COMPUTING PIPELINE (Edge Team)
   Sensors -> ESP32 Data Acquisition -> Signal Conditioning -> FFT & Features -> Edge ML
========================================================================================
                                           │
                                           │ MQTT over Wi-Fi
                                           ▼
========================================================================================
   FIRST-CLASS DATA CONTRACT BOUNDARY (Topics, JSON Schemas, Units, QoS, LWT)
   docs/contracts/edge-iot-data-contract.md
========================================================================================
                                           │
                                           ▼
========================================================================================
   IoT & BACKEND PIPELINE (My Scope)
   Broker -> Spring Integration -> Validation -> Domain Services -> PostgreSQL -> REST APIs -> Dashboard
========================================================================================
```

---

## 2. Comprehensive System Architecture

### 7-Layer End-to-End Architectural Blueprint

```text
┌─ 1. PHYSICAL MACHINE LAYER
│  12V DC Motor (RS-555 / RS-550) + 20-slot encoder disc on shaft
│  Physical signals: mechanical vibration | electrical load | thermal heat
└──────────────────────────────┬───────────────────────────────────
                               ▼
┌─ 2. SENSOR LAYER
│  ADXL345  -> Vibration (X/Y/Z)        [I2C/SPI]
│  INA219   -> Current + Voltage        [I2C]
│  DS18B20  -> Temperature              [1-Wire]
│  LM393    -> Optical RPM pulses       [GPIO interrupt, via 20-slot disc]
└──────────────────────────────┬───────────────────────────────────
                               ▼
┌─ 3. EDGE DEVICE LAYER  (ESP32-WROOM-32) - [EDGE TEAM]
│
│  3.1 Data Acquisition
│      Read ADXL345, INA219, DS18B20, LM393
│          ▼
│  3.2 Signal Processing
│      Sampling > Timestamping > Filtering > Windowing > Normalization
│          ▼
│  3.3 Feature Extraction
│      Vibration : RMS, Peak, Peak-to-Peak, Std Dev, Crest Factor,
│                  FFT / Dominant Frequency, Frequency-band energy
│      Other     : Current, Voltage, Temperature, RPM
│          ▼
│  3.4 Edge ML Inference (lightweight model, multi-sensor feature input)
│      Output classes: NORMAL | IMBALANCE | LOOSE MOUNTING |
│                      LOAD-RELATED ABNORMALITY
│          ▼
│  3.5 Machine Condition Result
└──────────────────────────────┬───────────────────────────────────
                               │  Wi-Fi / MQTT
                               ▼
┌─ 4. IoT / COMMUNICATION LAYER          - [MY RESPONSIBILITY]
│  MQTT Broker (Eclipse Mosquitto): telemetry + condition results
└──────────────────────────────┬───────────────────────────────────
                               ▼
┌─ 5. BACKEND LAYER (Spring Boot API)   - [MY RESPONSIBILITY]
│  - Device / Machine Management
│  - Telemetry Ingestion
│  - Condition / Fault Results
│  - Alert / Event Management
│  - Historical Data APIs
└──────────────────────────────┬───────────────────────────────────
                               ▼
┌─ 6. DATA STORAGE LAYER (PostgreSQL)   - [MY RESPONSIBILITY]
│  Machine Information | Sensor Metadata | Telemetry | Extracted Features
│  Classification Results | Fault Events | Alerts | Historical Measurements
└──────────────────────────────┬───────────────────────────────────
                               ▼
┌─ 7. PRESENTATION LAYER (Web Dashboard)
│  Live Monitoring   : Vibration, Current, Temperature, RPM
│  Machine Health    : Normal | Warning | Abnormal
│  Fault Detection   : Fault type, Confidence, Timestamp, Alert
│  Also              : Historical Trends, FFT Visualization, Alerts, Reports
└────────────────────────────────────────────────────────────────
```

### Visual Component & Data Flow Diagram

The following Mermaid diagram details the end-to-end data flow, execution threads, and infrastructure components across these 7 layers:

```mermaid
flowchart TD
    subgraph L1 ["1. PHYSICAL MACHINE LAYER"]
        M1["12V DC Motor (RS-555 / RS-550) + 20-slot encoder disc on shaft<br/>Physical signals: mechanical vibration | electrical load | thermal heat"]
    end

    subgraph L2 ["2. SENSOR LAYER"]
        direction LR
        S_ADXL["ADXL345<br/>Vibration (X/Y/Z)<br/>[I2C/SPI]"]
        S_INA["INA219<br/>Current + Voltage<br/>[I2C]"]
        S_DS["DS18B20<br/>Temperature<br/>[1-Wire]"]
        S_LM["LM393<br/>Optical RPM pulses<br/>[GPIO via 20-slot disc]"]
    end

    subgraph L3 ["3. EDGE DEVICE LAYER (ESP32-WROOM-32) - [EDGE TEAM]"]
        direction TB
        E1["3.1 Data Acquisition<br/>Read ADXL345, INA219, DS18B20, LM393"]
        E2["3.2 Signal Processing<br/>Sampling > Timestamping > Filtering > Windowing > Normalization"]
        E3["3.3 Feature Extraction<br/>Vibration: RMS, Peak, Peak-to-Peak, Std Dev, Crest Factor, FFT / Dominant Freq, Band Energy<br/>Other: Current, Voltage, Temperature, RPM"]
        E4["3.4 Edge ML Inference (lightweight multi-sensor model)<br/>Output classes: NORMAL | IMBALANCE | LOOSE MOUNTING | LOAD-RELATED ABNORMALITY"]
        E5["3.5 Machine Condition Result"]

        E1 --> E2 --> E3 --> E4 --> E5
    end

    subgraph L4 ["4. IoT / COMMUNICATION LAYER - [MY RESPONSIBILITY]"]
        MQTT["MQTT Broker (Eclipse Mosquitto)<br/>Telemetry + Condition Results"]
    end

    subgraph L5 ["5. BACKEND LAYER (Spring Boot API) - [MY RESPONSIBILITY]"]
        direction TB
        B1["5.1 Device / Machine Management<br/>• Use: Maps ESP32 hardware to RS-550 motor & tracks connection lifecycle<br/>• Values: ONLINE/OFFLINE state, IP address, Wi-Fi RSSI, firmware version, LWT disconnects"]
        
        B2["5.2 Telemetry Ingestion<br/>• Use: Validates, sanitizes, and buffers high-frequency multi-sensor streams<br/>• Values: Voltage (V), Current (A), Power (W), Temp (°C), RPM, Vib RMS, Peak, Crest Factor, FFT Dominant Freq (Hz)"]
        
        B3["5.3 Condition / Fault Ingestion<br/>• Use: Ingests real-time Edge ML classification inferences and anomaly confidence<br/>• Values: Condition Label (NORMAL, IMBALANCE, LOOSE_MOUNTING, LOAD_ANOMALY), Confidence Score (0-1.0)"]
        
        B4["5.4 Alert / Event Management<br/>• Use: Debounces transient electrical/vibration noise & manages active fault incidents<br/>• Values: Lifecycle (ACTIVE → ACKNOWLEDGED → RESOLVED), Severity (WARNING / CRITICAL), Incident Notes"]
        
        B5["5.5 Historical Data APIs<br/>• Use: Powers frontend dashboard with time-range analytics and real-time live push<br/>• Values: Historical trend curves, FFT spectrum arrays, motor health score, WebSocket live feed"]
    end

    subgraph L6 ["6. DATA STORAGE LAYER (PostgreSQL 16) - [MY RESPONSIBILITY]"]
        DB[("PostgreSQL Database<br/>machines | devices | telemetry_records | condition_records | fault_alerts<br/>(Composite B-Tree Indexes on timestamp)")]
    end

    subgraph L7 ["7. PRESENTATION LAYER (Web Dashboard)"]
        direction TB
        D1["Live Monitoring: Vibration, Current, Temperature, RPM"]
        D2["Machine Health: Normal | Warning | Abnormal"]
        D3["Fault Detection: Fault type, Confidence, Timestamp, Alert"]
        D4["Analytics: Historical Trends, FFT Visualization, Alerts, Reports"]
    end

    M1 --> S_ADXL & S_INA & S_DS & S_LM
    S_ADXL & S_INA & S_DS & S_LM --> E1
    E5 -->|"Wi-Fi / MQTT"| MQTT
    MQTT -->|"status topic"| B1
    MQTT -->|"telemetry topic"| B2
    MQTT -->|"condition topic"| B3
    B3 -->|"trigger alert"| B4
    B1 & B2 & B4 -->|"persist"| DB
    DB -->|"query"| B5
    B5 -->|"REST APIs / WebSocket Push"| D1 & D2 & D3 & D4
```

---

## 3. Sensor Suite & Diagnostic Correlation

Multi-sensor correlation prevents false positives. For example, increased current alone might simply mean the motor is working harder under a normal workload, but **increased current paired with dropping RPM and rising temperature indicates mechanical binding or bearing overload**:

| Sensor | Physical Metric | Edge-Extracted Features | Diagnostic Relevance |
|---|---|---|---|
| **ADXL345** | Acceleration ($g$) | RMS, Peak, Crest Factor, Dominant Freq (FFT) | Structural looseness, rotor mass imbalance, bearing impact rattling |
| **INA219** | Bus Voltage ($V$), Current ($A$) | Voltage, Armature Current, Real Power ($W$) | Torque demand ($\text{Torque} \propto I$), electrical load spikes, stall warnings |
| **DS18B20** | Temperature ($^\circ\text{C}$) | Casing Surface Temperature | Long-term $I^2R$ thermal dissipation, cooling failure, friction heat |
| **LM393** | Shaft RPM (20-slot disc) | Rotational Speed (rev/min) | Back-EMF equilibrium ($\text{Back-EMF} \propto \text{Speed}$), slip detection |

### Diagnostic Condition Labels (Edge ML Output)
* **`NORMAL`**: Nominal electrical draw, stable operating temperature, low vibration RMS ($<0.4g$).
* **`IMBALANCE`**: 1X rotational harmonic spike in FFT, elevated vibration RMS ($>1.0g$), stable electrical load.
* **`LOOSE_MOUNTING`**: High Crest Factor ($>3.0$), impact sub-harmonics from mechanical rattling/chatter.
* **`LOAD_RELATED_ABNORMALITY`**: Increased mechanical resistance $\rightarrow$ RPM drops $\rightarrow$ Back-EMF drops $\rightarrow$ Current surges $\rightarrow$ Casing temperature climbs rapidly.

---

## 4. MQTT Topic Taxonomy & Quality of Service (QoS)

| Topic | QoS | Retained | Frequency | Payload Description |
|---|---|---|---|---|
| `machinocare/v1/devices/{deviceId}/status` | **1** | **true** | On connect / LWT | Device presence (`ONLINE`/`OFFLINE`), IP, Wi-Fi RSSI, LWT payload |
| `machinocare/v1/devices/{deviceId}/telemetry` | **0** | **false** | Periodic (1000 ms) | Time-series electrical, thermal, RPM, and vibration summary features |
| `machinocare/v1/devices/{deviceId}/condition` | **1** | **false** | On inference / event | ML condition label, statistical confidence, and anomaly score |

---

## 5. Technology Stack & Key Decisions

* **Language & Runtime:** Java 21 (LTS) — utilizing modern Records, pattern matching, and Virtual Threads capability.
* **Backend Framework:** Spring Boot 3.3.4 (Spring Web, Spring Integration MQTT, Spring Data JPA, Jakarta Bean Validation).
* **Messaging Broker:** Eclipse Mosquitto 2.0 (Containerized via Docker Compose).
* **Database & Migration:** PostgreSQL 16 (Alpine) with Flyway database migration versioning (`V1__init_schema.sql`).
* **Connection Pooling:** HikariCP for high-throughput, low-latency database connection reuse.
* **Edge Simulation:** Python 3.14 with `paho-mqtt` 2.1 for hardware-in-the-loop mocking and physics emulation.

---

## 6. Project Directory Structure

```text
MachinoCare - Predictive maintainance/
├── README.md                               <-- Master Project Documentation
├── .gitignore                              <-- Git exclusions (Maven, Python, IDE)
├── docs/
│   └── contracts/
│       └── edge-iot-data-contract.md       <-- Formal Data Contract Specification v1.0.0
├── docker/
│   ├── docker-compose.yml                  <-- Mosquitto (port 1883) & PostgreSQL (port 5432)
│   └── mosquitto/
│       └── config/
│           └── mosquitto.conf              <-- Broker configuration (listeners, logging)
├── backend/                                <-- Spring Boot 3 / Java 21 Service
│   ├── pom.xml                             <-- Maven dependencies and build plugins
│   └── src/
│       ├── main/
│       │   ├── java/com/machinocare/
│       │   │   └── MachinoCareApplication.java
│       │   └── resources/
│       │       └── application.yml         <-- Central externalized configuration
│       └── test/
└── simulator/                              <-- Edge Hardware Testing & Mock Tools
    ├── requirements.txt                    <-- Python dependencies (paho-mqtt)
    ├── edge_simulator.py                   <-- Physics-based RS-550 DC motor simulator
    └── live_listener.py                    <-- Real-time CLI MQTT packet sniffer
```

---

## 7. Quick Start & Local Execution

### 1. Start Infrastructure (Docker)
Ensure Docker Desktop is running, then execute:
```powershell
cd docker
docker compose up -d
```
*Verify both containers are running:*
```powershell
docker compose ps
```

### 2. Verify Ports
* **Mosquitto (MQTT):** `localhost:1883`
* **PostgreSQL:** `localhost:5432` (`POSTGRES_DB=machinocare`, `POSTGRES_USER=postgres`, `POSTGRES_PASSWORD=postgres`)

### 3. Run the Hardware Simulator / Packet Listener (Optional Testing)
* To monitor live MQTT traffic:
  ```powershell
  python simulator/live_listener.py
  ```
* To simulate motor physics and inject faults:
  ```powershell
  python simulator/edge_simulator.py --mode cycle
  ```

### 4. Build & Run the Backend
```powershell
cd backend
mvn clean compile
mvn spring-boot:run
```

---

## 8. Cross-Machine Hardware Testing Guide (Windows $\leftrightarrow$ Mac / ESP32)

When integrating with physical ESP32 hardware or cross-platform team laptops:
1. Connect both computers (Windows host and Mac/ESP32) to the **same Wi-Fi or Mobile Hotspot** (avoid university Wi-Fi with client isolation).
2. Obtain the host Windows Wi-Fi IP via `ipconfig`.
3. Allow incoming TCP traffic on port 1883 via Windows PowerShell (Admin):
   ```powershell
   New-NetFirewallRule -DisplayName "MachinoCare MQTT" -Direction Inbound -LocalPort 1883 -Protocol TCP -Action Allow
   ```
4. Configure the ESP32 firmware to point to the host Windows IP on port 1883 with buffer size set to `1024` bytes:
   ```cpp
   mqttClient.setServer(hostIp, 1883);
   mqttClient.setBufferSize(1024);
   ```
