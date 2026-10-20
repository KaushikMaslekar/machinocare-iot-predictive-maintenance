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
 * MongoDB document storing high-frequency periodic telemetry and extracted sensor features.
 * Features a compound index on (deviceId, timestamp DESC) for sub-millisecond historical chart queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "telemetry")
@CompoundIndexes({
    @CompoundIndex(name = "device_timestamp_idx", def = "{'deviceId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "machine_timestamp_idx", def = "{'machineId': 1, 'timestamp': -1}")
})
public class TelemetryDocument {

    @Id
    private String id;

    private String deviceId;

    private String machineId;

    private String contractVersion;

    /**
     * Timestamp assigned by the ESP32 (ISO-8601 UTC).
     */
    private Instant timestamp;

    /**
     * Strict ingestion timestamp recorded upon arrival at Spring Boot backend.
     */
    private Instant receivedAt;

    /**
     * Bus voltage (V), current (A), and power (W) from INA219.
     */
    private ElectricalMetrics electrical;

    /**
     * Motor casing temperature (°C) from DS18B20.
     */
    private ThermalMetrics thermal;

    /**
     * Rotational speed (RPM) from LM393.
     */
    private RotationalMetrics rotational;

    /**
     * Vibration RMS, Peak, Crest Factor, Dominant Frequency from ADXL345.
     */
    private VibrationMetrics vibration;

    /**
     * Individual hardware sensor connectivity states.
     */
    private SensorHealthStatus sensorStatus;
}
