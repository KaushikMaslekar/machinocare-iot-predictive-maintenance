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

The following Mermaid diagram details the end-to-end data flow, execution threads, and infrastructure components across all layers:

```mermaid
flowchart TD
    subgraph PHYSICAL ["1. Physical Machine Layer"]
        MOTOR["12V DC Motor (RS-550 / RS-555)"]
        SHAFT["Rotating Shaft & Mechanical Load"]
        MOTOR --> SHAFT
    end

    subgraph SENSORS ["2. Sensor Instrumentation Layer"]
        ADXL["ADXL345 Accelerometer<br/>(Tri-axial Vibration X/Y/Z)"]
        INA["INA219 Current & Voltage Sensor<br/>(Bus V, Shunt mA, Power)"]
        DS["DS18B20 1-Wire Probe<br/>(Motor Casing Temp °C)"]
        LM["LM393 Optical Module<br/>(20-Slot Encoder Disc Pulse Count)"]
        
        SHAFT -.->|"Vibration Energy"| ADXL
        MOTOR -.->|"Armature Current / Back-EMF"| INA
        MOTOR -.->|"Thermal Conduction"| DS
        SHAFT -.->|"Optical Transitions"| LM
    end

    subgraph EDGE ["3. Edge Device Layer (ESP32-WROOM-32) - Edge Team"]
        direction TB
        ACQ["High-Frequency Sampling & ADC"]
        SIG["Bandpass Filtering & Windowing"]
        FFT["FFT & Statistical Feature Extraction<br/>(RMS, Peak, Crest Factor, Dominant Freq)"]
        ML["Edge ML Inference Engine<br/>(TinyML Random Forest / SVM)"]
        SER["JSON Serialization & LWT Manager<br/>(ArduinoJson / PubSubClient)"]

        ADXL --> ACQ
        INA --> ACQ
        DS --> ACQ
        LM --> ACQ
        ACQ --> SIG --> FFT --> ML --> SER
    end

    subgraph CONTRACT ["4. Data Contract Boundary (v1.0.0)"]
        TOPIC_STATUS["machinocare/v1/devices/{id}/status<br/>(QoS 1, Retained, LWT)"]
        TOPIC_TELEM["machinocare/v1/devices/{id}/telemetry<br/>(QoS 0, 1000ms Cadence)"]
        TOPIC_COND["machinocare/v1/devices/{id}/condition<br/>(QoS 1, State Changes)"]
        
        SER --> TOPIC_STATUS
        SER --> TOPIC_TELEM
        SER --> TOPIC_COND
    end

    subgraph IOT ["5. IoT Messaging Layer (Docker)"]
        MOSQ["Eclipse Mosquitto MQTT Broker<br/>(Port 1883 TCP, Client Isolation Handling)"]
        TOPIC_STATUS --> MOSQ
        TOPIC_TELEM --> MOSQ
        TOPIC_COND --> MOSQ
    end

    subgraph BACKEND ["6. Backend Pipeline (Spring Boot 3.3.4 / Java 21)"]
        direction TB
        PAHO["MqttPahoMessageDrivenChannelAdapter<br/>(Eclipse Paho Client Pool)"]
        CHAN["Spring Integration MessageChannel<br/>(Async Worker Thread Pool)"]
        VAL["DTO Deserializer & Jakarta Bean Validator<br/>(@Valid, Range Checks, Error Flags)"]
        ROUTER["Inbound Message Router & Coordinator"]
        
        subgraph SERVICES ["Domain Business Logic Layer"]
            DEVSVC["Device Lifecycle & Presence Service"]
            TELEMSVC["Telemetry Ingestion & Aggregation Service"]
            ALERTSVC["Alarm Engine & Anomaly State Machine<br/>(Debouncing, Severity Escalation)"]
        end

        MOSQ -->|"TCP Inbound Stream"| PAHO
        PAHO --> CHAN
        CHAN --> VAL
        VAL --> ROUTER
        ROUTER -->|"Status Messages"| DEVSVC
        ROUTER -->|"Sensor Readings"| TELEMSVC
        ROUTER -->|"Fault Labels"| ALERTSVC
    end

    subgraph STORAGE ["7. Persistence Layer (PostgreSQL 16)"]
        direction TB
        FLYWAY["Flyway Migration Engine<br/>(V1__init_schema.sql)"]
        
        TBL_MACHINES[("machines<br/>(Name, Specs, Ratings)")]
        TBL_DEVICES[("devices<br/>(ESP32 Metadata, Status, IP, RSSI)")]
        TBL_TELEM[("telemetry_records<br/>(V, I, T, RPM, Vib Features, JSONB Status)")]
        TBL_COND[("condition_records<br/>(ML Label, Confidence, Anomaly Score)")]
        TBL_ALERTS[("fault_alerts<br/>(ACTIVE, ACKNOWLEDGED, RESOLVED)")]

        IDX["Composite B-Tree Indexes<br/>(device_id, recorded_at DESC)"]

        DEVSVC --> TBL_DEVICES
        TELEMSVC --> TBL_TELEM
        ALERTSVC --> TBL_COND
        ALERTSVC --> TBL_ALERTS
        TBL_MACHINES -.-> TBL_DEVICES
        TBL_TELEM -.-> IDX
    end

    subgraph PRESENTATION ["8. Presentation & Client Layer"]
        API["Spring MVC REST Controllers<br/>(/api/v1/machines, /telemetry/trends, /alerts)"]
        WS["WebSocket / SSE Real-time Broadcaster"]
        DASH["Web Dashboard<br/>(Health Gauges, FFT Spectral Plots, Alert Log)"]

        TBL_TELEM --> API
        TBL_ALERTS --> API
        ALERTSVC --> WS
        API --> DASH
        WS --> DASH
    end
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
