package com.z254.butterfly.aurora.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.avro.generic.GenericRecord;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Canonical representation of an anomaly signal across the service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalySignal {

    private String anomalyId;
    private String rimNodeId;
    private long timestamp;
    private String anomalyType;
    private double severity;
    @Builder.Default
    private Set<String> affectedComponents = new HashSet<>();
    @Builder.Default
    private Map<String, Double> metrics = new HashMap<>();
    private String correlationId;
    private String sourceService;
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    public static AnomalySignal fromRecord(GenericRecord record) {
        if (record == null) {
            return null;
        }

        AnomalySignal signal = new AnomalySignal();
        signal.setAnomalyId(getString(record, "anomalyId"));
        signal.setRimNodeId(getString(record, "rimNodeId"));
        signal.setTimestamp(getLong(record, "timestamp"));
        signal.setAnomalyType(getString(record, "anomalyType"));
        signal.setSeverity(getDouble(record, "severity"));
        signal.setCorrelationId(getString(record, "correlationId"));
        signal.setSourceService(getString(record, "sourceService"));
        signal.setAffectedComponents(getSet(record, "affectedComponents"));
        signal.setMetrics(getDoubleMap(record, "metrics"));
        signal.setMetadata(getStringMap(record, "metadata"));
        return signal;
    }

    private static String getString(GenericRecord record, String field) {
        Object value = record.get(field);
        return value != null ? value.toString() : null;
    }

    private static long getLong(GenericRecord record, String field) {
        Object value = record.get(field);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static double getDouble(GenericRecord record, String field) {
        Object value = record.get(field);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getSet(GenericRecord record, String field) {
        Object raw = record.get(field);
        if (raw instanceof Iterable<?> iterable) {
            Set<String> set = new HashSet<>();
            for (Object item : iterable) {
                if (item != null) {
                    set.add(item.toString());
                }
            }
            return set;
        }
        return new HashSet<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> getDoubleMap(GenericRecord record, String field) {
        Object raw = record.get(field);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Double> result = new HashMap<>();
            map.forEach((k, v) -> {
                if (k != null && v instanceof Number number) {
                    result.put(k.toString(), number.doubleValue());
                }
            });
            return result;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getStringMap(GenericRecord record, String field) {
        Object raw = record.get(field);
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> result = new HashMap<>();
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    result.put(k.toString(), v.toString());
                }
            });
            return result;
        }
        return new HashMap<>();
    }
}
