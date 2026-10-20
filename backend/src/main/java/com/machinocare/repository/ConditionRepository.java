package com.machinocare.repository;

import com.machinocare.model.ConditionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for Edge ML classification inferences.
 */
@Repository
public interface ConditionRepository extends MongoRepository<ConditionDocument, String> {

    /**
     * Fetch latest machine health condition for a specific device.
     */
    Optional<ConditionDocument> findFirstByDeviceIdOrderByTimestampDesc(String deviceId);

    /**
     * Fetch latest machine health condition for a specific motor unit.
     */
    Optional<ConditionDocument> findFirstByMachineIdOrderByTimestampDesc(String machineId);

    /**
     * Retrieve recent condition timeline for stability and transition tracking.
     */
    List<ConditionDocument> findTop20ByDeviceIdOrderByTimestampDesc(String deviceId);

    /**
     * Query conditions over an investigation time window.
     */
    List<ConditionDocument> findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(
            String deviceId, Instant from, Instant to);
}
