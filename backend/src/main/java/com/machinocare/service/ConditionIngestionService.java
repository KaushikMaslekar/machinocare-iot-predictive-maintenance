package com.machinocare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machinocare.model.AlertDocument;
import com.machinocare.model.AlertSeverity;
import com.machinocare.model.AlertType;
import com.machinocare.model.ConditionDocument;
import com.machinocare.model.MachineCondition;
import com.machinocare.repository.AlertRepository;
import com.machinocare.repository.ConditionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service ingesting Edge ML inference classifications and triggering operational alarms.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConditionIngestionService {

    private final ConditionRepository conditionRepository;
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    public void processConditionMessage(String topic, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String deviceId = root.path("deviceId").asText();
            String machineId = root.path("machineId").asText();
            String contractVersion = root.path("contractVersion").asText("1.0");
            String conditionStr = root.path("condition").asText("NORMAL");
            Double confidence = root.has("confidence") ? root.path("confidence").asDouble() : 0.0;
            Double inferenceTimeMs = root.has("inferenceTimeMs") && !root.path("inferenceTimeMs").isNull()
                    ? root.path("inferenceTimeMs").asDouble() : null;
            Double anomalyScore = root.has("anomalyScore") && !root.path("anomalyScore").isNull()
                    ? root.path("anomalyScore").asDouble() : null;

            Instant timestamp = root.has("timestamp") && !root.path("timestamp").isNull()
                    ? Instant.parse(root.path("timestamp").asText())
                    : Instant.now();
            Instant receivedAt = Instant.now();

            MachineCondition condition;
            try {
                condition = MachineCondition.valueOf(conditionStr.toUpperCase());
            } catch (IllegalArgumentException ex) {
                log.warn("Unrecognized condition label received: {} on device: {}. Defaulting to NORMAL.", conditionStr, deviceId);
                condition = MachineCondition.NORMAL;
            }

            ConditionDocument doc = ConditionDocument.builder()
                    .deviceId(deviceId)
                    .machineId(machineId)
                    .contractVersion(contractVersion)
                    .timestamp(timestamp)
                    .receivedAt(receivedAt)
                    .condition(condition)
                    .confidence(confidence)
                    .inferenceTimeMs(inferenceTimeMs)
                    .anomalyScore(anomalyScore)
                    .build();

            conditionRepository.save(doc);
            log.info("Ingested Condition Inference | Device: {} | Condition: {} | Confidence: {}%",
                    deviceId, condition, Math.round(confidence * 100));

            // Automated Fault Alert Trigger
            if (condition != MachineCondition.NORMAL) {
                AlertDocument alert = AlertDocument.builder()
                        .deviceId(deviceId)
                        .machineId(machineId)
                        .timestamp(receivedAt)
                        .severity(AlertSeverity.CRITICAL)
                        .alertType(AlertType.FAULT_DETECTED)
                        .message("Diagnostic fault detected on motor: " + condition)
                        .details(String.format("Edge TinyML model detected %s with %.1f%% confidence (Inference time: %.1fms)",
                                condition, confidence * 100, inferenceTimeMs != null ? inferenceTimeMs : 0.0))
                        .resolved(false)
                        .build();

                alertRepository.save(alert);
                log.warn("CRITICAL FAULT ALERT RAISED | Machine: {} | Fault: {}", machineId, condition);
            }

        } catch (Exception e) {
            log.error("Failed to parse condition payload from topic: {} | Error: {}", topic, e.getMessage(), e);
        }
    }
}
