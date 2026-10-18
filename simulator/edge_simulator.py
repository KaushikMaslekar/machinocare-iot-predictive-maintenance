#!/usr/bin/env python3
"""
MachinoCare - Edge Device & Motor Mock Simulator
=================================================
Simulates an ESP32 edge node monitoring a 12V DC motor (RS-555 / RS-550).
Strictly adheres to the Edge-to-IoT Data Contract Specification (v1.0.0).

Features:
- Connects over MQTT with Last Will and Testament (LWT) on .../status
- Publishes periodic telemetry (electrical, thermal, rotational, vibration features)
- Publishes condition inference results (NORMAL, IMBALANCE, LOOSE_MOUNTING, LOAD_RELATED_ABNORMALITY)
- Simulates realistic physical DC motor dynamics (Load -> RPM drops -> Current surges -> Temp rises)
- Supports fault modes: 'normal', 'load', 'imbalance', 'loose', 'sensor_error', 'cycle'
"""

import argparse
import json
import math
import random
import signal
import sys
import time
from datetime import datetime, timezone
import paho.mqtt.client as mqtt

# Default Configuration
DEFAULT_BROKER_HOST = "localhost"
DEFAULT_BROKER_PORT = 1883
DEFAULT_DEVICE_ID = "ESP32-001"
DEFAULT_MACHINE_ID = "MOTOR-001"
DEFAULT_INTERVAL_SEC = 1.0
CONTRACT_VERSION = "1.0"


def current_iso_timestamp() -> str:
    """Returns current UTC time in ISO-8601 format with millisecond precision."""
    now = datetime.now(timezone.utc)
    return now.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


