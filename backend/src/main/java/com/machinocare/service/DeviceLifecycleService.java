package com.machinocare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machinocare.model.AlertDocument;
import com.machinocare.model.AlertSeverity;
import com.machinocare.model.AlertType;
import com.machinocare.model.DeviceDocument;
import com.machinocare.model.DeviceStatus;
import com.machinocare.repository.AlertRepository;
import com.machinocare.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Service managing ESP32 lifecycle, online/offline presence, and Last Will & Testament (LWT) triage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceLifecycleService {

    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    /**
     * Ingests and processes a JSON status message from .../status topic.
     * Handles normal BOOT/SHUTDOWN and automatic LWT UNEXPECTED_DISCONNECT.
     */
    public void processStatusMessage(String topic, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String deviceId = root.path("deviceId").asText();
            String machineId = root.path("machineId").asText();
            String statusStr = root.path("status").asText();
            String reason = root.path("reason").asText();
            String firmwareVersion = root.path("firmwareVersion").asText(null);
            String ipAddress = root.path("ipAddress").asText(null);
            Integer wifiRssi = root.has("wifiRssi") && !root.path("wifiRssi").isNull() ? root.path("wifiRssi").asInt() : null;
            Boolean timeSynced = root.has("timeSynced") ? root.path("timeSynced").asBoolean() : false;
            String contractVersion = root.path("contractVersion").asText("1.0");

            DeviceStatus status = "ONLINE".equalsIgnoreCase(statusStr) ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE;
            Instant now = Instant.now();

            Optional<DeviceDocument> existing = deviceRepository.findById(deviceId);
            DeviceDocument device = existing.orElseGet(() -> DeviceDocument.builder()
                    .id(deviceId)
                    .registeredAt(now)
                    .build());

            device.setMachineId(machineId);
            device.setStatus(status);
            device.setReason(reason);
            if (firmwareVersion != null) device.setFirmwareVersion(firmwareVersion);
            if (ipAddress != null) device.setIpAddress(ipAddress);
            if (wifiRssi != null) device.setWifiRssi(wifiRssi);
            device.setTimeSynced(timeSynced);
            device.setContractVersion(contractVersion);
            device.setLastSeenAt(now);

            deviceRepository.save(device);
            log.info("Device presence updated | ID: {} | Machine: {} | Status: {} | Reason: {}",
                    deviceId, machineId, status, reason);

            // Handle Unexpected Disconnect (Last Will and Testament)
            if (status == DeviceStatus.OFFLINE && "UNEXPECTED_DISCONNECT".equalsIgnoreCase(reason)) {
                AlertDocument alert = AlertDocument.builder()
                        .deviceId(deviceId)
                        .machineId(machineId)
                        .timestamp(now)
                        .severity(AlertSeverity.CRITICAL)
                        .alertType(AlertType.DEVICE_OFFLINE)
                        .message("Device disconnected unexpectedly! Wi-Fi or power loss detected via LWT.")
                        .details("Broker published Last Will and Testament for " + deviceId)
                        .resolved(false)
                        .build();

                alertRepository.save(alert);
                log.warn("CRITICAL ALERT RAISED: LWT disconnect on device: {}", deviceId);
            }

        } catch (Exception e) {
            log.error("Failed to parse status payload from topic: {} | Error: {}", topic, e.getMessage(), e);
        }
    }
}
