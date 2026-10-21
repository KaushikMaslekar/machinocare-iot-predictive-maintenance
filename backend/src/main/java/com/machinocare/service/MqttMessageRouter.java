package com.machinocare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

/**
 * Inbound MQTT message dispatcher.
 * Subscribed to "mqttInputChannel", parses the incoming topic, and routes the payload
 * to the appropriate domain service (status, telemetry, or condition).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttMessageRouter {

    private final DeviceLifecycleService deviceLifecycleService;
    private final TelemetryIngestionService telemetryIngestionService;
    private final ConditionIngestionService conditionIngestionService;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void routeInboundMessage(Message<String> message) {
        String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
        String payload = message.getPayload();

        if (topic == null) {
            log.warn("Received MQTT message with null topic. Ignoring payload.");
            return;
        }

        log.debug("MQTT Inbound Packet Received on topic: {}", topic);

        if (topic.endsWith("/status")) {
            deviceLifecycleService.processStatusMessage(topic, payload);
        } else if (topic.endsWith("/telemetry")) {
            telemetryIngestionService.processTelemetryMessage(topic, payload);
        } else if (topic.endsWith("/condition")) {
            conditionIngestionService.processConditionMessage(topic, payload);
        } else {
            log.warn("Received message on unmapped topic: {}", topic);
        }
    }
}
