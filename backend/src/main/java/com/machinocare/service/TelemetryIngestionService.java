package com.machinocare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machinocare.model.AlertDocument;
import com.machinocare.model.AlertSeverity;
import com.machinocare.model.AlertType;
import com.machinocare.model.ElectricalMetrics;
import com.machinocare.model.RotationalMetrics;
import com.machinocare.model.SensorHealthStatus;
import com.machinocare.model.TelemetryDocument;
import com.machinocare.model.ThermalMetrics;
import com.machinocare.model.VibrationMetrics;
import com.machinocare.repository.AlertRepository;
import com.machinocare.repository.DeviceRepository;
import com.machinocare.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service ingesting high-frequency periodic multi-sensor telemetry streams.
 * Handles validation, monotonic timestamping, persistence, and sensor disconnect alerts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryIngestionService {

    private final TelemetryRepository telemetryRepository;
    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    public void processTelemetryMessage(String topic, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String deviceId = root.path("deviceId").asText();
            String machineId = root.path("machineId").asText();
            String contractVersion = root.path("contractVersion").asText("1.0");
            
            Instant timestamp = root.has("timestamp") && !root.path("timestamp").isNull()
                    ? Instant.parse(root.path("timestamp").asText())
                    : Instant.now();
            Instant receivedAt = Instant.now();

            // 1. Electrical Metrics (INA219)
            ElectricalMetrics electrical = null;
            if (root.has("electrical") && !root.path("electrical").isNull()) {
                JsonNode el = root.path("electrical");
                electrical = ElectricalMetrics.builder()
                        .voltage(el.has("voltage") && !el.path("voltage").isNull() ? el.path("voltage").asDouble() : null)
                        .current(el.has("current") && !el.path("current").isNull() ? el.path("current").asDouble() : null)
                        .power(el.has("power") && !el.path("power").isNull() ? el.path("power").asDouble() : null)
                        .build();
            }

            // 2. Thermal Metrics (DS18B20)
            ThermalMetrics thermal = null;
            if (root.has("thermal") && !root.path("thermal").isNull()) {
                JsonNode th = root.path("thermal");
                thermal = ThermalMetrics.builder()
                        .temperature(th.has("temperature") && !th.path("temperature").isNull() ? th.path("temperature").asDouble() : null)
                        .build();
            }

            // 3. Rotational Metrics (LM393)
            RotationalMetrics rotational = null;
            if (root.has("rotational") && !root.path("rotational").isNull()) {
                JsonNode ro = root.path("rotational");
                rotational = RotationalMetrics.builder()
                        .rpm(ro.has("rpm") && !ro.path("rpm").isNull() ? ro.path("rpm").asDouble() : null)
                        .build();
            }

            // 4. Vibration Metrics (ADXL345)
            VibrationMetrics vibration = null;
            if (root.has("vibration") && !root.path("vibration").isNull()) {
                JsonNode vb = root.path("vibration");
                vibration = VibrationMetrics.builder()
                        .rms(vb.has("rms") && !vb.path("rms").isNull() ? vb.path("rms").asDouble() : null)
                        .peak(vb.has("peak") && !vb.path("peak").isNull() ? vb.path("peak").asDouble() : null)
                        .crestFactor(vb.has("crestFactor") && !vb.path("crestFactor").isNull() ? vb.path("crestFactor").asDouble() : null)
                        .dominantFrequency(vb.has("dominantFrequency") && !vb.path("dominantFrequency").isNull() ? vb.path("dominantFrequency").asDouble() : null)
                        .build();
            }

            // 5. Sensor Diagnostic Health Status
            SensorHealthStatus sensorStatus = null;
            if (root.has("sensorStatus") && !root.path("sensorStatus").isNull()) {
                JsonNode ss = root.path("sensorStatus");
                sensorStatus = SensorHealthStatus.builder()
                        .ina219(ss.path("ina219").asText("OK"))
                        .ds18b20(ss.path("ds18b20").asText("OK"))
                        .lm393(ss.path("lm393").asText("OK"))
                        .adxl345(ss.path("adxl345").asText("OK"))
                        .build();

                // Check for sensor hardware failure / disconnect
                checkSensorHealth(deviceId, machineId, sensorStatus, receivedAt);
            }

            TelemetryDocument doc = TelemetryDocument.builder()
                    .deviceId(deviceId)
                    .machineId(machineId)
                    .contractVersion(contractVersion)
                    .timestamp(timestamp)
                    .receivedAt(receivedAt)
                    .electrical(electrical)
                    .thermal(thermal)
                    .rotational(rotational)
                    .vibration(vibration)
                    .sensorStatus(sensorStatus)
                    .build();

            telemetryRepository.save(doc);

            // Update device heartbeat in registry
            deviceRepository.findById(deviceId).ifPresent(d -> {
                d.setLastSeenAt(receivedAt);
                deviceRepository.save(d);
            });

            log.debug("Ingested telemetry | Device: {} | V: {} | A: {} | RPM: {} | Vib RMS: {}",
                    deviceId,
                    electrical != null ? electrical.getVoltage() : null,
                    electrical != null ? electrical.getCurrent() : null,
                    rotational != null ? rotational.getRpm() : null,
                    vibration != null ? vibration.getRms() : null);

        } catch (Exception e) {
            log.error("Failed to parse telemetry payload from topic: {} | Error: {}", topic, e.getMessage(), e);
        }
    }

    private void checkSensorHealth(String deviceId, String machineId, SensorHealthStatus status, Instant now) {
        checkIndividualSensor(deviceId, machineId, "INA219 (Power)", status.getIna219(), now);
        checkIndividualSensor(deviceId, machineId, "DS18B20 (Temp)", status.getDs18b20(), now);
        checkIndividualSensor(deviceId, machineId, "LM393 (RPM)", status.getLm393(), now);
        checkIndividualSensor(deviceId, machineId, "ADXL345 (Vibration)", status.getAdxl345(), now);
    }

    private void checkIndividualSensor(String deviceId, String machineId, String sensorName, String state, Instant now) {
        if ("DISCONNECTED".equalsIgnoreCase(state) || "ERROR".equalsIgnoreCase(state)) {
            AlertDocument alert = AlertDocument.builder()
                    .deviceId(deviceId)
                    .machineId(machineId)
                    .timestamp(now)
                    .severity(AlertSeverity.WARNING)
                    .alertType(AlertType.SENSOR_FAILURE)
                    .message("Sensor hardware fault detected: " + sensorName + " is " + state)
                    .details("Sensor probe failed self-test or communication timeout.")
                    .resolved(false)
                    .build();

            alertRepository.save(alert);
            log.warn("SENSOR ALERT: {} on device {} is {}", sensorName, deviceId, state);
        }
    }
}