class MotorPhysicsState:
    """Maintains continuous physical state of the 12V DC motor."""

    def __init__(self):
        self.temperature = 38.0  # Celsius
        self.operating_seconds = 0.0

    def step(self, mode: str, dt: float):
        self.operating_seconds += dt
        noise = random.gauss(0, 0.02)

        if mode == "normal":
            # Nominal operating point for 12V RS-550 unloaded / light load
            voltage = round(12.0 + random.uniform(-0.1, 0.1), 2)
            current = round(1.35 + random.uniform(-0.05, 0.05) + noise, 2)
            rpm = round(2850.0 + random.uniform(-20, 20), 1)
            power = round(voltage * current, 2)

            # Gradual thermal equilibrium towards ~42 C
            self.temperature += (42.0 - self.temperature) * 0.03 * dt
            temp = round(self.temperature + random.uniform(-0.1, 0.1), 1)

            # Low, healthy vibration profile
            v_rms = round(0.32 + random.uniform(-0.02, 0.02), 3)
            v_peak = round(v_rms * random.uniform(2.2, 2.5), 3)
            crest_factor = round(v_peak / v_rms, 2)
            dom_freq = round(47.5 + random.uniform(-1.0, 1.0), 1)

            condition = "NORMAL"
            confidence = round(random.uniform(0.93, 0.98), 2)
            sensor_status = {"ina219": "OK", "ds18b20": "OK", "lm393": "OK", "adxl345": "OK"}

        elif mode == "load":
            # Mechanical resistance on shaft: Resistance UP -> RPM DOWN -> Back-EMF DOWN -> Current UP
            voltage = round(11.85 + random.uniform(-0.1, 0.05), 2)
            current = round(3.40 + random.uniform(-0.15, 0.15) + noise, 2)
            rpm = round(1920.0 + random.uniform(-35, 35), 1)
            power = round(voltage * current, 2)

            # Heavy heating due to I^2 * R copper losses towards ~68 C
            self.temperature += (68.0 - self.temperature) * 0.06 * dt
            temp = round(self.temperature + random.uniform(-0.2, 0.2), 1)

            v_rms = round(0.55 + random.uniform(-0.04, 0.04), 3)
            v_peak = round(v_rms * random.uniform(2.3, 2.6), 3)
            crest_factor = round(v_peak / v_rms, 2)
            dom_freq = round(32.0 + random.uniform(-1.5, 1.5), 1)

            condition = "LOAD_RELATED_ABNORMALITY"
            confidence = round(random.uniform(0.90, 0.96), 2)
            sensor_status = {"ina219": "OK", "ds18b20": "OK", "lm393": "OK", "adxl345": "OK"}

        elif mode == "imbalance":
            # Rotor mass imbalance: RPM stable, vibration amplitude & dominant harmonic spike
            voltage = round(12.0 + random.uniform(-0.08, 0.08), 2)
            current = round(1.58 + random.uniform(-0.06, 0.06), 2)
            rpm = round(2820.0 + random.uniform(-25, 25), 1)
            power = round(voltage * current, 2)

            self.temperature += (46.0 - self.temperature) * 0.03 * dt
            temp = round(self.temperature + random.uniform(-0.1, 0.1), 1)

            # High vibration RMS and peak
            v_rms = round(1.18 + random.uniform(-0.08, 0.08), 3)
            v_peak = round(v_rms * random.uniform(2.2, 2.4), 3)
            crest_factor = round(v_peak / v_rms, 2)
            # 1X RPM frequency: 2820 RPM / 60 = ~47 Hz
            dom_freq = round(47.0 + random.uniform(-0.5, 0.5), 1)

            condition = "IMBALANCE"
            confidence = round(random.uniform(0.92, 0.97), 2)
            sensor_status = {"ina219": "OK", "ds18b20": "OK", "lm393": "OK", "adxl345": "OK"}

        elif mode == "loose":
            # Loose mounting: High crest factor (impacting/rattling), sub-harmonics
            voltage = round(12.0 + random.uniform(-0.08, 0.08), 2)
            current = round(1.48 + random.uniform(-0.06, 0.06), 2)
            rpm = round(2800.0 + random.uniform(-40, 40), 1)
            power = round(voltage * current, 2)

            self.temperature += (44.0 - self.temperature) * 0.03 * dt
            temp = round(self.temperature + random.uniform(-0.1, 0.1), 1)

            v_rms = round(0.72 + random.uniform(-0.06, 0.06), 3)
            # Spiky impacts cause high peak and high crest factor
            v_peak = round(2.75 + random.uniform(-0.15, 0.15), 3)
            crest_factor = round(v_peak / v_rms, 2)
            dom_freq = round(23.5 + random.uniform(-1.0, 1.0), 1)

            condition = "LOOSE_MOUNTING"
            confidence = round(random.uniform(0.89, 0.95), 2)
            sensor_status = {"ina219": "OK", "ds18b20": "OK", "lm393": "OK", "adxl345": "OK"}

        elif mode == "sensor_error":
            # Simulated DS18B20 wire disconnection
            voltage = round(12.05, 2)
            current = round(1.40, 2)
            power = round(voltage * current, 2)
            rpm = 2840.0
            temp = None  # Null per contract
            v_rms = 0.33
            v_peak = 0.79
            crest_factor = round(v_peak / v_rms, 2)
            dom_freq = 47.3
            condition = "NORMAL"
            confidence = 0.90
            sensor_status = {"ina219": "OK", "ds18b20": "DISCONNECTED", "lm393": "OK", "adxl345": "OK"}

        else:
            raise ValueError(f"Unknown mode: {mode}")

        telemetry = {
            "contractVersion": CONTRACT_VERSION,
            "deviceId": DEFAULT_DEVICE_ID,
            "machineId": DEFAULT_MACHINE_ID,
            "timestamp": current_iso_timestamp(),
            "electrical": {
                "voltage": voltage,
                "current": current,
                "power": power
            },
            "thermal": {
                "temperature": temp
            },
            "rotational": {
                "rpm": rpm
            },
            "vibration": {
                "rms": v_rms,
                "peak": v_peak,
                "crestFactor": crest_factor,
                "dominantFrequency": dom_freq
            },
            "sensorStatus": sensor_status
        }

        condition_payload = {
            "contractVersion": CONTRACT_VERSION,
            "deviceId": DEFAULT_DEVICE_ID,
            "machineId": DEFAULT_MACHINE_ID,
            "timestamp": current_iso_timestamp(),
            "condition": condition,
            "confidence": confidence,
            "inferenceTimeMs": round(random.uniform(11.0, 15.5), 1),
            "anomalyScore": 0.05 if condition == "NORMAL" else round(random.uniform(0.65, 0.92), 2)
        }

        return telemetry, condition_payload


