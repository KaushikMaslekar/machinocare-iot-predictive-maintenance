package com.machinocare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Diagnostic health and bus connection status of physical sensors.
 * Values: "OK", "DISCONNECTED", "ERROR".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorHealthStatus {
    private String ina219;
    private String ds18b20;
    private String lm393;
    private String adxl345;
}
