# SYNAPSE Connector Compatibility Matrix

This document provides a comprehensive overview of all SYNAPSE connectors, their capabilities, supported operations, authentication methods, and sandbox behavior.

## Quick Reference

| Connector | Type | Sandbox | DryRun | Cancel | Operations | Rate Limits | Auth Method |
|-----------|------|---------|--------|--------|------------|-------------|-------------|
| Slack | MESSAGING | ✅ | ✅ | N/A | 8 | Tier 1-4 | OAuth Token |
| Teams | MESSAGING | ✅ | ✅ | N/A | 4 | Webhook | Webhook URL |
| Email | MESSAGING | ✅ | ✅ | N/A | 4 | 100/batch | SMTP |
| Jira | TICKETING | ✅ | ✅ | N/A | 13 | Cloud limits | Basic Auth |
| PagerDuty | INCIDENT | ✅ | ✅ | N/A | 10 | 120/min | API Token |
| S3 | STORAGE | ✅ | ✅ | N/A | 8 | AWS limits | AWS SigV4 |
| HTTP | HTTP | ✅ | ✅ | ✅ | 6 | Configurable | Various |
| Database | DATABASE | ✅ | ✅ | N/A | 4 | Connection pool | JDBC |
| Kafka | MESSAGING | ✅ | ✅ | N/A | 3 | Broker limits | SASL |
| Webhook | HTTP | ✅ | ✅ | N/A | 2 | Configurable | Various |
| GitHubActions | WORKFLOW | ✅ | ✅ | N/A | 4 | API limits | PAT/App |

---

## Connector Capabilities

### Legend
- ✅ Fully supported
- ⚠️ Partial/Limited support
- ❌ Not supported

| Capability | Slack | Teams | Email | Jira | PagerDuty | S3 | HTTP |
|------------|-------|-------|-------|------|-----------|-----|------|
| SANDBOX_MODE | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| DRY_RUN_MODE | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| CANCEL_SUPPORT | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| SIDE_EFFECT_TRACKING | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| CIRCUIT_BREAKER | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| RETRY_WITH_BACKOFF | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| RATE_LIMIT_AWARE | ✅ | ⚠️ | ⚠️ | ✅ | ✅ | ✅ | ⚠️ |
| BULK_OPERATIONS | ⚠️ | ❌ | ✅ | ⚠️ | ⚠️ | ⚠️ | ❌ |
| PAGINATION | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ |
| INPUT_VALIDATION | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## Detailed Connector Information

### Slack Connector

**Type:** MESSAGING  
**Connector Name:** `slack`  
**Tool ID Pattern:** `slack_*`, `messaging` with `slack` in config

#### Supported Operations

| Operation | Description | Required Parameters | Side Effect |
|-----------|-------------|---------------------|-------------|
| `SEND_MESSAGE` | Send a basic text message | `channel`, `text` | MESSAGE_SENT |
| `POST_MESSAGE` | Post a rich message with blocks | `channel`, `text` | MESSAGE_CREATED |
| `UPDATE_MESSAGE` | Update an existing message | `channel`, `ts`, `text` | MESSAGE_UPDATED |
| `DELETE_MESSAGE` | Delete a message | `channel`, `ts` | MESSAGE_DELETED |
| `ADD_REACTION` | Add emoji reaction | `channel`, `ts`, `emoji` | REACTION_ADDED |
| `REMOVE_REACTION` | Remove emoji reaction | `channel`, `ts`, `emoji` | REACTION_REMOVED |
| `LIST_CHANNELS` | List available channels | `limit` (optional) | None |
| `UPLOAD_FILE` | Upload file to channel | `channel`, `content` | FILE_UPLOADED |

#### Authentication
- OAuth Bot Token (xoxb-*)
- User Token (xoxp-*) for some operations

#### Rate Limits
- Tier 1: 1 req/sec
- Tier 2: 20 req/min
- Tier 3: 50 req/min
- Tier 4: 100 req/min

#### Sandbox Response Schema
```json
{
  "ok": true,
  "channel": "C012345678",
  "ts": "1234567890.123456",
  "sandbox": true
}
```

---

### Microsoft Teams Connector

