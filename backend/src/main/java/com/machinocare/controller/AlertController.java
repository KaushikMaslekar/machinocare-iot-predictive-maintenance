package com.machinocare.controller;

import com.machinocare.dto.ResolveAlertRequest;
import com.machinocare.model.AlertDocument;
import com.machinocare.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing critical operational alarms and incident resolution lifecycle.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRepository alertRepository;

    /**
     * Get all active (unresolved) alerts, optionally filtered by device ID.
     */
    @GetMapping("/active")
    public ResponseEntity<List<AlertDocument>> getActiveAlerts(@RequestParam(required = false) String deviceId) {
        if (deviceId != null && !deviceId.isBlank()) {
            return ResponseEntity.ok(alertRepository.findByDeviceIdAndResolvedFalseOrderByTimestampDesc(deviceId));
        }
        return ResponseEntity.ok(alertRepository.findByResolvedFalseOrderByTimestampDesc());
    }

    /**
     * Get the most recent 50 alerts (both resolved and unresolved) for audit trails.
     */
    @GetMapping("/history")
    public ResponseEntity<List<AlertDocument>> getAlertHistory() {
        return ResponseEntity.ok(alertRepository.findTop50ByOrderByTimestampDesc());
    }

    /**
     * Mark an alert as resolved by a plant operator / technician.
     */
    @PostMapping("/{alertId}/resolve")
    public ResponseEntity<AlertDocument> resolveAlert(
            @PathVariable String alertId,
            @RequestBody(required = false) ResolveAlertRequest request) {

        Optional<AlertDocument> alertOpt = alertRepository.findById(alertId);
        if (alertOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AlertDocument alert = alertOpt.get();
        alert.setResolved(true);
        alert.setResolvedAt(Instant.now());

        if (request != null && request.getResolutionNotes() != null) {
            String existingDetails = alert.getDetails() != null ? alert.getDetails() : "";
            alert.setDetails(existingDetails + " | Resolved by " +
                    (request.getOperatorName() != null ? request.getOperatorName() : "Operator") +
                    ": " + request.getResolutionNotes());
        }

        AlertDocument saved = alertRepository.save(alert);
        log.info("Alert {} marked as resolved by operator.", alertId);
        return ResponseEntity.ok(saved);
    }
}
