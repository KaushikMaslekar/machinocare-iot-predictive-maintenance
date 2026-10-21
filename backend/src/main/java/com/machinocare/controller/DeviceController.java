package com.machinocare.controller;

import com.machinocare.dto.MotorSummaryDto;
import com.machinocare.model.AlertDocument;
import com.machinocare.model.ConditionDocument;
import com.machinocare.model.DeviceDocument;
import com.machinocare.model.DeviceStatus;
import com.machinocare.model.MachineCondition;
import com.machinocare.model.TelemetryDocument;
import com.machinocare.repository.AlertRepository;
import com.machinocare.repository.ConditionRepository;
import com.machinocare.repository.DeviceRepository;
import com.machinocare.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * REST controller for device presence, hardware inventory, and overall machine summary.
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;
    private final ConditionRepository conditionRepository;
    private final AlertRepository alertRepository;

    /**
     * List all registered ESP32 edge units and their current status.
     */
    @GetMapping
    public ResponseEntity<List<DeviceDocument>> getAllDevices() {
        return ResponseEntity.ok(deviceRepository.findAll());
    }

    /**
     * Fetch a specific device by its hardware ID.
     */
    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceDocument> getDeviceById(@PathVariable String deviceId) {
        return deviceRepository.findById(deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Aggregated motor health summary card (Device info + latest telemetry + latest diagnosis + alerts).
     */
    @GetMapping("/{deviceId}/summary")
    public ResponseEntity<MotorSummaryDto> getMotorSummary(@PathVariable String deviceId) {
        Optional<DeviceDocument> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        DeviceDocument device = deviceOpt.get();
        Optional<TelemetryDocument> latestTelemetry = telemetryRepository.findFirstByDeviceIdOrderByTimestampDesc(deviceId);
        Optional<ConditionDocument> latestCondition = conditionRepository.findFirstByDeviceIdOrderByTimestampDesc(deviceId);
        List<AlertDocument> activeAlerts = alertRepository.findByDeviceIdAndResolvedFalseOrderByTimestampDesc(deviceId);

        // Determine aggregated health state
        String healthStatus = "HEALTHY";
        if (device.getStatus() == DeviceStatus.OFFLINE) {
            healthStatus = "OFFLINE";
        } else if (!activeAlerts.isEmpty()) {
            healthStatus = "CRITICAL";
        } else if (latestCondition.isPresent() && latestCondition.get().getCondition() != MachineCondition.NORMAL) {
            healthStatus = "WARNING";
        }

        MotorSummaryDto summary = MotorSummaryDto.builder()
                .device(device)
                .latestTelemetry(latestTelemetry.orElse(null))
                .latestCondition(latestCondition.orElse(null))
                .activeAlertsCount(activeAlerts.size())
                .healthStatus(healthStatus)
                .build();

        return ResponseEntity.ok(summary);
    }
}
