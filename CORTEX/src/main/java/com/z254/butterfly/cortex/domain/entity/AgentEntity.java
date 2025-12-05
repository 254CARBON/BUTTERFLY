package com.z254.butterfly.cortex.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Cassandra entity for Agent storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("agents")
public class AgentEntity {

    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED)
    private String id;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("type")
    private String type;

    @Column("system_prompt")
    private String systemPrompt;

    @Column("tool_ids")
    private Set<String> toolIds;

    @Column("memory_config")
    private String memoryConfig;  // JSON

    @Column("llm_config")
    private String llmConfig;  // JSON

    @Column("governance_config")
    private String governanceConfig;  // JSON

    @Column("status")
    private String status;

    @Column("version")
    private String version;

    @Column("namespace")
    private String namespace;

    @Column("owner")
    private String owner;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("last_used_at")
    private Instant lastUsedAt;

    @Column("task_count")
    private Long taskCount;

    @Column("success_rate")
    private Double successRate;

    @Column("metadata")
    private Map<String, String> metadata;
}
