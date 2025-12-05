package com.z254.butterfly.aurora.rca;

import com.z254.butterfly.aurora.stream.DependencyContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Causal graph builder for root cause analysis.
 * <p>
 * Constructs a directed graph representing:
 * <ul>
 *     <li>Component dependencies</li>
 *     <li>Fault propagation paths</li>
 *     <li>Anomaly relationships</li>
 * </ul>
 */
@Slf4j
@Component
public class CausalGraph {

    /**
     * Build a causal graph from anomalies and dependency context.
     *
     * @param anomalies the list of anomaly events
     * @param dependencyContext topology and dependency information
     * @return the constructed component graph
     */
    public ComponentGraph buildFromAnomalies(List<Object> anomalies, 
                                              DependencyContext dependencyContext) {
        ComponentGraph graph = new ComponentGraph();

        // Step 1: Add nodes for all affected components
        Set<String> affectedComponents = extractAffectedComponents(anomalies);
        for (String component : affectedComponents) {
            ComponentNode node = new ComponentNode(component);
            node.setAnomalyCount(countAnomaliesForComponent(anomalies, component));
            node.setMaxSeverity(getMaxSeverityForComponent(anomalies, component));
            graph.addNode(node);
        }

        // Step 2: Add dependency edges
        if (dependencyContext != null) {
            addDependencyEdges(graph, dependencyContext, affectedComponents);
        }

        // Step 3: Add temporal edges based on anomaly timing
        addTemporalEdges(graph, anomalies);

        // Step 4: Calculate node depths
        calculateDepths(graph);

        log.debug("Built causal graph: nodes={}, edges={}", 
                graph.getNodes().size(), graph.getEdges().size());

        return graph;
    }

    /**
     * Extract all affected components from anomalies.
     */
    private Set<String> extractAffectedComponents(List<Object> anomalies) {
        Set<String> components = new HashSet<>();
        
        for (Object anomaly : anomalies) {
            if (anomaly instanceof org.apache.avro.generic.GenericRecord record) {
                // Get primary component
                Object rimNodeId = record.get("rimNodeId");
                if (rimNodeId != null) {
                    components.add(rimNodeId.toString());
                }
                
                // Get affected components array
                Object affected = record.get("affectedComponents");
                if (affected instanceof List<?> list) {
                    for (Object item : list) {
                        if (item != null) {
                            components.add(item.toString());
                        }
                    }
                }
            }
        }
        
        return components;
    }

