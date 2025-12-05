package com.z254.butterfly.aurora.domain.repository;

import com.z254.butterfly.aurora.domain.model.Incident;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for incident persistence.
 */
public interface IncidentRepository {

    /**
     * Persist the given incident. Existing incidents are replaced.
     */
    Incident save(Incident incident);

    /**
     * Look up an incident by ID.
     */
    Optional<Incident> findById(String id);

    /**
     * Retrieve all incidents.
     */
    List<Incident> findAll();

    /**
     * Remove an incident.
     */
    void delete(String id);
}
