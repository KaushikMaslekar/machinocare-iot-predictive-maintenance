package com.machinocare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thermal measurements acquired via DS18B20 digital temperature probe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThermalMetrics {
    /** Motor casing surface temperature in degrees Celsius (°C). */
    private Double temperature;
}
