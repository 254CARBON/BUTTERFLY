package com.z254.butterfly.testing.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tracking all active test containers.
 * 
 * <p>This registry allows test utilities and custom setup code to discover
 * running containers by name. It's primarily used for:</p>
 * <ul>
 *   <li>Container status reporting in test logs</li>
 *   <li>Custom container configuration in specialized tests</li>
 *   <li>Container cleanup verification</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * // Check if a container is registered and running
 * Optional<GenericContainer<?>> kafka = ContainerRegistry.get("kafka");
 * if (kafka.isPresent() && kafka.get().isRunning()) {
 *     // Use container
 * }
 * 
 * // Get all running containers
 * Map<String, GenericContainer<?>> all = ContainerRegistry.getAll();
 * all.forEach((name, container) -> 
 *     log.info("{} running on port {}", name, container.getFirstMappedPort()));
 * }</pre>
 * 
 * @see ButterflyTestContainers
 * @since 0.1.0
 */
public final class ContainerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ContainerRegistry.class);
    
    private static final Map<String, GenericContainer<?>> containers = new ConcurrentHashMap<>();

    private ContainerRegistry() {
        // Prevent instantiation
    }

    /**
     * Register a container with the given name.
     *
     * @param name      unique name for the container (e.g., "postgres", "kafka")
     * @param container the container instance
     */
    public static void register(String name, GenericContainer<?> container) {
        containers.put(name, container);
        log.debug("Registered container: {} -> {}", name, container.getDockerImageName());
    }

    /**
     * Get a container by name.
     *
     * @param name the container name
     * @return Optional containing the container if registered
     */
    public static Optional<GenericContainer<?>> get(String name) {
        return Optional.ofNullable(containers.get(name));
    }

    /**
     * Get all registered containers.
     *
     * @return unmodifiable map of container name to container instance
     */
    public static Map<String, GenericContainer<?>> getAll() {
        return Map.copyOf(containers);
    }

    /**
     * Check if a container is registered.
     *
     * @param name the container name
     * @return true if the container is registered
     */
    public static boolean isRegistered(String name) {
        return containers.containsKey(name);
    }

    /**
     * Check if a container is registered and running.
     *
     * @param name the container name
     * @return true if the container is registered and running
     */
    public static boolean isRunning(String name) {
        GenericContainer<?> container = containers.get(name);
        return container != null && container.isRunning();
    }

    /**
     * Get a summary of all registered containers and their status.
     *
     * @return formatted string with container status
     */
    public static String getStatusSummary() {
        StringBuilder sb = new StringBuilder("Container Status:\n");
        containers.forEach((name, container) -> {
            sb.append(String.format("  - %s: %s (image: %s)%n",
                    name,
                    container.isRunning() ? "RUNNING" : "STOPPED",
                    container.getDockerImageName()));
        });
        return sb.toString();
    }

    /**
     * Remove a container from the registry.
     * Note: This does not stop the container.
     *
     * @param name the container name to remove
     * @return the removed container, or null if not found
     */
    public static GenericContainer<?> unregister(String name) {
        GenericContainer<?> removed = containers.remove(name);
        if (removed != null) {
            log.debug("Unregistered container: {}", name);
        }
        return removed;
    }

    /**
     * Clear all registered containers.
     * Note: This does not stop the containers.
     */
    public static void clear() {
        containers.clear();
        log.debug("Cleared all registered containers");
    }

    /**
     * Get the count of registered containers.
     *
     * @return number of registered containers
     */
    public static int size() {
        return containers.size();
    }
}

