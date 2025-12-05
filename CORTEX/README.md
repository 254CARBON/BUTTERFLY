# CORTEX - AI Agent Orchestration Layer

CORTEX is the AI agent orchestration layer for the BUTTERFLY ecosystem. It provides intelligent agents that can reason, plan, use tools (via SYNAPSE), and maintain memory (via CAPSULE).

## Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                               CORTEX                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Agent Framework                               │   │
│  │  ┌────────────┐  ┌──────────────────┐  ┌─────────────────────────┐  │   │
│  │  │  ReAct     │  │ PlanAndExecute   │  │      Reflexive          │  │   │
│  │  │  Agent     │  │     Agent        │  │       Agent             │  │   │
│  │  │            │  │                  │  │                         │  │   │
│  │  │ Reason-Act │  │ Plan → Approve   │  │ Fast-path simple        │  │   │
│  │  │ -Observe   │  │    → Execute     │  │ responses               │  │   │
│  │  │ Loop       │  │                  │  │                         │  │   │
│  │  └────────────┘  └──────────────────┘  └─────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        LLM Abstraction                               │   │
│  │  ┌────────────┐  ┌────────────────┐  ┌────────────────────────────┐│   │
│  │  │  OpenAI    │  │   Anthropic    │  │         Ollama            ││   │
│  │  │  Provider  │  │   Provider     │  │        Provider           ││   │
│  │  │ GPT-4,etc  │  │  Claude 3,etc  │  │    Llama, Mistral,etc    ││   │
│  │  └────────────┘  └────────────────┘  └────────────────────────────┘│   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Memory Systems                                │   │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────────────┐│   │
│  │  │   Episodic     │  │   Semantic     │  │       Working          ││   │
│  │  │   (Redis)      │  │ (VectorStore)  │  │      (In-Memory)       ││   │
│  │  │                │  │                │  │                        ││   │
│  │  │ Recent history │  │ Long-term      │  │ Per-task context       ││   │
│  │  │ sliding window │  │ knowledge      │  │ scratchpad             ││   │
│  │  └────────────────┘  └────────────────┘  └────────────────────────┘│   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Integrations: SYNAPSE (tools) │ PLATO (governance) │ CAPSULE (memory)     │
│  APIs: REST │ WebSocket (streaming) │ Kafka (async)                        │
│  Port: 8086                                                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Features

### Agent Types

- **ReActAgent**: Implements the Reason-Act-Observe loop for tool-using tasks
- **PlanAndExecuteAgent**: Creates and executes multi-step plans with governance approval
- **ReflexiveAgent**: Fast-path simple responses without tool use

### LLM Providers

- **OpenAI**: GPT-4, GPT-4-turbo, GPT-3.5-turbo with native function calling
- **Anthropic**: Claude 3 Opus, Sonnet, Haiku with tool use
- **Ollama**: Local models (Llama, Mistral) for air-gapped deployments

### Memory Systems

- **Episodic Memory**: Redis Streams for recent conversation history
- **Semantic Memory**: Vector store for long-term knowledge retrieval
- **Working Memory**: Per-task context scratchpad
- **CAPSULE Integration**: Long-term memory persistence

### Safety Features

- Token budget enforcement per task/conversation/daily limits
- Execution quota management (per user, agent, namespace)
- Output validation and guardrails
- PII detection and content filtering
- Tool call sandboxing with timeouts
- Governance checks via PLATO
- Automatic rate limiting

### Agent Service Capabilities

- Full agent lifecycle management (create, update, delete, suspend/activate)
- Task submission with streaming thought support via Reactor Sinks
- Conversation management with episodic memory integration
- Real-time WebSocket streaming of agent reasoning
- Kafka-based async task processing for high-throughput scenarios

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.9+
- Docker and Docker Compose
- Redis
- Kafka

### Local Development

```bash
# Start dependencies
docker-compose up -d redis kafka cassandra

# Run the service
mvn spring-boot:run

# Or build and run
mvn clean package
java -jar target/cortex-service.jar
```

### Docker

```bash
# Build and run with all dependencies
docker-compose up --build

# With local LLM (Ollama)
docker-compose --profile local-llm up --build
```

### Configuration

Set environment variables or update `application.yml`:

```bash
# Required: LLM API keys
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...

# Optional: Service URLs
export SYNAPSE_URL=http://localhost:8085
export PLATO_URL=http://localhost:8083
export CAPSULE_URL=http://localhost:8081
```

## API Reference

