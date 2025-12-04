# BUTTERFLY TypeScript/JavaScript SDK

> Official TypeScript SDK for BUTTERFLY ecosystem integration

**Last Updated**: 2025-12-03  
**SDK Version**: 1.0.0  
**Node.js Version**: 18+

---

## Installation

```bash
# npm
npm install @butterfly/sdk

# yarn
yarn add @butterfly/sdk

# pnpm
pnpm add @butterfly/sdk
```

---

## Quick Start

```typescript
import { ButterflyClient } from '@butterfly/sdk';

const client = new ButterflyClient({
  nexusUrl: 'http://localhost:8084',
  apiKey: 'your-api-key',
});

// Query CAPSULE
const capsules = await client.capsule.query({
  scopeId: 'rim:entity:finance:EURUSD',
  fromTime: '2024-12-01T00:00:00Z',
  toTime: '2024-12-01T12:00:00Z',
});

for (const capsule of capsules) {
  console.log(`Capsule: ${capsule.capsuleId}`);
}
```

---

## Configuration

### Environment Variables

```bash
export BUTTERFLY_NEXUS_URL=http://localhost:8084
export BUTTERFLY_API_KEY=your-api-key
```

```typescript
import { ButterflyClient } from '@butterfly/sdk';

// Automatically reads from environment
const client = ButterflyClient.fromEnv();
```

### Configuration Object

```typescript
import { ButterflyClient, ButterflyConfig } from '@butterfly/sdk';

const config: ButterflyConfig = {
  nexusUrl: 'http://localhost:8084',
  apiKey: 'your-api-key',
  timeout: 30000,
  maxRetries: 3,
  retryBackoff: 1000,
};

const client = new ButterflyClient(config);
```

---

## API Reference

### CAPSULE Operations

#### Create Capsule

```typescript
import { CapsuleRequest, VantageMode } from '@butterfly/sdk';

const request: CapsuleRequest = {
  scopeId: 'rim:entity:finance:EURUSD',
  timestamp: new Date().toISOString(),
  resolution: 60,
  vantageMode: VantageMode.OMNISCIENT,
};

const response = await client.capsule.create(request);
console.log(`Created: ${response.capsuleId}`);
```

#### Query Capsules

```typescript
const capsules = await client.capsule.query({
  scopeId: 'rim:entity:finance:EURUSD',
  fromTime: '2024-12-01T00:00:00Z',
  toTime: '2024-12-01T12:00:00Z',
  vantageMode: VantageMode.CURRENT,
  limit: 100,
});

for (const capsule of capsules) {
  console.log(`${capsule.capsuleId}: ${capsule.timestamp}`);
}
```

#### Time Travel

```typescript
const view = await client.capsule.travelTo({
  entityId: 'rim:entity:finance:EURUSD',
  timestamp: '2024-12-01T12:00:00Z',
});

console.log(`State: ${JSON.stringify(view.state)}`);
console.log(`Visible events: ${view.visibleEvents.length}`);
```

### ODYSSEY Operations

#### Graph Query

```typescript
import { GraphQuery } from '@butterfly/sdk';

const query: GraphQuery = {
  startNode: 'rim:entity:finance:EURUSD',
  traversalDepth: 3,
  relationTypes: ['INFLUENCES', 'CORRELATES_WITH'],
};

const result = await client.odyssey.query(query);

for (const node of result.nodes) {
  console.log(`Node: ${node.rimNodeId}`);
}

for (const edge of result.edges) {
  console.log(`${edge.source} -> ${edge.target}`);
}
```

#### Actor Profile

```typescript
const actor = await client.odyssey.getActor('rim:actor:regulator:FED');

console.log(`Name: ${actor.name}`);
console.log(`Type: ${actor.actorType}`);
console.log(`Influence: ${actor.influenceScore}`);
```

#### Find Paths

