package com.z254.butterfly.aurora.domain.repository;

import com.z254.butterfly.aurora.domain.model.Incident;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Basic in-memory repository used until a durable store (Cassandra) is wired up.
 */
@Repository
public class InMemoryIncidentRepository implements IncidentRepository {

    private final Map<String, Incident> store = new ConcurrentHashMap<>();

    @Override
    public Incident save(Incident incident) {
        store.put(incident.getId(), incident);
        return incident;
    }

    @Override
    public Optional<Incident> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Incident> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