**Type:** MESSAGING  
**Connector Name:** `teams`  
**Tool ID Pattern:** `teams_*`, `microsoft_*`

#### Supported Operations

| Operation | Description | Required Parameters | Side Effect |
|-----------|-------------|---------------------|-------------|
| `SEND_MESSAGE` | Send text via webhook | `webhook_url`, `text` | MESSAGE_SENT |
| `SEND_CARD` | Send adaptive card | `webhook_url`, `card` | MESSAGE_SENT |
| `SEND_ALERT` | Send alert message | `webhook_url`, `message` | MESSAGE_SENT |
| `SEND_NOTIFICATION` | Send notification | `webhook_url`, `summary` | MESSAGE_SENT |

#### Authentication
- Incoming Webhook URL

#### Rate Limits
- Webhook: 4 messages/second per webhook

#### Sandbox Response Schema
```json
{
  "sent": true,
  "response": "1",
  "sandbox": true
}
```

---

### Jira Connector

**Type:** TICKETING  
**Connector Name:** `jira`  
**Tool ID Pattern:** `jira_*`, `ticketing` with Jira config

#### Supported Operations

| Operation | Description | Required Parameters | Side Effect |
|-----------|-------------|---------------------|-------------|
| `CREATE_ISSUE` | Create new issue | `project_key`, `summary` | TICKET_CREATED |
| `GET_ISSUE` | Get issue details | `issue_key` | None |
| `UPDATE_ISSUE` | Update issue fields | `issue_key`, `fields` | TICKET_UPDATED |
| `DELETE_ISSUE` | Delete issue | `issue_key` | TICKET_DELETED |
| `TRANSITION_ISSUE` | Change issue status | `issue_key`, `transition_*` | TICKET_TRANSITIONED |
| `ADD_COMMENT` | Add comment | `issue_key`, `body` | COMMENT_ADDED |
| `ASSIGN_ISSUE` | Assign to user | `issue_key`, `account_id` | TICKET_ASSIGNED |
| `SEARCH_ISSUES` | JQL search | `jql` | None |
| `GET_TRANSITIONS` | Get available transitions | `issue_key` | None |
| `LINK_ISSUES` | Link two issues | `inward_issue`, `outward_issue` | TICKETS_LINKED |
| `GET_PROJECT` | Get project details | `project_key` | None |
| `LIST_PROJECTS` | List all projects | None | None |
| `ADD_ATTACHMENT` | Add attachment | `issue_key`, `file` | ATTACHMENT_ADDED |

#### Authentication
- Basic Auth (email:api_token)
- Jira Cloud uses Atlassian Document Format (ADF) for rich text

#### Sandbox Response Schema
```json
{
  "id": "10001",
  "key": "PROJ-123",
  "self": "https://mock.atlassian.net/rest/api/3/issue/PROJ-123",
  "sandbox": true
}
```

---

### PagerDuty Connector

**Type:** INCIDENT  
**Connector Name:** `pagerduty`  
**Tool ID Pattern:** `pagerduty_*`, `incident_*`

#### Supported Operations

| Operation | Description | Required Parameters | Side Effect |
|-----------|-------------|---------------------|-------------|
| `CREATE_INCIDENT` | Create incident | `service_id`, `title` | INCIDENT_CREATED |
| `GET_INCIDENT` | Get incident details | `incident_id` | None |
| `UPDATE_INCIDENT` | Update incident | `incident_id`, `*` | INCIDENT_UPDATED |
| `RESOLVE_INCIDENT` | Resolve incident | `incident_id` | INCIDENT_RESOLVED |
| `ACKNOWLEDGE_INCIDENT` | Acknowledge | `incident_id` | INCIDENT_ACKNOWLEDGED |
| `TRIGGER_EVENT` | Events API v2 | `routing_key`, `summary` | EVENT_TRIGGERED |
| `LIST_INCIDENTS` | List incidents | None | None |
| `ADD_NOTE` | Add note to incident | `incident_id`, `content` | INCIDENT_NOTE_ADDED |
| `REASSIGN_INCIDENT` | Reassign | `incident_id`, `assignee_*` | INCIDENT_REASSIGNED |
| `MERGE_INCIDENTS` | Merge incidents | `source_incidents`, `target` | INCIDENTS_MERGED |

