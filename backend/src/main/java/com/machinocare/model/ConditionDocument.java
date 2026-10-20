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
 * MongoDB document storing TinyML machine condition inferences from the ESP32 edge device.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conditions")
@CompoundIndexes({
    @CompoundIndex(name = "device_condition_time_idx", def = "{'deviceId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "machine_condition_time_idx", def = "{'machineId': 1, 'timestamp': -1}")
})
public class ConditionDocument {

    @Id
    private String id;

    private String deviceId;

    private String machineId;

    private String contractVersion;

    /**
     * Inference window end timestamp assigned by ESP32.
     */
    private Instant timestamp;

    /**
     * Timestamp recorded upon arrival at Spring Boot backend.
     */
    private Instant receivedAt;

    /**
     * ML classification condition: NORMAL, IMBALANCE, LOOSE_MOUNTING, LOAD_RELATED_ABNORMALITY.
     */
    private MachineCondition condition;

    /**
     * Statistical confidence of prediction (0.00 to 1.00).
     */
    private Double confidence;

    /**
     * Execution duration of inference on ESP32 in milliseconds.
     */
    private Double inferenceTimeMs;

    /**
     * Normalized distance or anomaly metric (0.00 to 1.00).
     */
    private Double anomalyScore;
}
