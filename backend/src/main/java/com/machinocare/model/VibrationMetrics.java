package com.machinocare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vibration statistical and spectral features extracted by ESP32 from ADXL345 accelerometer data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VibrationMetrics {
    /** Root Mean Square acceleration in g (indicates total vibration energy). */
    private Double rms;

    /** Peak acceleration in window in g. */
    private Double peak;

    /** Crest factor (Peak / RMS) indicating sharpness of impact shocks. */
    private Double crestFactor;

    /** Dominant frequency peak from FFT analysis in Hertz (Hz). */
    private Double dominantFrequency;
}
