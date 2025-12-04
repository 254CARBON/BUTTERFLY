# BUTTERFLY Webhook Integration

> Webhook configuration and event delivery

**Last Updated**: 2025-12-03  
**Target Audience**: Integration developers

---

## Overview

Webhooks provide a way to receive real-time notifications when events occur in BUTTERFLY, without the need to poll APIs.

---

## Webhook Registration

### Create Webhook

```bash
curl -X POST http://localhost:8084/api/v1/webhooks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://your-app.com/webhooks/butterfly",
    "events": ["capsule.created", "event.detected", "plan.completed"],
    "secret": "your-webhook-secret-256-bits",
    "active": true,
    "description": "Production webhook for event notifications"
  }'
```

**Response:**

```json
{
  "id": "whk_abc123",
  "url": "https://your-app.com/webhooks/butterfly",
  "events": ["capsule.created", "event.detected", "plan.completed"],
  "active": true,
  "created_at": "2024-12-03T10:00:00Z"
}
```

### List Webhooks

```bash
curl http://localhost:8084/api/v1/webhooks \
  -H "Authorization: Bearer $TOKEN"
```

### Update Webhook

```bash
curl -X PUT http://localhost:8084/api/v1/webhooks/whk_abc123 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "events": ["capsule.created"],
    "active": true
  }'
```

### Delete Webhook

```bash
curl -X DELETE http://localhost:8084/api/v1/webhooks/whk_abc123 \
  -H "Authorization: Bearer $TOKEN"
```

---

## Event Types

### Available Events

| Event | Description | Service |
|-------|-------------|---------|
| `capsule.created` | New CAPSULE created | CAPSULE |
| `capsule.deleted` | CAPSULE deleted | CAPSULE |
| `event.detected` | New event detected | PERCEPTION |
| `signal.detected` | Weak signal detected | PERCEPTION |
| `scenario.created` | New scenario generated | PERCEPTION |
| `plan.started` | Plan execution started | PLATO |
| `plan.completed` | Plan execution completed | PLATO |
| `plan.failed` | Plan execution failed | PLATO |
| `spec.published` | Spec published | PLATO |
| `governance.violation` | Policy violation detected | PLATO |

### Event Payload Structure

All webhook payloads follow this structure:

```json
{
  "id": "evt_xyz789",
  "type": "capsule.created",
  "timestamp": "2024-12-03T10:00:00Z",
  "data": {
    // Event-specific data
  },
  "meta": {
    "webhook_id": "whk_abc123",
    "delivery_id": "del_123456",
    "attempt": 1
  }
}
```

---

## Webhook Delivery

### Delivery Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Webhook Delivery Flow                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  BUTTERFLY                                           Your Server            │
│      │                                                    │                 │
│      │  Event occurs                                      │                 │
│      │                                                    │                 │
│      │  POST https://your-app.com/webhooks/butterfly     │                 │
│      │  Headers:                                          │                 │
│      │    Content-Type: application/json                  │                 │
│      │    X-Butterfly-Signature: sha256=...               │                 │
│      │    X-Butterfly-Delivery: del_123456                │                 │
│      │    X-Butterfly-Event: capsule.created              │                 │
│      │                                                    │                 │
│      │────────────────────────────────────────────────────▶│                 │
│      │                                                    │                 │
│      │                                    Verify signature │                 │
│      │                                    Process event    │                 │
│      │                                                    │                 │
│      │  200 OK                                            │                 │
│      │◀────────────────────────────────────────────────────│                 │
│      │                                                    │                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Request Headers

| Header | Description |
|--------|-------------|
| `Content-Type` | `application/json` |
| `X-Butterfly-Signature` | HMAC-SHA256 signature |
| `X-Butterfly-Delivery` | Unique delivery ID |
| `X-Butterfly-Event` | Event type |
| `X-Butterfly-Timestamp` | Event timestamp |

### Retry Policy

| Attempt | Delay | Total Wait |
|---------|-------|------------|
| 1 | 0 | 0 |
| 2 | 1 minute | 1 minute |
| 3 | 5 minutes | 6 minutes |
| 4 | 30 minutes | 36 minutes |
| 5 | 2 hours | ~2.5 hours |

After 5 failed attempts, the delivery is marked as failed.

---

## Signature Verification

### Signature Format

```
X-Butterfly-Signature: sha256=hex_signature
```

### Verification Algorithm

1. Get the raw request body
2. Compute HMAC-SHA256 using your webhook secret
3. Compare with the signature in the header

### Python Example