    /**
     * Count anomalies for a specific component.
     */
    private int countAnomaliesForComponent(List<Object> anomalies, String component) {
        int count = 0;
        for (Object anomaly : anomalies) {
            if (anomaly instanceof org.apache.avro.generic.GenericRecord record) {
                Object rimNodeId = record.get("rimNodeId");
                if (component.equals(String.valueOf(rimNodeId))) {
                    count++;
                }
                Object affected = record.get("affectedComponents");
                if (affected instanceof List<?> list && list.contains(component)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Get maximum severity for a component.
     */
    private double getMaxSeverityForComponent(List<Object> anomalies, String component) {
        double maxSeverity = 0.0;
        for (Object anomaly : anomalies) {
            if (anomaly instanceof org.apache.avro.generic.GenericRecord record) {
                Object rimNodeId = record.get("rimNodeId");
                if (component.equals(String.valueOf(rimNodeId))) {
                    Object severity = record.get("severity");
                    if (severity instanceof Number) {
                        maxSeverity = Math.max(maxSeverity, ((Number) severity).doubleValue());
                    }
                }
            }
        }
        return maxSeverity;
    }

    /**
     * Add edges based on dependency context.
     */
    private void addDependencyEdges(ComponentGraph graph, 
                                     DependencyContext context,
                                     Set<String> affectedComponents) {
        if (context.getUpstreamComponents() != null) {
            for (String upstream : context.getUpstreamComponents()) {
                for (String component : affectedComponents) {
                    if (!upstream.equals(component)) {
                        graph.addEdge(new DependencyEdge(upstream, component, EdgeType.DEPENDENCY, 1.0));
                    }
                }
            }
        }
        
        if (context.getDownstreamComponents() != null) {
            for (String downstream : context.getDownstreamComponents()) {
                for (String component : affectedComponents) {
                    if (!downstream.equals(component)) {
                        graph.addEdge(new DependencyEdge(component, downstream, EdgeType.DEPENDENCY, 1.0));
                    }
                }
            }
        }
    }

    /**
     * Add edges based on temporal ordering of anomalies.
     */
    private void addTemporalEdges(ComponentGraph graph, List<Object> anomalies) {
        // Sort anomalies by timestamp
        List<AnomalyTiming> timings = new ArrayList<>();
        for (Object anomaly : anomalies) {
            if (anomaly instanceof org.apache.avro.generic.GenericRecord record) {
                Object timestamp = record.get("timestamp");
                Object rimNodeId = record.get("rimNodeId");
                if (timestamp instanceof Long && rimNodeId != null) {
                    timings.add(new AnomalyTiming(rimNodeId.toString(), (Long) timestamp));
                }
            }
        }
        
        timings.sort(Comparator.comparingLong(AnomalyTiming::timestamp));
        
        // Create edges from earlier to later anomalies
        for (int i = 0; i < timings.size() - 1; i++) {
            for (int j = i + 1; j < timings.size(); j++) {
                String from = timings.get(i).component();
                String to = timings.get(j).component();
                
                if (!from.equals(to) && !graph.hasEdge(from, to)) {
                    // Calculate temporal weight (closer in time = stronger relationship)
                    long timeDiff = timings.get(j).timestamp() - timings.get(i).timestamp();
                    double weight = Math.max(0.1, 1.0 - (timeDiff / 60000.0)); // Decay over 1 minute
                    
                    graph.addEdge(new DependencyEdge(from, to, EdgeType.TEMPORAL, weight));
                }
            }
        }
    }

    /**
     * Calculate depth of each node from roots.
     */
    private void calculateDepths(ComponentGraph graph) {
        // Find roots (nodes with no incoming edges)
        Set<String> roots = graph.getNodes().values().stream()
                .map(ComponentNode::getId)
                .filter(id -> graph.getIncomingEdges(id).isEmpty())
                .collect(Collectors.toSet());

        // BFS from roots to calculate depths
        Queue<String> queue = new LinkedList<>(roots);
        Map<String, Integer> depths = new HashMap<>();
        
        for (String root : roots) {
            depths.put(root, 0);
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depths.getOrDefault(current, 0);
            
            for (DependencyEdge edge : graph.getOutgoingEdges(current)) {
                String target = edge.getTarget();
                if (!depths.containsKey(target) || depths.get(target) > currentDepth + 1) {
                    depths.put(target, currentDepth + 1);
                    queue.add(target);
                }
            }
        }

        // Update node depths
        for (Map.Entry<String, Integer> entry : depths.entrySet()) {
            ComponentNode node = graph.getNode(entry.getKey());
            if (node != null) {
                node.setDepth(entry.getValue());
            }
        }
    }

    // ========== Data Classes ==========

    private record AnomalyTiming(String component, long timestamp) {}

    /**
     * Component graph representing causal relationships.
     */
    @Data
    public static class ComponentGraph {
        private final Map<String, ComponentNode> nodes = new HashMap<>();
        private final List<DependencyEdge> edges = new ArrayList<>();
        private final Map<String, List<DependencyEdge>> outgoingEdges = new HashMap<>();
        private final Map<String, List<DependencyEdge>> incomingEdges = new HashMap<>();

        public void addNode(ComponentNode node) {
            nodes.put(node.getId(), node);
        }

        public ComponentNode getNode(String id) {
            return nodes.get(id);
        }

        public void addEdge(DependencyEdge edge) {
            edges.add(edge);
            outgoingEdges.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge);
            incomingEdges.computeIfAbsent(edge.getTarget(), k -> new ArrayList<>()).add(edge);
        }

        public boolean hasEdge(String source, String target) {
            return edges.stream()
                    .anyMatch(e -> e.getSource().equals(source) && e.getTarget().equals(target));
        }

        public List<DependencyEdge> getOutgoingEdges(String nodeId) {
            return outgoingEdges.getOrDefault(nodeId, Collections.emptyList());
        }

        public List<DependencyEdge> getIncomingEdges(String nodeId) {
            return incomingEdges.getOrDefault(nodeId, Collections.emptyList());
        }

        public Set<String> findUpstream(String nodeId) {
            Set<String> upstream = new HashSet<>();
            Queue<String> queue = new LinkedList<>();
            queue.add(nodeId);
            
            while (!queue.isEmpty()) {
                String current = queue.poll();
                for (DependencyEdge edge : getIncomingEdges(current)) {
                    if (upstream.add(edge.getSource())) {
                        queue.add(edge.getSource());
                    }
                }
            }
            return upstream;
        }

        public Set<String> findDownstream(String nodeId) {
            Set<String> downstream = new HashSet<>();
            Queue<String> queue = new LinkedList<>();
            queue.add(nodeId);
            
            while (!queue.isEmpty()) {
                String current = queue.poll();
                for (DependencyEdge edge : getOutgoingEdges(current)) {
                    if (downstream.add(edge.getTarget())) {
                        queue.add(edge.getTarget());
                    }
                }
            }
            return downstream;
        }
    }

    /**
     * Node in the causal graph representing a component.
     */
    @Data
    public static class ComponentNode {
        private final String id;
        private int anomalyCount;
        private double maxSeverity;
        private int depth;
        private Map<String, Object> attributes = new HashMap<>();

        public ComponentNode(String id) {
            this.id = id;
        }
    }

    /**
     * Edge representing a dependency or causal relationship.
     */
    @Data
    public static class DependencyEdge {
        private final String source;
        private final String target;
        private final EdgeType type;
        private final double weight;
    }

    /**
     * Types of edges in the causal graph.
     */
    public enum EdgeType {
        /** Direct service dependency */
        DEPENDENCY,
        /** Temporal relationship (earlier to later) */
        TEMPORAL,
        /** Inferred causal relationship */
        CAUSAL,
        /** Correlation without causation */
        CORRELATION
    }
}