#### Authentication
- REST API: API Token (Bearer)
- Events API: Integration Key

#### Rate Limits
- 120 requests/minute per API token

---

### S3 Connector

**Type:** STORAGE  
**Connector Name:** `s3`  
**Tool ID Pattern:** `s3_*`, `aws_*`, `storage_*`

#### Supported Operations

| Operation | Description | Required Parameters | Side Effect |
|-----------|-------------|---------------------|-------------|
| `PUT_OBJECT` | Upload object | `bucket`, `key`, `content` | OBJECT_UPLOADED |
| `LIST_OBJECTS` | List bucket contents | `bucket` | None |
| `GET_OBJECT_METADATA` | Head object | `bucket`, `key` | None |
| `DELETE_OBJECT` | Delete object | `bucket`, `key` | OBJECT_DELETED |
| `COPY_OBJECT` | Copy object | `source_*`, `destination_*` | OBJECT_COPIED |
| `GENERATE_PRESIGNED_URL` | Generate URL | `bucket`, `key` | None |
| `LIST_BUCKETS` | List buckets | None | None |
| `HEAD_BUCKET` | Check bucket exists | `bucket` | None |

#### Authentication
- AWS Signature Version 4
- Credentials: Access Key ID + Secret Access Key
- Region-specific endpoints

#### Sandbox Response Schema
```json
{
  "bucket": "test-bucket",
  "key": "test-key",
  "etag": "\"mock-etag-12345\"",
  "sandbox": true
}
```

---

### Email Connector

**Type:** MESSAGING  
**Connector Name:** `email`  
**Tool ID Pattern:** `email_*`, `mail_*`

#### Supported Operations

| Operation | Description | Required Parameters | Side Effect |
|-----------|-------------|---------------------|-------------|
| `SEND_EMAIL` | Send plain text | `to`, `subject`, `body` | EMAIL_SENT |
| `SEND_HTML_EMAIL` | Send HTML email | `to`, `subject`, `body` | EMAIL_SENT |
| `SEND_TEMPLATE_EMAIL` | Send templated | `to`, `template_id`, `data` | EMAIL_SENT |
| `SEND_BULK_EMAIL` | Bulk send | `recipients`, `subject`, `body` | BULK_EMAIL_SENT |

#### Authentication
- SMTP credentials (host, port, username, password)

---

## Unified Error Codes

All connectors use the standardized `ConnectorErrorCode` enum:

### Client Errors
| Code | Description | Retriable |
|------|-------------|-----------|
| `MISSING_PARAMETER` | Required parameter not provided | No |
| `INVALID_PARAMETER` | Parameter value invalid | No |
| `UNSUPPORTED_OPERATION` | Operation not supported | No |
| `VALIDATION_ERROR` | Business rule violation | No |
| `RESOURCE_NOT_FOUND` | Resource doesn't exist | No |
| `PERMISSION_DENIED` | Access denied | No |

### Configuration Errors
| Code | Description | Retriable |
|------|-------------|-----------|
| `CONFIGURATION_ERROR` | Connector misconfigured | No |
| `AUTHENTICATION_ERROR` | Invalid credentials | No |
| `AUTHORIZATION_ERROR` | Insufficient scope | No |

### External Service Errors
| Code | Description | Retriable |
|------|-------------|-----------|
| `EXTERNAL_SERVICE_ERROR` | Service returned error | Yes |
| `RATE_LIMITED` | Rate limit exceeded | Yes |
| `TIMEOUT` | Request timed out | Yes |
| `NETWORK_ERROR` | Network issue | Yes |
| `SERVICE_UNAVAILABLE` | Service down | Yes |

### Connector Operational Errors
| Code | Description | Retriable |
|------|-------------|-----------|
| `CIRCUIT_BREAKER_OPEN` | Circuit breaker tripped | Yes |
| `BULKHEAD_FULL` | Capacity exceeded | Yes |
| `RETRIES_EXHAUSTED` | All retries failed | No |
| `EXECUTION_ERROR` | Internal connector error | Yes |
| `CANCELLED` | Operation cancelled | No |

