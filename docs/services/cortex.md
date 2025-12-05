# CORTEX Service

CORTEX is the AI agent orchestration layer of the BUTTERFLY ecosystem. It provides intelligent agent capabilities with LLM integration, memory systems, tool use, and governance compliance.

## Overview

CORTEX enables the creation and management of AI agents that can:
- Reason through complex tasks using the ReAct pattern
- Create and execute multi-step plans
- Use tools and integrate with other BUTTERFLY services
- Maintain conversation history and semantic memory
- Comply with governance policies via PLATO integration

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                           CORTEX Service                             │
├─────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │   REST API   │  │  WebSocket   │  │    Kafka     │              │
│  │  /api/v1/*   │  │   Streaming  │  │  Consumer/   │              │
│  │              │  │              │  │  Producer    │              │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘              │
│         │                 │                 │                        │
│         └────────────────┬┴─────────────────┘                        │
│                          │                                           │
│  ┌───────────────────────▼──────────────────────────────┐           │
│  │                   Agent Service                       │           │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐      │           │
│  │  │   ReAct    │  │ PlanExec   │  │ Reflexive  │      │           │
│  │  │   Agent    │  │   Agent    │  │   Agent    │      │           │
│  │  └────────────┘  └────────────┘  └────────────┘      │           │
│  └───────────────────────┬──────────────────────────────┘           │
│                          │                                           │
│  ┌───────────────────────▼──────────────────────────────┐           │
│  │                  Core Components                      │           │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐      │           │
│  │  │    LLM     │  │   Memory   │  │    Tool    │      │           │
│  │  │ Providers  │  │  Systems   │  │  Registry  │      │           │
│  │  └────────────┘  └────────────┘  └────────────┘      │           │
│  └──────────────────────────────────────────────────────┘           │
│                          │                                           │
│  ┌───────────────────────▼──────────────────────────────┐           │
│  │               External Integrations                   │           │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐        │           │
│  │  │ PLATO  │ │CAPSULE │ │SYNAPSE │ │PERCEPTION│        │           │
│  │  └────────┘ └────────┘ └────────┘ └────────┘        │           │
│  └──────────────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────────────┘
```

## Agent Types

### ReAct Agent
The ReAct (Reason-Act-Observe) agent uses a reasoning loop:
1. **Reason**: Analyze the current state
2. **Act**: Execute a tool or generate output
3. **Observe**: Process the result and decide next steps

Best for tasks requiring tool use and multi-step reasoning.

### Plan-and-Execute Agent
Creates a plan first, optionally gets approval, then executes step-by-step:
1. Analyze task and create multi-step plan
2. Submit plan for governance approval (optional)
3. Execute each step sequentially
4. Synthesize final answer

Best for complex tasks requiring human oversight.

### Reflexive Agent
Fast-path agent for simple tasks:
- Direct LLM response without tool use
- Lower latency than ReAct
- Can escalate to ReAct if complexity detected

Best for simple Q&A and text generation.

## LLM Providers

CORTEX supports multiple LLM providers:

| Provider | Models | Features |
|----------|--------|----------|
| OpenAI | GPT-4, GPT-4-turbo, GPT-3.5-turbo | Streaming, function calling, embeddings |
| Anthropic | Claude 3 Opus, Sonnet, Haiku | Streaming, tool use |
| Ollama | Llama, Mistral, CodeLlama | Local deployment, embeddings |

## Memory Systems

### Episodic Memory
- Redis Streams-based storage
- Sliding window with retention
- Records user/assistant messages and tool calls

### Semantic Memory
- Vector store-backed (in-memory stub, production: Pinecone/Milvus)
- Semantic search using embeddings
- Long-term knowledge storage

### Working Memory
- Per-task context storage
- In-memory key-value store
- Cleared after task completion

### CAPSULE Integration
- Historical state storage
- Time-travel queries
- Conversation archival

## API Endpoints

### Agents
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/agents` | POST | Create agent |
| `/api/v1/agents/{id}` | GET | Get agent |
| `/api/v1/agents` | GET | List agents |
| `/api/v1/agents/{id}` | PUT | Update agent |
| `/api/v1/agents/{id}` | DELETE | Delete agent |

### Tasks
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/agent-tasks` | POST | Submit task (async) |
| `/api/v1/agent-tasks/execute` | POST | Execute task (sync) |
| `/api/v1/agent-tasks/stream` | POST | Execute with streaming (SSE) |
| `/api/v1/agent-tasks/{id}` | GET | Get task status |
| `/api/v1/agent-tasks/{id}/result` | GET | Get task result |
| `/api/v1/agent-tasks/{id}/cancel` | POST | Cancel task |

### Chat
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/chat` | POST | Send message |
| `/api/v1/chat/stream` | POST | Send with streaming |
| `/api/v1/chat/conversations` | GET | List conversations |
| `/api/v1/chat/conversations` | POST | Create conversation |
| `/api/v1/chat/conversations/{id}` | GET | Get conversation |

## Kafka Topics

| Topic | Description |
|-------|-------------|
| `cortex.agent.tasks` | Task requests for async processing |
| `cortex.agent.results` | Task completion results |
| `cortex.agent.thoughts` | Streaming agent thoughts |
| `cortex.conversations` | Conversation events |

## Safety Features

### Token Budgets
- Per-task, per-conversation, and daily limits
- Warning thresholds at 90% usage
- Automatic enforcement

### Tool Sandboxing
- Execution timeouts
- Parameter validation
- Call quotas per task

### Execution Quotas
- User daily limits
- Agent daily limits
- Namespace limits
- Rate limiting (requests/minute)

### Guardrails
- PII detection and redaction
- Content filtering
- Blocked patterns
- Output validation

## Governance Integration

CORTEX integrates with PLATO for governance:

- **Policy Evaluation**: Check if actions are allowed
- **Plan Submission**: Submit plans for approval
- **Evidence Collection**: Track execution evidence
- **Approval Workflows**: Handle human-in-the-loop

## Configuration

Key configuration properties in `application.yml`:

```yaml
cortex:
  llm:
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-3-sonnet-20240229
    ollama:
      enabled: false
      base-url: http://localhost:11434
      model: llama3
  
  memory:
    episodic:
      max-episodes: 100
      retention-period: PT24H
    semantic:
      similarity-threshold: 0.7
    capsule:
      enabled: true
  
  safety:
    token-budget:
      max-per-task: 4000
      max-per-day: 100000
    guardrails:
      pii-detection-enabled: true
      content-filtering-enabled: true
    quota:
      max-tasks-per-user-per-day: 1000
      max-requests-per-minute: 60
```

## Related Services

- **PLATO**: Governance and policy enforcement
- **CAPSULE**: Historical state storage
- **SYNAPSE**: Tool execution bridge
- **PERCEPTION**: Entity and event queries
- **ODYSSEY**: Strategic context

## Development

### Quick Start
```bash
cd CORTEX
docker-compose up -d
./mvnw spring-boot:run
```

### Running Tests
```bash
./mvnw test
```

### Building
```bash
./mvnw clean package
docker build -t cortex-service .
```

## See Also

- [Architecture Overview](../architecture/ecosystem-overview.md)
- [API Documentation](../api/README.md)
- [Security Model](../security/security-model.md)
