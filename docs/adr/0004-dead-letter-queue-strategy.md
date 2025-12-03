# ADR-0004: Dead Letter Queue (DLQ) Strategy

## Status

Accepted

## Date

2024-11-25

## Context

In event-driven systems, message processing can fail for various reasons:
- Deserialization errors (malformed JSON/Avro)
- Validation failures (invalid RimNodeId)
- Downstream service unavailability
- Database write failures
- Unexpected runtime exceptions

Without a DLQ strategy:
- Failed messages are lost or block processing
- Manual intervention is required for every failure
- No visibility into failure patterns
- Recovery requires re-publishing from source

BUTTERFLY requires a robust failure handling mechanism that:
1. Preserves failed messages for later analysis
2. Categorizes failures for appropriate handling
3. Supports automated retry for transient failures
4. Provides metrics for alerting and monitoring

## Decision

We will implement a **topic-based DLQ pattern** with shared infrastructure in `butterfly-common`.

### DLQ Topic Naming

Failed messages route to: `{original-topic}.dlq`

Example: `rim.fast-path` → `rim.fast-path.dlq`

### DLQ Record Structure

Each DLQ message includes:

```java
DlqRecord {
    String id;                    // Unique DLQ record ID
    String originalTopic;         // Source topic
    Integer partition;            // Source partition
    Long offset;                  // Source offset
    String key;                   // Original message key
    String payload;               // Original message payload
    Map<String, String> headers;  // Original headers
    Instant originalTimestamp;    // When original was produced
    Instant dlqTimestamp;         // When sent to DLQ
    String errorType;             // Exception class name
    String errorMessage;          // Exception message
    String stackTrace;            // Truncated stack trace
    String consumerGroupId;       // Consumer that failed
    String sourceService;         // Application name
    int retryCount;               // Number of retry attempts
    DlqStatus status;             // PENDING, RETRYING, RESOLVED, DEAD
    FailureCategory failureCategory; // See below
}
```

### Failure Categories

| Category | Description | Retry Strategy |
|----------|-------------|----------------|
| DESERIALIZATION | JSON/Avro parsing failure | No retry (fix producer) |
| VALIDATION | Business rule violation | No retry (fix data) |
| DOWNSTREAM_FAILURE | Service unavailable | Exponential backoff |
| PERSISTENCE | Database write failure | Exponential backoff |
| UNKNOWN | Unexpected error | Manual review |

### Components

1. **DlqPublisher**: Publishes failed messages to DLQ with context
2. **DlqRecord**: Data structure for DLQ messages
3. **DlqRetryService**: Replays messages with backoff
4. **DlqErrorHandler**: Spring Kafka integration

### Metrics

- `dlq.publish.success{topic, failure_category}`
- `dlq.publish.failure{topic, error_type}`
- `dlq.retry.success{topic}`
- `dlq.retry.failure{topic}`

## Consequences

### Positive

- **No message loss**: Failed messages are preserved
- **Categorization**: Different failures get appropriate handling
- **Observability**: Metrics and logs for each failure
- **Automated recovery**: Transient failures retry automatically
- **Audit trail**: Full context preserved for debugging

### Negative

- **Additional topics**: Each topic needs a DLQ topic
- **Storage cost**: DLQ messages accumulate until resolved
- **Operational overhead**: Need process for DLQ review
- **Complexity**: Services must integrate DLQ handling

### Neutral

- DLQ messages are JSON (not Avro) for maximum compatibility
- Stack traces are truncated to 4KB
- Retry backoff is service-configurable

## Alternatives Considered

### Alternative 1: Infinite retry with backoff

Retry forever until success. Rejected because:
- Blocks partition if message is permanently bad
- No visibility into failure patterns
- No way to skip bad messages

### Alternative 2: Log and drop

Log failures and continue. Rejected because:
- Message loss
- No recovery option
- Poor audit trail

### Alternative 3: Database-backed DLQ

Store failed messages in database. Rejected because:
- Additional infrastructure dependency
- Harder to correlate with Kafka offsets
- Less natural for Kafka-centric architecture

## References

- [DlqPublisher.java](../../butterfly-common/src/main/java/com/z254/butterfly/common/kafka/dlq/DlqPublisher.java)
- [DlqRecord.java](../../butterfly-common/src/main/java/com/z254/butterfly/common/kafka/dlq/DlqRecord.java)
- [DlqRetryService.java](../../butterfly-common/src/main/java/com/z254/butterfly/common/kafka/dlq/DlqRetryService.java)

