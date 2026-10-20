package com.machinocare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document storing operational alarms, hardware disconnects, and detected machine faults.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alerts")
@CompoundIndexes({
    @CompoundIndex(name = "device_alert_time_idx", def = "{'deviceId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "resolved_time_idx", def = "{'resolved': 1, 'timestamp': -1}")
})
public class AlertDocument {

    @Id
    private String id;

    private String deviceId;

    private String machineId;

    private Instant timestamp;

    /**
     * Alert severity: INFO, WARNING, CRITICAL.
     */
    private AlertSeverity severity;

    /**
     * Classification category: FAULT_DETECTED, SENSOR_FAILURE, DEVICE_OFFLINE, THRESHOLD_EXCEEDED.
     */
    private AlertType alertType;

    /**
     * Human-readable diagnostic description.
     */
    private String message;

    /**
     * Triggering value (e.g., "IMBALANCE (confidence: 94%)", "INA219 DISCONNECTED").
     */
    private String details;

    /**
     * Whether this alert has been acknowledged or addressed by a plant operator.
     */
    private boolean resolved;

    /**
     * Timestamp of resolution.
     */
    private Instant resolvedAt;
}