```typescript
const paths = await client.odyssey.findPaths({
  source: 'rim:entity:finance:EURUSD',
  target: 'rim:entity:finance:GBPUSD',
  maxDepth: 5,
});

for (const path of paths) {
  console.log(`Path: ${path.nodes.join(' -> ')}`);
  console.log(`Weight: ${path.totalWeight}`);
}
```

### PERCEPTION Operations

#### Submit Event

```typescript
import { PerceptionEventInput, EventType } from '@butterfly/sdk';

const event: PerceptionEventInput = {
  eventType: EventType.MARKET,
  title: 'Significant market movement detected',
  entities: ['rim:entity:finance:EURUSD'],
  source: 'external-system',
  trustScore: 0.8,
};

const ack = await client.perception.submitEvent(event);
console.log(`Event ID: ${ack.eventId}`);
```

#### Query Scenarios

```typescript
import { ScenarioType } from '@butterfly/sdk';

const scenarios = await client.perception.queryScenarios({
  entityId: 'rim:entity:finance:EURUSD',
  scenarioType: ScenarioType.MARKET_IMPACT,
  limit: 10,
});

for (const scenario of scenarios) {
  console.log(`${scenario.title}: ${(scenario.probability * 100).toFixed(2)}%`);
}
```

### PLATO Operations

#### Execute Plan

```typescript
import { PlanRequest, PlatoEngine } from '@butterfly/sdk';

const plan: PlanRequest = {
  name: 'Analysis Plan',
  engine: PlatoEngine.RESEARCH_OS,
  parameters: {
    topic: 'market-volatility',
    depth: 'comprehensive',
  },
};

const result = await client.plato.execute(plan);
console.log(`Plan ID: ${result.planId}`);
console.log(`Status: ${result.status}`);
```

#### Async Plan Execution with Polling

```typescript
async function runPlan(plan: PlanRequest): Promise<PlanResult> {
  let result = await client.plato.execute(plan);
  
  while (result.status === 'RUNNING') {
    await new Promise(resolve => setTimeout(resolve, 1000));
    result = await client.plato.getStatus(result.planId);
  }
  
  return result;
}
```

---

## Streaming

### Server-Sent Events

```typescript
import { createEventSource } from '@butterfly/sdk';

const eventSource = createEventSource(client, {
  endpoint: '/api/v1/events/stream',
  params: { entityId: 'rim:entity:finance:EURUSD' },
});

eventSource.on('event', (event) => {
  console.log(`Received: ${event.type}`);
  console.log(`Data: ${JSON.stringify(event.data)}`);
});

eventSource.on('error', (error) => {
  console.error('Stream error:', error);
});

// Start listening
eventSource.connect();

// Later: disconnect
eventSource.disconnect();
```

### WebSocket

```typescript
import { createWebSocketClient } from '@butterfly/sdk';

const ws = createWebSocketClient(client, {
  endpoint: '/api/v1/ws',
});

ws.on('message', (message) => {
  console.log('Received:', message);
});

ws.on('error', (error) => {
  console.error('WebSocket error:', error);
});

ws.on('close', () => {
  console.log('WebSocket closed');
});

await ws.connect();

// Subscribe to entity updates
ws.send({
  type: 'subscribe',
  entities: ['rim:entity:finance:EURUSD'],
});

// Later: disconnect
ws.disconnect();
```

---

## Error Handling

### Exception Types

```typescript
import {
  ButterflyError,
  AuthenticationError,
  RateLimitError,
  ResourceNotFoundError,
  ServiceUnavailableError,
} from '@butterfly/sdk';
```

### Example

