#!/usr/bin/env python3
"""
MachinoCare - Live MQTT Telemetry & Event Listener
===================================================
Subscribes to all MachinoCare topics ('machinocare/#') on the local Mosquitto broker
and pretty-prints incoming JSON payloads in real time.
"""

import json
import sys
from datetime import datetime
import paho.mqtt.client as mqtt

BROKER_HOST = "localhost"
BROKER_PORT = 1883
TOPIC_FILTER = "machinocare/#"


def on_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        print("=" * 70, flush=True)
        print(f" Connected to Mosquitto Broker at {BROKER_HOST}:{BROKER_PORT}", flush=True)
        print(f" Subscribed to Topic Filter: '{TOPIC_FILTER}'", flush=True)
        print(" Waiting for incoming telemetry and condition events...", flush=True)
        print(" Press Ctrl+C to stop.", flush=True)
        print("=" * 70, flush=True)
        client.subscribe(TOPIC_FILTER, qos=1)
    else:
        print(f"Failed to connect, return code {rc}", flush=True)


def on_message(client, userdata, msg):
    timestamp_now = datetime.now().strftime("%H:%M:%S")
    topic = msg.topic
    raw_payload = msg.payload.decode("utf-8", errors="replace")

    print(f"\n[{timestamp_now}] [TOPIC] {topic} (QoS {msg.qos}, Retained={msg.retain})", flush=True)

    try:
        data = json.loads(raw_payload)
        # Pretty-print formatted JSON
        formatted_json = json.dumps(data, indent=2)
        print(formatted_json, flush=True)
    except json.JSONDecodeError:
        print(f"Raw: {raw_payload}", flush=True)


def main():
    try:
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="machinocare-live-listener")
    except AttributeError:
        client = mqtt.Client(client_id="machinocare-live-listener")

    client.on_connect = on_connect
    client.on_message = on_message

    try:
        client.connect(BROKER_HOST, BROKER_PORT, keepalive=60)
        client.loop_forever()
    except KeyboardInterrupt:
        print("\nListener stopped.")
        client.disconnect()


if __name__ == "__main__":
    main()