class EdgeSimulator:
    def __init__(self, host: str, port: int, device_id: str, machine_id: str, interval: float, mode: str):
        self.host = host
        self.port = port
        self.device_id = device_id
        self.machine_id = machine_id
        self.interval = interval
        self.mode = mode
        self.running = False
        self.physics = MotorPhysicsState()

        # Topic paths per contract v1.0.0
        self.status_topic = f"machinocare/v1/devices/{self.device_id}/status"
        self.telemetry_topic = f"machinocare/v1/devices/{self.device_id}/telemetry"
        self.condition_topic = f"machinocare/v1/devices/{self.device_id}/condition"

        # Initialize MQTT client (compatible with paho-mqtt 2.x and 1.x)
        try:
            self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"sim-{self.device_id}")
        except AttributeError:
            self.client = mqtt.Client(client_id=f"sim-{self.device_id}")

        self.setup_lwt()

    def setup_lwt(self):
        """Configures Last Will and Testament (LWT) published if simulator crashes unexpectedly."""
        lwt_payload = json.dumps({
            "contractVersion": CONTRACT_VERSION,
            "deviceId": self.device_id,
            "machineId": self.machine_id,
            "status": "OFFLINE",
            "reason": "UNEXPECTED_DISCONNECT",
            "timestamp": current_iso_timestamp()
        })
        self.client.will_set(self.status_topic, payload=lwt_payload, qos=1, retain=True)

    def publish_status(self, status: str, reason: str):
        """Publishes explicit lifecycle status (Retained = True, QoS = 1)."""
        payload = json.dumps({
            "contractVersion": CONTRACT_VERSION,
            "deviceId": self.device_id,
            "machineId": self.machine_id,
            "status": status,
            "reason": reason,
            "firmwareVersion": "1.0.0",
            "ipAddress": "127.0.0.1",
            "wifiRssi": -58,
            "timeSynced": True,
            "timestamp": current_iso_timestamp()
        })
        print(f"[STATUS] -> {self.status_topic}: {status} ({reason})", flush=True)
        self.client.publish(self.status_topic, payload=payload, qos=1, retain=True)

    def start(self):
        print(f"Connecting to MQTT Broker at {self.host}:{self.port}...", flush=True)
        self.client.connect(self.host, self.port, keepalive=60)
        self.client.loop_start()

        # Publish initial ONLINE status
        self.publish_status("ONLINE", "BOOT")
        self.running = True

        print("=" * 70, flush=True)
        print(f" MachinoCare Edge Simulator Running | Device: {self.device_id}", flush=True)
        print(f" Mode: {self.mode.upper()} | Publish Interval: {self.interval}s", flush=True)
        print(" Press Ctrl+C to stop cleanly.", flush=True)
        print("=" * 70, flush=True)

        modes_cycle = ["normal", "normal", "imbalance", "normal", "load", "loose", "sensor_error"]
        cycle_idx = 0
        step_counter = 0

        try:
            while self.running:
                active_mode = self.mode
                if self.mode == "cycle":
                    active_mode = modes_cycle[cycle_idx]
                    if step_counter % 8 == 0 and step_counter > 0:
                        cycle_idx = (cycle_idx + 1) % len(modes_cycle)
                        print(f"\n>>> [CYCLE AUTO-SWITCH] Changing mode to: {modes_cycle[cycle_idx].upper()} <<<\n", flush=True)

                telemetry, condition = self.physics.step(active_mode, self.interval)
                telemetry["deviceId"] = self.device_id
                telemetry["machineId"] = self.machine_id
                condition["deviceId"] = self.device_id
                condition["machineId"] = self.machine_id

                # Publish Telemetry (QoS 0)
                t_json = json.dumps(telemetry)
                self.client.publish(self.telemetry_topic, t_json, qos=0, retain=False)

                # Publish Condition (QoS 1) every 2 intervals or on abnormal conditions
                if step_counter % 2 == 0 or condition["condition"] != "NORMAL":
                    c_json = json.dumps(condition)
                    self.client.publish(self.condition_topic, c_json, qos=1, retain=False)

                # Console log summary line
                elec = telemetry["electrical"]
                vib = telemetry["vibration"]
                print(f"[{datetime.now().strftime('%H:%M:%S')}] [{active_mode.upper():<12}] "
                      f"V={elec['voltage']:4.1f}V | I={elec['current']:4.2f}A | "
                      f"RPM={telemetry['rotational']['rpm']:5.0f} | "
                      f"T={str(telemetry['thermal']['temperature']) + 'C' if telemetry['thermal']['temperature'] else 'NULL':>5} | "
                      f"VibRMS={vib['rms']:4.2f}g | "
                      f"Condition={condition['condition']} ({condition['confidence']*100:.0f}%)", flush=True)

                step_counter += 1
                time.sleep(self.interval)

        except KeyboardInterrupt:
            self.stop()

    def stop(self):
        print("\nStopping simulator gracefully...")
        self.running = False
        self.publish_status("OFFLINE", "SHUTDOWN")
        time.sleep(0.5)
        self.client.loop_stop()
        self.client.disconnect()
        print("Simulator stopped.")


def main():
    parser = argparse.ArgumentParser(description="MachinoCare Edge Mock Simulator")
    parser.add_argument("--host", default=DEFAULT_BROKER_HOST, help="MQTT Broker host")
    parser.add_argument("--port", type=int, default=DEFAULT_BROKER_PORT, help="MQTT Broker port")
    parser.add_argument("--device-id", default=DEFAULT_DEVICE_ID, help="Hardware device identifier")
    parser.add_argument("--machine-id", default=DEFAULT_MACHINE_ID, help="Motor identifier")
    parser.add_argument("--interval", type=float, default=DEFAULT_INTERVAL_SEC, help="Publish interval in seconds")
    parser.add_argument("--mode", default="cycle",
                        choices=["normal", "load", "imbalance", "loose", "sensor_error", "cycle"],
                        help="Motor operating simulation mode")

    args = parser.parse_args()

    simulator = EdgeSimulator(
        host=args.host,
        port=args.port,
        device_id=args.device_id,
        machine_id=args.machine_id,
        interval=args.interval,
        mode=args.mode
    )

    def sig_handler(sig, frame):
        simulator.stop()
        sys.exit(0)

    signal.signal(signal.SIGINT, sig_handler)
    signal.signal(signal.SIGTERM, sig_handler)

    simulator.start()


if __name__ == "__main__":
    main()