```typescript
import {
  RateLimitError,
  ResourceNotFoundError,
  ButterflyError,
} from '@butterfly/sdk';

try {
  const result = await client.capsule.create(request);
} catch (error) {
  if (error instanceof AuthenticationError) {
    console.error('Check your API key');
    throw error;
  }
  
  if (error instanceof RateLimitError) {
    console.log(`Rate limited - retry after ${error.retryAfter}ms`);
    await new Promise(r => setTimeout(r, error.retryAfter));
    return client.capsule.create(request);
  }
  
  if (error instanceof ResourceNotFoundError) {
    console.log(`Resource not found: ${error.resourceId}`);
    return null;
  }
  
  if (error instanceof ButterflyError) {
    console.error(`API error: ${error.errorCode} - ${error.message}`);
    throw error;
  }
  
  throw error;
}
```

### Retry Wrapper

```typescript
import { withRetry } from '@butterfly/sdk';

const createCapsule = withRetry(
  (request: CapsuleRequest) => client.capsule.create(request),
  {
    maxRetries: 3,
    retryOn: [RateLimitError, ServiceUnavailableError],
    backoff: 'exponential',
  }
);

const result = await createCapsule(request);
```

---

## TypeScript Types

Full TypeScript definitions are included:

```typescript
import type {
  CapsuleRequest,
  CapsuleResponse,
  GraphQuery,
  GraphResult,
  ActorProfile,
  PerceptionEventInput,
  Scenario,
  PlanRequest,
  PlanResult,
} from '@butterfly/sdk';

// All types are fully documented
const request: CapsuleRequest = {
  scopeId: 'rim:entity:finance:EURUSD',  // Required
  timestamp: new Date().toISOString(),    // Required
  resolution: 60,                         // Optional
  vantageMode: VantageMode.CURRENT,       // Optional
};
```

---

## Browser Support

```typescript
import { ButterflyClient } from '@butterfly/sdk/browser';

const client = new ButterflyClient({
  nexusUrl: 'https://api.butterfly.example.com',
  apiKey: 'your-api-key',
});
```

### Bundle Sizes

| Import | Size (gzip) |
|--------|-------------|
| Full SDK | ~25KB |
| Browser bundle | ~15KB |
| Core only | ~8KB |

---

## Testing

### Mock Client

```typescript
import { MockButterflyClient } from '@butterfly/sdk/testing';

describe('MyService', () => {
  let client: MockButterflyClient;
  
  beforeEach(() => {
    client = new MockButterflyClient();
  });
  
  it('should query capsules', async () => {
    client.capsule.mockQuery([
      { capsuleId: 'cap_123', scopeId: 'rim:entity:finance:EURUSD' },
    ]);
    
    const result = await client.capsule.query({
      scopeId: 'rim:entity:finance:EURUSD',
    });
    
    expect(result).toHaveLength(1);
    expect(result[0].capsuleId).toBe('cap_123');
  });
});
```

### Jest Matchers

```typescript
import '@butterfly/sdk/testing/matchers';

expect(response).toBeValidCapsule();
expect(event).toHaveRimNodeId('rim:entity:finance:EURUSD');
```

---

## Framework Integration

### Next.js

```typescript
// lib/butterfly.ts
import { ButterflyClient } from '@butterfly/sdk';

const globalForButterfly = globalThis as unknown as {
  butterfly: ButterflyClient | undefined;
};

export const butterfly =
  globalForButterfly.butterfly ??
  new ButterflyClient({
    nexusUrl: process.env.BUTTERFLY_NEXUS_URL!,
    apiKey: process.env.BUTTERFLY_API_KEY!,
  });

if (process.env.NODE_ENV !== 'production') {
  globalForButterfly.butterfly = butterfly;
}
```

### Express

```typescript
import express from 'express';
import { ButterflyClient } from '@butterfly/sdk';

const app = express();
const butterfly = ButterflyClient.fromEnv();

app.get('/api/capsules/:scopeId', async (req, res) => {
  const capsules = await butterfly.capsule.query({
    scopeId: req.params.scopeId,
  });
  res.json(capsules);
});
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Client Integration Guide](../client-guide.md) | Integration patterns |
| [Kafka Contracts](../kafka-contracts.md) | Event schemas |
| [API Authentication](../../api/authentication.md) | Auth details |
