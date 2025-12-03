# BUTTERFLY Python SDK

> Official Python SDK for BUTTERFLY ecosystem integration

**Last Updated**: 2025-12-03  
**SDK Version**: 1.0.0  
**Python Version**: 3.9+

---

## Installation

```bash
pip install butterfly-sdk

# With async support
pip install butterfly-sdk[async]

# With Kafka support
pip install butterfly-sdk[kafka]

# All extras
pip install butterfly-sdk[all]
```

---

## Quick Start

### Synchronous Client

```python
from butterfly_sdk import ButterflyClient

# Create client
client = ButterflyClient(
    nexus_url="http://localhost:8083",
    api_key="your-api-key"
)

# Query CAPSULE
capsules = client.capsule.query(
    scope_id="rim:entity:finance:EURUSD",
    from_time="2024-12-01T00:00:00Z",
    to_time="2024-12-01T12:00:00Z"
)

for capsule in capsules:
    print(f"Capsule: {capsule.capsule_id}")
```

### Async Client

```python
import asyncio
from butterfly_sdk import AsyncButterflyClient

async def main():
    async with AsyncButterflyClient(
        nexus_url="http://localhost:8083",
        api_key="your-api-key"
    ) as client:
        capsules = await client.capsule.query(
            scope_id="rim:entity:finance:EURUSD",
            from_time="2024-12-01T00:00:00Z",
            to_time="2024-12-01T12:00:00Z"
        )
        
        for capsule in capsules:
            print(f"Capsule: {capsule.capsule_id}")

asyncio.run(main())
```

---

## Configuration

### Environment Variables

```bash
export BUTTERFLY_NEXUS_URL=http://localhost:8083
export BUTTERFLY_API_KEY=your-api-key
```

```python
from butterfly_sdk import ButterflyClient

# Automatically reads from environment
client = ButterflyClient.from_env()
```

### Configuration Object

```python
from butterfly_sdk import ButterflyClient, ButterflyConfig

config = ButterflyConfig(
    nexus_url="http://localhost:8083",
    api_key="your-api-key",
    timeout=30.0,
    max_retries=3,
    retry_backoff=1.0,
)

client = ButterflyClient(config=config)
```

---

## API Reference

### CAPSULE Operations

#### Create Capsule

```python
from butterfly_sdk.models import CapsuleRequest, VantageMode

request = CapsuleRequest(
    scope_id="rim:entity:finance:EURUSD",
    timestamp="2024-12-03T10:00:00Z",
    resolution=60,  # 1-minute resolution
    vantage_mode=VantageMode.OMNISCIENT
)

response = client.capsule.create(request)
print(f"Created: {response.capsule_id}")
```

#### Query Capsules

```python
capsules = client.capsule.query(
    scope_id="rim:entity:finance:EURUSD",
    from_time="2024-12-01T00:00:00Z",
    to_time="2024-12-01T12:00:00Z",
    vantage_mode=VantageMode.CURRENT,
    limit=100
)

for capsule in capsules:
    print(f"{capsule.capsule_id}: {capsule.timestamp}")
```

#### Time Travel

```python
from butterfly_sdk.models import TimePoint

view = client.capsule.travel_to(TimePoint(
    entity_id="rim:entity:finance:EURUSD",
    timestamp="2024-12-01T12:00:00Z"
))

print(f"State: {view.state}")
print(f"Visible events: {view.visible_events}")
```

### ODYSSEY Operations

#### Graph Query

```python
from butterfly_sdk.models import GraphQuery

query = GraphQuery(
    start_node="rim:entity:finance:EURUSD",
    traversal_depth=3,
    relation_types=["INFLUENCES", "CORRELATES_WITH"]
)

result = client.odyssey.query(query)

for node in result.nodes:
    print(f"Node: {node.rim_node_id}")

for edge in result.edges:
    print(f"{edge.source} -> {edge.target}")
```

#### Actor Profile

```python
actor = client.odyssey.get_actor("rim:actor:regulator:FED")

print(f"Name: {actor.name}")
print(f"Type: {actor.actor_type}")
print(f"Influence: {actor.influence_score}")
```

#### Find Paths

```python
from butterfly_sdk.models import PathQuery

paths = client.odyssey.find_paths(PathQuery(
    source="rim:entity:finance:EURUSD",
    target="rim:entity:finance:GBPUSD",
    max_depth=5
))

for path in paths:
    print(f"Path: {' -> '.join(path.nodes)}")
    print(f"Weight: {path.total_weight}")
```

### PERCEPTION Operations

#### Submit Event

```python
from butterfly_sdk.models import PerceptionEventInput, EventType

event = PerceptionEventInput(
    event_type=EventType.MARKET,
    title="Significant market movement detected",
    entities=["rim:entity:finance:EURUSD"],
    source="external-system",
    trust_score=0.8
)

ack = client.perception.submit_event(event)
print(f"Event ID: {ack.event_id}")
```

#### Query Scenarios

