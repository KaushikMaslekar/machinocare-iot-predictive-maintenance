package com.machinocare.controller;

import com.machinocare.model.TelemetryDocument;
import com.machinocare.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * REST controller for high-frequency multi-sensor telemetry feeds and historical trend curves.
 */
@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryRepository telemetryRepository;

    /**
     * Fetch the single most recent telemetry packet for live vital gauges.
     */
    @GetMapping("/latest")
    public ResponseEntity<TelemetryDocument> getLatestTelemetry(@RequestParam String deviceId) {
        return telemetryRepository.findFirstByDeviceIdOrderByTimestampDesc(deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Fetch recent 50 telemetry points for real-time dashboard line charts.
     */
    @GetMapping("/history")
    public ResponseEntity<List<TelemetryDocument>> getTelemetryHistory(@RequestParam String deviceId) {
        List<TelemetryDocument> records = telemetryRepository.findTop50ByDeviceIdOrderByTimestampDesc(deviceId);
        return ResponseEntity.ok(records);
    }

    /**
     * Query sensor telemetry across an explicit historical time window.
     */
    @GetMapping("/range")
    public ResponseEntity<List<TelemetryDocument>> getTelemetryByRange(
            @RequestParam String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<TelemetryDocument> records =
                telemetryRepository.findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(deviceId, from, to);
        return ResponseEntity.ok(records);
    }
}