```python
import hmac
import hashlib

def verify_webhook(request_body: bytes, signature_header: str, secret: str) -> bool:
    # Parse signature
    if not signature_header.startswith("sha256="):
        return False
    
    received_sig = signature_header[7:]  # Remove "sha256=" prefix
    
    # Compute expected signature
    expected_sig = hmac.new(
        secret.encode('utf-8'),
        request_body,
        hashlib.sha256
    ).hexdigest()
    
    # Constant-time comparison
    return hmac.compare_digest(received_sig, expected_sig)

# Flask example
@app.route('/webhooks/butterfly', methods=['POST'])
def handle_webhook():
    signature = request.headers.get('X-Butterfly-Signature')
    
    if not verify_webhook(request.data, signature, WEBHOOK_SECRET):
        return 'Invalid signature', 401
    
    event = request.json
    process_event(event)
    
    return 'OK', 200
```

### Node.js Example

```javascript
const crypto = require('crypto');

function verifyWebhook(body, signatureHeader, secret) {
    if (!signatureHeader.startsWith('sha256=')) {
        return false;
    }
    
    const receivedSig = signatureHeader.slice(7);
    const expectedSig = crypto
        .createHmac('sha256', secret)
        .update(body, 'utf8')
        .digest('hex');
    
    return crypto.timingSafeEqual(
        Buffer.from(receivedSig),
        Buffer.from(expectedSig)
    );
}

// Express example
app.post('/webhooks/butterfly', (req, res) => {
    const signature = req.headers['x-butterfly-signature'];
    const rawBody = JSON.stringify(req.body);
    
    if (!verifyWebhook(rawBody, signature, process.env.WEBHOOK_SECRET)) {
        return res.status(401).send('Invalid signature');
    }
    
    const event = req.body;
    processEvent(event);
    
    res.status(200).send('OK');
});
```

### Java Example

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

public class WebhookVerifier {
    
    public static boolean verify(String payload, String signature, String secret) {
        if (!signature.startsWith("sha256=")) {
            return false;
        }
        
        String receivedSig = signature.substring(7);
        
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), 
                "HmacSHA256"
            );
            mac.init(keySpec);
            
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSig = bytesToHex(hash);
            
            return MessageDigest.isEqual(
                receivedSig.getBytes(), 
                expectedSig.getBytes()
            );
        } catch (Exception e) {
            return false;
        }
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
```

---

## Event Payload Examples

### capsule.created

```json
{
  "id": "evt_cap123",
  "type": "capsule.created",
  "timestamp": "2024-12-03T10:00:00Z",
  "data": {
    "capsule_id": "cap_abc123",
    "scope_id": "rim:entity:finance:EURUSD",
    "timestamp": "2024-12-03T10:00:00Z",
    "resolution": 60,
    "vantage_mode": "omniscient"
  }
}
```

### event.detected

```json
{
  "id": "evt_det456",
  "type": "event.detected",
  "timestamp": "2024-12-03T10:00:00Z",
  "data": {
    "event_id": "det_xyz789",
    "event_type": "MARKET",
    "title": "Fed Rate Decision Announced",
    "entities": ["rim:entity:finance:USD", "rim:actor:regulator:FED"],
    "trust_score": 0.95,
    "impact": {
      "magnitude": 0.8,
      "direction": "negative"
    }
  }
}
```

### plan.completed

```json
{
  "id": "evt_plan789",
  "type": "plan.completed",
  "timestamp": "2024-12-03T10:05:00Z",
  "data": {
    "plan_id": "plan_abc",
    "status": "SUCCESS",
    "duration_ms": 5000,
    "artifacts": [
      {"id": "art_123", "type": "REPORT"}
    ],
    "proofs": [
      {"id": "prf_456", "status": "VERIFIED"}
    ]
  }
}
```

---

## Best Practices

### 1. Respond Quickly

Return 2xx within 10 seconds. Process events asynchronously:

```python
@app.route('/webhooks/butterfly', methods=['POST'])
def handle_webhook():
    # Verify signature
    if not verify_webhook(request.data, request.headers.get('X-Butterfly-Signature'), SECRET):
        return '', 401
    
    # Queue for async processing
    event = request.json
    task_queue.enqueue(process_event, event)
    
    # Return immediately
    return '', 200
```

### 2. Handle Idempotency

Events may be delivered multiple times. Use the delivery ID:

```python
def process_event(event):
    delivery_id = event['meta']['delivery_id']
    
    # Check if already processed
    if redis.sismember('processed_deliveries', delivery_id):
        return  # Skip duplicate
    
    # Process event
    do_processing(event)
    
    # Mark as processed
    redis.sadd('processed_deliveries', delivery_id)
    redis.expire('processed_deliveries', 86400)  # 24 hour TTL
```

### 3. Handle Failures Gracefully

Return appropriate status codes:

| Status | Meaning | BUTTERFLY Action |
|--------|---------|------------------|
| 2xx | Success | Mark delivered |
| 4xx | Client error | Mark failed (no retry) |
| 5xx | Server error | Retry with backoff |
| Timeout | No response | Retry with backoff |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Integration Guide](README.md) | Integration overview |
| [API Authentication](../api/authentication.md) | Auth patterns |
