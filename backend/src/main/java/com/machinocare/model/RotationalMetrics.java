package com.machinocare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rotational speed measured by LM393 optical interrupter with 20-slot encoder disc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RotationalMetrics {
    /** Motor drive shaft rotational speed in Revolutions Per Minute (RPM). */
    private Double rpm;
}
