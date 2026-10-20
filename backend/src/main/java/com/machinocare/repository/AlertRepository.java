package com.machinocare.repository;

import com.machinocare.model.AlertDocument;
import com.machinocare.model.AlertSeverity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data MongoDB repository for operational and diagnostic alerts.
 */
@Repository
public interface AlertRepository extends MongoRepository<AlertDocument, String> {

    /**
     * Fetch all active (unresolved) alerts across the entire factory plant.
     */
    List<AlertDocument> findByResolvedFalseOrderByTimestampDesc();

    /**
     * Fetch active (unresolved) alerts for a specific device.
     */
    List<AlertDocument> findByDeviceIdAndResolvedFalseOrderByTimestampDesc(String deviceId);

    /**
     * Fetch active alerts by severity (e.g. all unresolved CRITICAL alerts).
     */
    List<AlertDocument> findBySeverityAndResolvedFalseOrderByTimestampDesc(AlertSeverity severity);

    /**
     * Fetch most recent 50 alerts (both resolved and unresolved) for audit history.
     */
    List<AlertDocument> findTop50ByOrderByTimestampDesc();
}
