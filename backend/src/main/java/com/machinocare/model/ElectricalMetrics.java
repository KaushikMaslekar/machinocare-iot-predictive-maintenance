package com.machinocare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Electrical bus metrics measured by INA219 sensor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElectricalMetrics {
    /** Bus supply voltage in Volts (V). */
    private Double voltage;

    /** Armature current draw in Amperes (A). */
    private Double current;

    /** Real electrical power in Watts (W). */
    private Double power;
}
