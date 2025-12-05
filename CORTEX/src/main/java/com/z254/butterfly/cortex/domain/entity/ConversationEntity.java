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
import java.util.List;
import java.util.Map;

/**
 * Cassandra entity for Conversation storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("conversations")
public class ConversationEntity {

    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED)
    private String id;

    @Column("agent_id")
    private String agentId;

    @Column("user_id")
    private String userId;

    @Column("title")
    private String title;

    @Column("namespace")
    private String namespace;

    @Column("messages")
    private String messages;  // JSON array

    @Column("task_ids")
    private List<String> taskIds;

    @Column("status")
    private String status;

    @Column("total_input_tokens")
    private Integer totalInputTokens;

    @Column("total_output_tokens")
    private Integer totalOutputTokens;

    @Column("total_tokens")
    private Integer totalTokens;

    @Column("message_count")
    private Integer messageCount;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("last_active_at")
    private Instant lastActiveAt;

    @Column("capsule_id")
    private String capsuleId;

    @Column("rim_node_id")
    private String rimNodeId;

    @Column("metadata")
    private Map<String, String> metadata;
}
