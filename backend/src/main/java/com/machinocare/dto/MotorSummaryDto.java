package com.machinocare.dto;

import com.machinocare.model.ConditionDocument;
import com.machinocare.model.DeviceDocument;
import com.machinocare.model.TelemetryDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated summary DTO for instantaneous motor health overview on the dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotorSummaryDto {
    private DeviceDocument device;
    private TelemetryDocument latestTelemetry;
    private ConditionDocument latestCondition;
    private long activeAlertsCount;
    private String healthStatus; // HEALTHY, WARNING, CRITICAL, OFFLINE
}
