package com.machinocare.controller;

import com.machinocare.model.ConditionDocument;
import com.machinocare.repository.ConditionRepository;
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
 * REST controller for Edge ML condition inferences, confidence scores, and diagnostic timelines.
 */
@RestController
@RequestMapping("/api/v1/conditions")
@RequiredArgsConstructor
public class ConditionController {

    private final ConditionRepository conditionRepository;

    /**
     * Get the latest machine health condition classification.
     */
    @GetMapping("/latest")
    public ResponseEntity<ConditionDocument> getLatestCondition(@RequestParam String deviceId) {
        return conditionRepository.findFirstByDeviceIdOrderByTimestampDesc(deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get recent 20 classification inferences for stability and transition tracking.
     */
    @GetMapping("/history")
    public ResponseEntity<List<ConditionDocument>> getConditionHistory(@RequestParam String deviceId) {
        List<ConditionDocument> history = conditionRepository.findTop20ByDeviceIdOrderByTimestampDesc(deviceId);
        return ResponseEntity.ok(history);
    }

    /**
     * Query conditions across an explicit historical time window.
     */
    @GetMapping("/range")
    public ResponseEntity<List<ConditionDocument>> getConditionsByRange(
            @RequestParam String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<ConditionDocument> history =
                conditionRepository.findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(deviceId, from, to);
        return ResponseEntity.ok(history);
    }
}
