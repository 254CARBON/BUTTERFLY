package com.z254.butterfly.cortex.memory.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.memory.EpisodicMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Streams-based episodic memory implementation.
 * Uses Redis Streams for ordered, time-based episode storage with automatic pruning.
 */
@Component
@Slf4j
public class RedisEpisodicMemory implements EpisodicMemory {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CortexProperties.MemoryProperties.EpisodicProperties config;

    public RedisEpisodicMemory(
            ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            CortexProperties cortexProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.config = cortexProperties.getMemory().getEpisodic();
    }

    @Override
    public Mono<Void> record(String conversationId, Episode episode) {
        String streamKey = getStreamKey(conversationId);
        
        Map<String, String> episodeMap = serializeEpisode(episode);
        
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .in(streamKey)
                .ofMap(episodeMap);
        
        return redisTemplate.opsForStream()
                .add(record)
                .doOnSuccess(id -> log.debug("Recorded episode {} in conversation {}", 
                        episode.getId(), conversationId))
                .then(trimStream(streamKey));
    }

    @Override
    public Flux<Episode> retrieve(String conversationId, int limit) {
        String streamKey = getStreamKey(conversationId);
        
        // XREVRANGE to get most recent first, then reverse for chronological order
        return redisTemplate.opsForStream()
                .reverseRange(streamKey, org.springframework.data.domain.Range.unbounded())
                .take(limit)
                .map(this::recordToEpisode)
                .collectList()
                .flatMapMany(list -> {
                    java.util.Collections.reverse(list);
                    return Flux.fromIterable(list);
                });
    }

    @Override
    public Flux<Episode> retrieveByTimeRange(String conversationId, Instant start, Instant end) {
        String streamKey = getStreamKey(conversationId);
        
        // Redis stream IDs are timestamp-based
        String startId = start.toEpochMilli() + "-0";
        String endId = end.toEpochMilli() + "-9999999";
        
        return redisTemplate.opsForStream()
                .range(streamKey, org.springframework.data.domain.Range.closed(startId, endId))
                .map(this::recordToEpisode);
    }

    @Override
    public Mono<Long> prune(String conversationId, Duration retention) {
        String streamKey = getStreamKey(conversationId);
        long minId = Instant.now().minus(retention).toEpochMilli();
        String minIdStr = minId + "-0";
        
        // XTRIM with MINID
        return redisTemplate.opsForStream()
                .size(streamKey)
                .flatMap(sizeBefore -> redisTemplate.execute(connection ->
                        connection.streamCommands().xTrim(
                                java.nio.ByteBuffer.wrap(streamKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                                minId,
                                true  // approximate
                        ))
                        .next()
                        .defaultIfEmpty(0L)
                        .map(removed -> {
                            log.debug("Pruned {} episodes from conversation {}", removed, conversationId);
                            return removed;
                        }));
    }

    @Override
    public Mono<Long> count(String conversationId) {
        String streamKey = getStreamKey(conversationId);
        return redisTemplate.opsForStream().size(streamKey);
    }

    @Override
    public Mono<Void> clear(String conversationId) {
        String streamKey = getStreamKey(conversationId);
        return redisTemplate.delete(streamKey).then();
    }

    private String getStreamKey(String conversationId) {
        return config.getRedisKeyPrefix() + conversationId;
    }

    private Map<String, String> serializeEpisode(Episode episode) {
        Map<String, String> map = new HashMap<>();
        map.put("id", episode.getId());
        map.put("type", episode.getType().name());
        map.put("role", episode.getRole());
        if (episode.getContent() != null) {
            map.put("content", episode.getContent());
        }
        if (episode.getTaskId() != null) {
            map.put("taskId", episode.getTaskId());
        }
        if (episode.getToolName() != null) {
            map.put("toolName", episode.getToolName());
        }
        if (episode.getToolResult() != null) {
            map.put("toolResult", episode.getToolResult());
        }
        if (episode.getTokenCount() != null) {
            map.put("tokenCount", String.valueOf(episode.getTokenCount()));
        }
        map.put("timestamp", episode.getTimestamp().toString());
        if (episode.getMetadata() != null) {
            try {
                map.put("metadata", objectMapper.writeValueAsString(episode.getMetadata()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize episode metadata", e);
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Episode recordToEpisode(MapRecord<String, Object, Object> record) {
        Map<Object, Object> map = record.getValue();
        
        Episode.EpisodeBuilder builder = Episode.builder()
                .id((String) map.get("id"))
                .type(EpisodeType.valueOf((String) map.get("type")))
                .role((String) map.get("role"))
                .content((String) map.get("content"))
                .taskId((String) map.get("taskId"))
                .toolName((String) map.get("toolName"))
                .toolResult((String) map.get("toolResult"));
        
        String tokenCount = (String) map.get("tokenCount");
        if (tokenCount != null) {
            builder.tokenCount(Integer.parseInt(tokenCount));
        }
        
        String timestamp = (String) map.get("timestamp");
        if (timestamp != null) {
            builder.timestamp(Instant.parse(timestamp));
        }
        
        String metadata = (String) map.get("metadata");
        if (metadata != null) {
            try {
                builder.metadata(objectMapper.readValue(metadata, Map.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize episode metadata", e);
            }
        }
        
        return builder.build();
    }

    private Mono<Void> trimStream(String streamKey) {
        // Keep only the most recent N episodes
        return redisTemplate.opsForStream()
                .trim(streamKey, config.getMaxEpisodes(), true)
                .then();
    }

    /**
     * Scheduled task to prune old episodes across all conversations.
     */
    @Scheduled(fixedRateString = "${cortex.memory.episodic.pruning-interval:PT1H}")
    public void scheduledPruning() {
        if (!config.isPruningEnabled()) {
            return;
        }
        
        log.debug("Starting scheduled episodic memory pruning");
        
        // Find all conversation streams and prune each
        String pattern = config.getRedisKeyPrefix() + "*";
        redisTemplate.keys(pattern)
                .flatMap(key -> {
                    String conversationId = key.replace(config.getRedisKeyPrefix(), "");
                    return prune(conversationId, config.getRetentionPeriod());
                })
                .reduce(0L, Long::sum)
                .subscribe(total -> log.info("Episodic memory pruning complete: {} episodes removed", total));
    }
}