```python
from butterfly_sdk.models import ScenarioType

scenarios = client.perception.query_scenarios(
    entity_id="rim:entity:finance:EURUSD",
    scenario_type=ScenarioType.MARKET_IMPACT,
    limit=10
)

for scenario in scenarios:
    print(f"{scenario.title}: {scenario.probability:.2%}")
```

### PLATO Operations

#### Execute Plan

```python
from butterfly_sdk.models import PlanRequest, PlatoEngine

plan = PlanRequest(
    name="Analysis Plan",
    engine=PlatoEngine.RESEARCH_OS,
    parameters={
        "topic": "market-volatility",
        "depth": "comprehensive"
    }
)

result = client.plato.execute(plan)
print(f"Plan ID: {result.plan_id}")
print(f"Status: {result.status}")
```

#### Async Plan Execution

```python
async def run_plan():
    async with AsyncButterflyClient.from_env() as client:
        # Start plan
        result = await client.plato.execute(plan)
        
        # Poll for completion
        while result.status == "RUNNING":
            await asyncio.sleep(1)
            result = await client.plato.get_status(result.plan_id)
        
        return result
```

---

## Kafka Integration

### Consumer

```python
from butterfly_sdk.kafka import ButterflyKafkaConsumer

consumer = ButterflyKafkaConsumer(
    bootstrap_servers="localhost:9092",
    schema_registry_url="http://localhost:8081",
    group_id="my-consumer-group",
    topics=["rim.fast-path"]
)

for event in consumer:
    print(f"Received: {event.rim_node_id}")
    print(f"Price: {event.price}")
    print(f"Stress: {event.stress}")
    consumer.commit()
```

### Async Consumer

```python
from butterfly_sdk.kafka import AsyncButterflyKafkaConsumer

async def consume():
    consumer = AsyncButterflyKafkaConsumer(
        bootstrap_servers="localhost:9092",
        schema_registry_url="http://localhost:8081",
        group_id="my-consumer-group",
        topics=["rim.fast-path"]
    )
    
    async for event in consumer:
        print(f"Received: {event.rim_node_id}")
        await process_event(event)
        await consumer.commit()
```

### Producer

```python
from butterfly_sdk.kafka import ButterflyKafkaProducer
from butterfly_sdk.models.avro import RimFastEvent

producer = ButterflyKafkaProducer(
    bootstrap_servers="localhost:9092",
    schema_registry_url="http://localhost:8081"
)

event = RimFastEvent(
    rim_node_id="rim:entity:finance:EURUSD",
    timestamp=int(time.time() * 1000),
    critical_metrics={"bid": 1.0855, "ask": 1.0857},
    price=1.0856,
    status="ACTIVE"
)

producer.send("rim.fast-path", key=event.rim_node_id, value=event)
producer.flush()
```

---

## Error Handling

### Exception Types

```python
from butterfly_sdk.exceptions import (
    ButterflyError,
    AuthenticationError,
    RateLimitError,
    ResourceNotFoundError,
    ServiceUnavailableError
)
```

### Example

```python
from butterfly_sdk.exceptions import (
    RateLimitError,
    ResourceNotFoundError,
    ButterflyError
)

try:
    result = client.capsule.create(request)
except AuthenticationError:
    print("Check your API key")
    raise
except RateLimitError as e:
    print(f"Rate limited - retry after {e.retry_after}s")
    time.sleep(e.retry_after)
    result = client.capsule.create(request)
except ResourceNotFoundError as e:
    print(f"Resource not found: {e.resource_id}")
except ButterflyError as e:
    print(f"API error: {e.error_code} - {e.message}")
    raise
```

### Retry Decorator

```python
from butterfly_sdk.retry import retry_on_rate_limit

@retry_on_rate_limit(max_retries=3)
def create_capsule(client, request):
    return client.capsule.create(request)
```

---

## Type Hints

The SDK is fully type-hinted:

```python
from butterfly_sdk import ButterflyClient
from butterfly_sdk.models import (
    CapsuleRequest,
    CapsuleResponse,
    GraphQuery,
    GraphResult
)

def query_capsules(
    client: ButterflyClient,
    scope_id: str
) -> list[CapsuleResponse]:
    return client.capsule.query(scope_id=scope_id)
```

---

## Logging

```python
import logging

# Enable SDK logging
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger("butterfly_sdk")
logger.setLevel(logging.DEBUG)
```

---

## Testing

### Mock Client

```python
from butterfly_sdk.testing import MockButterflyClient

def test_capsule_query():
    client = MockButterflyClient()
    
    # Configure mock response
    client.capsule.mock_query([
        {"capsule_id": "cap_123", "scope_id": "rim:entity:finance:EURUSD"}
    ])
    
    result = client.capsule.query(scope_id="rim:entity:finance:EURUSD")
    
    assert len(result) == 1
    assert result[0].capsule_id == "cap_123"
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Client Integration Guide](../client-guide.md) | Integration patterns |
| [Kafka Contracts](../kafka-contracts.md) | Event schemas |
| [API Authentication](../../api/authentication.md) | Auth details |