### REST Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/agents` | POST | Create a new agent |
| `/api/v1/agents/{id}` | GET | Get agent details |
| `/api/v1/agents` | GET | List all agents |
| `/api/v1/agent-tasks` | POST | Submit a task for execution |
| `/api/v1/agent-tasks/{id}` | GET | Get task status and result |
| `/api/v1/chat` | POST | Send a chat message |
| `/api/v1/chat/stream` | POST | Stream chat response (SSE) |

### WebSocket

Connect to `/ws/agents` for real-time agent interaction and thought streaming.

#### Message Types

**Client → Server:**
```json
// Subscribe to task updates
{ "type": "subscribe", "taskId": "task-123" }

// Unsubscribe from task
{ "type": "unsubscribe", "taskId": "task-123" }

// Execute a task
{ "type": "execute", "agentId": "agent-456", "input": "What is 2+2?", "conversationId": "conv-789" }

// Cancel a running task
{ "type": "cancel", "taskId": "task-123" }

// Heartbeat
{ "type": "ping" }
```

**Server → Client:**
```json
// Task started
{ "type": "task_started", "taskId": "task-123", "data": { "agentId": "agent-456" } }

// Agent thought (reasoning, tool call, etc.)
{ "type": "thought", "taskId": "task-123", "data": { "type": "REASONING", "content": "..." } }

// Task completed
{ "type": "task_completed", "taskId": "task-123" }

// Error
{ "type": "error", "error": "Error message" }

// Acknowledgment
{ "type": "ack", "data": { "message": "Subscribed to task: task-123" } }

// Heartbeat response
{ "type": "pong", "timestamp": "2024-01-15T10:30:00Z" }
```

### Kafka Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `cortex.agent.tasks` | Consumer | Incoming task requests |
| `cortex.agent.results` | Producer | Completed task results |
| `cortex.agent.thoughts` | Producer | Real-time thought streaming |
| `cortex.conversations` | Producer | Conversation state events |

## Architecture

### Package Structure

```
com.z254.butterfly.cortex/
├── agent/          # Agent implementations (ReAct, PlanAndExecute, Reflexive)
├── api/            # REST controllers, DTOs, WebSocket handlers
├── client/         # External service clients (SYNAPSE, PLATO, CAPSULE)
├── config/         # Spring configuration
├── domain/         # Domain models, entities, repositories
├── governance/     # PLATO integration, approval workflows
├── health/         # Health indicators
├── kafka/          # Kafka producers and consumers
├── llm/            # LLM provider abstraction
├── memory/         # Memory subsystems (episodic, semantic, working)
├── observability/  # Logging, tracing, metrics
├── security/       # Security filters
└── tool/           # Tool interface and built-in tools
```

### Integration Points

- **SYNAPSE**: Tool execution via REST/Kafka
- **PLATO**: Governance checks, plan approval, policy evaluation
- **CAPSULE**: Long-term memory storage, historical context
- **PERCEPTION**: Entity queries, event lookups
- **ODYSSEY**: World state, strategic context

## Observability

### Metrics (Prometheus)

- `cortex.agent.tasks` - Task execution metrics
- `cortex.llm.calls` - LLM call latency and token usage
- `cortex.memory.operations` - Memory operation metrics
- `cortex.tool.executions` - Tool execution metrics

### Health Endpoints

- `/actuator/health` - Service health status
- `/actuator/prometheus` - Prometheus metrics
- `/actuator/info` - Service information

## Development

### Building

```bash
mvn clean package
```

### Testing

```bash
# Run all unit tests
mvn test

# Run integration tests (requires Docker for Testcontainers)
mvn verify -Pintegration-tests

# Run a specific test class
mvn test -Dtest=AgentServiceImplTest

# Run tests with coverage report
mvn verify -Pcoverage
```

#### Test Coverage

CORTEX includes comprehensive test coverage:

**Unit Tests:**
- `AgentServiceImplTest` - Agent lifecycle, task execution, conversation management
- `AgentResultProducerTest` - Kafka message production, metrics validation
- `TokenBudgetEnforcerTest` - Budget creation, checking, usage recording
- `ExecutionQuotaManagerTest` - User/agent/namespace quotas, rate limiting

**Integration Tests:**
- `AgentControllerIntegrationTest` - REST API validation with WebTestClient
- `AgentWebSocketHandlerTest` - WebSocket message routing, streaming

**Test Configuration:**
- `CortexTestConfiguration` - Testcontainers setup for Redis, Kafka
- `application-test.yml` - Test-specific configuration

All integration tests use Testcontainers for:
- Redis (episodic memory, quotas, rate limiting)
- Kafka (async task processing, result publishing)

### Code Style

Follow the BUTTERFLY coding standards. Run checkstyle:

```bash
mvn checkstyle:check
```

## License

Proprietary - 254STUDIOZ LLC
