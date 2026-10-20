package com.machinocare.repository;

import com.machinocare.model.TelemetryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for high-frequency telemetry records.
 */
@Repository
public interface TelemetryRepository extends MongoRepository<TelemetryDocument, String> {

    /**
     * Fetch the single most recent telemetry packet for a given device.
     */
    Optional<TelemetryDocument> findFirstByDeviceIdOrderByTimestampDesc(String deviceId);

    /**
     * Fetch the single most recent telemetry packet for a given machine.
     */
    Optional<TelemetryDocument> findFirstByMachineIdOrderByTimestampDesc(String machineId);

    /**
     * Fetch the most recent 50 telemetry points for real-time frontend charts.
     */
    List<TelemetryDocument> findTop50ByDeviceIdOrderByTimestampDesc(String deviceId);

    /**
     * Query telemetry over a historical time window.
     */
    List<TelemetryDocument> findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(
            String deviceId, Instant from, Instant to);
}
