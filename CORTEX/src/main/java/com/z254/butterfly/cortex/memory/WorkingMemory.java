package com.z254.butterfly.cortex.memory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Working memory for per-task context storage.
 * Provides a simple key-value store with lifecycle tied to task execution.
 */
public interface WorkingMemory {

    /**
     * Set a value in working memory.
     *
     * @param key the key
     * @param value the value
     */
    void set(String key, Object value);

    /**
     * Get a value from working memory.
     *
     * @param key the key
     * @param type the expected type
     * @return the value or null
     */
    <T> T get(String key, Class<T> type);

    /**
     * Get a value without type checking.
     *
     * @param key the key
     * @return the value or null
     */
    Object get(String key);

    /**
     * Check if a key exists.
     *
     * @param key the key
     * @return true if exists
     */
    boolean has(String key);

    /**
     * Remove a value.
     *
     * @param key the key
     * @return the removed value or null
     */
    Object remove(String key);

    /**
     * Clear all values.
     */
    void clear();

    /**
     * Get all key-value pairs.
     *
     * @return all entries
     */
    Map<String, Object> getAll();

    /**
     * Get the number of entries.
     *
     * @return entry count
     */
    int size();

    /**
     * Merge values from another map.
     *
     * @param values the values to merge
     */
    default void putAll(Map<String, Object> values) {
        if (values != null) {
            values.forEach(this::set);
        }
    }

    /**
     * Get a value with a default if not present.
     *
     * @param key the key
     * @param defaultValue the default value
     * @param type the expected type
     * @return the value or default
     */
    default <T> T getOrDefault(String key, T defaultValue, Class<T> type) {
        T value = get(key, type);
        return value != null ? value : defaultValue;
    }

    /**
     * Compute value if absent.
     *
     * @param key the key
     * @param compute function to compute the value
     * @return the existing or computed value
     */
    default Object computeIfAbsent(String key, java.util.function.Function<String, Object> compute) {
        Object value = get(key);
        if (value == null) {
            value = compute.apply(key);
            if (value != null) {
                set(key, value);
            }
        }
        return value;
    }

    // Common working memory keys
    
    String KEY_TASK_INPUT = "task.input";
    String KEY_TASK_ID = "task.id";
    String KEY_AGENT_ID = "agent.id";
    String KEY_CONVERSATION_ID = "conversation.id";
    String KEY_ITERATION = "execution.iteration";
    String KEY_PLAN = "execution.plan";
    String KEY_PLAN_STEP = "execution.plan.step";
    String KEY_OBSERVATIONS = "execution.observations";
    String KEY_TOOL_RESULTS = "execution.tool_results";
    String KEY_INTERMEDIATE_RESULTS = "execution.intermediate_results";
    String KEY_ERROR = "execution.error";

    /**
     * Default in-memory implementation.
     */
    class Default implements WorkingMemory {
        private final Map<String, Object> store = new ConcurrentHashMap<>();

        @Override
        public void set(String key, Object value) {
            if (key != null && value != null) {
                store.put(key, value);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            Object value = store.get(key);
            if (value != null && type.isInstance(value)) {
                return (T) value;
            }
            return null;
        }

        @Override
        public Object get(String key) {
            return store.get(key);
        }

        @Override
        public boolean has(String key) {
            return store.containsKey(key);
        }

        @Override
        public Object remove(String key) {
            return store.remove(key);
        }

        @Override
        public void clear() {
            store.clear();
        }

        @Override
        public Map<String, Object> getAll() {
            return new ConcurrentHashMap<>(store);
        }

        @Override
        public int size() {
            return store.size();
        }
    }
}
