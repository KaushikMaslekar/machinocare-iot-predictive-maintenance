package com.machinocare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload when a technician acknowledges and resolves an active alert.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveAlertRequest {
    private String resolutionNotes;
    private String operatorName;
}