---

## Side Effect Types

Standardized side effect types via `SideEffectType` enum:

### Messaging
- `MESSAGE_SENT` / `MESSAGE_CREATED`
- `MESSAGE_UPDATED`
- `MESSAGE_DELETED`
- `REACTION_ADDED` / `REACTION_REMOVED`
- `FILE_UPLOADED`

### Ticketing
- `TICKET_CREATED` / `TICKET_UPDATED` / `TICKET_DELETED`
- `TICKET_TRANSITIONED`
- `TICKET_ASSIGNED`
- `COMMENT_ADDED`
- `TICKETS_LINKED`

### Incident Management
- `INCIDENT_CREATED` / `INCIDENT_UPDATED`
- `INCIDENT_ACKNOWLEDGED`
- `INCIDENT_RESOLVED`
- `INCIDENT_REASSIGNED`
- `EVENT_TRIGGERED`

### Storage
- `OBJECT_UPLOADED` / `OBJECT_CREATED`
- `OBJECT_DELETED`
- `OBJECT_COPIED`
- `OBJECT_MOVED`

---

## Metrics Pattern

All connectors follow the standardized metric naming pattern:

```
synapse.connector.{connector}.{metric}
```

### Standard Metrics

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `operations.total` | Counter | connector | Total operations executed |
| `operations.success` | Counter | connector | Successful operations |
| `operations.failure` | Counter | connector | Failed operations |
| `operations.sandbox` | Counter | connector | Sandbox mode operations |
| `operations.dryrun` | Counter | connector | Dry-run mode operations |
| `duration` | Timer | connector, operation, mode | Operation duration |
| `errors` | Counter | connector, error_code, operation | Errors by type |
| `side_effects` | Counter | connector, side_effect_type | Side effects produced |
| `circuit_breaker` | Counter | connector, state | Circuit breaker events |
| `retries` | Counter | connector, operation | Retry attempts |

---

## Sandbox Behavior

### Common Characteristics

1. **No External Calls**: Sandbox mode never contacts external services
2. **Realistic Responses**: Mock data matches real API response shapes
3. **No Side Effects**: `sideEffects` list is always empty in sandbox
4. **Fast Execution**: No network latency
5. **Validation**: Input validation still performed

### Per-Connector Mock Response Fields

| Connector | Mock Identifiers |
|-----------|------------------|
| Slack | `sandbox: true`, mock `ts` timestamp |
| Teams | `sandbox: true`, `response: "1"` |
| Jira | `sandbox: true`, mock issue key |
| PagerDuty | `sandbox: true`, mock incident ID |
| S3 | `sandbox: true`, mock ETag |
| Email | `sandbox: true`, `sent: true` |

---

## Conformance Testing

All connectors are validated against the `ConnectorConformanceContract` interface:

### Required Test Coverage

1. **Sandbox Mode**: Returns success without external calls
2. **Dry-Run Mode**: Validates inputs, returns expected response
3. **Error Handling**: Standard error codes for missing params, unsupported ops
4. **Side Effects**: Tracked in production, empty in sandbox
5. **Metrics**: Standard metrics recorded
6. **Capabilities**: Declared capabilities are accurate

### Running Conformance Tests

```bash
# Run all conformance tests
mvn test -Dgroups=conformance

# Run for specific connector
mvn test -Dtest="*ConnectorConformanceTest"
```

---

## Best Practices

### For Connector Implementers

1. Extend `AbstractConnector` for new connectors
2. Use `ConnectorErrorCode` enum for all errors
3. Use `SideEffectType` enum for side effects
4. Use `ConnectorMetrics` for all metrics
5. Implement realistic `generateMockResponse()`
6. Add conformance test extending `AbstractConnectorConformanceTest`

### For Connector Users

1. Always handle both success and failure outcomes
2. Use sandbox mode for testing integrations
3. Check `sideEffects` for audit trails
4. Monitor connector metrics for operational visibility
5. Implement retry logic respecting `retriable` error flag

---

*Document Version: 1.0*  
*Last Updated: 2025-12-05*  
*Maintainer: SYNAPSE Team*
