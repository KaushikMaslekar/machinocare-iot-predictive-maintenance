package com.machinocare.repository;

import com.machinocare.model.DeviceDocument;
import com.machinocare.model.DeviceStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for ESP32 device documents.
 */
@Repository
public interface DeviceRepository extends MongoRepository<DeviceDocument, String> {

    /**
     * Find device by associated physical machine identifier.
     */
    Optional<DeviceDocument> findByMachineId(String machineId);

    /**
     * Find all devices currently in a given status (e.g. ONLINE, OFFLINE).
     */
    List<DeviceDocument> findByStatus(DeviceStatus status);
}
