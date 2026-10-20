package com.machinocare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document representing an Edge IoT Microcontroller (ESP32).
 * Tracks device presence, firmware version, network diagnostics, and machine binding.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "devices")
public class DeviceDocument {

    /**
     * Unique hardware identifier (e.g., "ESP32-001").
     * Serves as the primary document key in MongoDB.
     */
    @Id
    private String id;

    /**
     * Identifier of the monitored physical motor (e.g., "MOTOR-001").
     */
    @Indexed
    private String machineId;

    /**
     * Online or Offline presence state.
     */
    private DeviceStatus status;

    /**
     * Context reason for current status (e.g., "BOOT", "SHUTDOWN", "UNEXPECTED_DISCONNECT").
     */
    private String reason;

    /**
     * Semantic version string of running ESP32 firmware.
     */
    private String firmwareVersion;

    /**
     * Local IP address assigned by Wi-Fi DHCP.
     */
    private String ipAddress;

    /**
     * Wi-Fi received signal strength indicator in dBm.
     */
    private Integer wifiRssi;

    /**
     * Whether the edge RTC was synchronized with NTP.
     */
    private Boolean timeSynced;

    /**
     * Contract schema version (e.g., "1.0").
     */
    private String contractVersion;

    /**
     * Timestamp of most recent transmission received from device.
     */
    private Instant lastSeenAt;

    /**
     * Timestamp when device was first registered in system.
     */
    private Instant registeredAt;
}
