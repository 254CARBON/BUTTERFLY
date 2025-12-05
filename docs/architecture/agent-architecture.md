# Agent Architecture

## Overview

The CORTEX Agent Architecture provides a framework for building intelligent, autonomous agents that can reason, plan, and execute complex tasks within the BUTTERFLY ecosystem.

## Design Principles

1. **Transparency** - All agent reasoning is observable and auditable
2. **Governance** - Actions bounded by PLATO policies
3. **Resilience** - Graceful degradation under LLM failures
4. **Efficiency** - Token budget management prevents runaway costs
5. **Coordination** - Agents collaborate on complex tasks

## Agent Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                    Agent Task Lifecycle                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐  │
│  │  Task    │──▶│ Planning │──▶│ Execution│──▶│  Output  │  │
│  │ Received │   │          │   │          │   │          │  │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘  │
│       │              │              │              │         │
│       ▼              ▼              ▼              ▼         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Thought Stream (Kafka)                   │   │
│  └──────────────────────────────────────────────────────┘   │
│       │              │              │              │         │
│       ▼              ▼              ▼              ▼         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Episode Record (CAPSULE)                 │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Thought Types

Agents generate structured thoughts during execution:

| Type | Description | Example |
|------|-------------|---------|
| `PLANNING` | High-level task decomposition | "I need to first query metrics, then analyze..." |
| `REASONING` | Logical inference | "Given the high latency, the likely cause is..." |
| `TOOL_SELECTION` | Choosing which tool to use | "Using metrics-query tool with parameters..." |
| `TOOL_INVOCATION` | Executing the tool | "Calling service-restart with target=order-svc" |
| `OBSERVATION` | Processing tool results | "The restart succeeded, checking health..." |
| `OUTPUT` | Final response | "Task complete. Remediation applied." |
| `ERROR` | Handling failures | "Tool invocation failed, attempting retry..." |

## Agent Types

### Analyzer Agent
- Specialized in data analysis and pattern recognition
- Uses RAG for knowledge retrieval
- Outputs structured analysis reports

### Executor Agent
- Specialized in executing remediation actions
- Heavy SYNAPSE tool integration
- Focused on safe, incremental changes

### Coordinator Agent
- Orchestrates multi-agent tasks
- Manages handoffs and dependencies
- Ensures task completion

### Validator Agent
- Verifies execution results
- Performs health checks
- Confirms success criteria met

## Multi-Agent Coordination

```
┌─────────────────────────────────────────────────────────────┐
│                 Multi-Agent Task Flow                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐       ┌──────────────┐                    │
│  │   Analyst    │──────▶│   Executor   │                    │
│  │    Agent     │       │    Agent     │                    │
│  └──────────────┘       └──────────────┘                    │
│         │ HANDOFF              │ HANDOFF                     │
│         ▼                      ▼                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │            Coordination Topic (Kafka)                 │   │
│  └──────────────────────────────────────────────────────┘   │
│                            │                                 │
│                            ▼                                 │
│                    ┌──────────────┐                         │
│                    │  Validator   │                         │
│                    │    Agent     │                         │
│                    └──────────────┘                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Coordination Types

| Type | Description |
|------|-------------|
| `HANDOFF` | Sequential task transfer between agents |
| `BROADCAST` | Message to multiple agents |
| `REQUEST_RESPONSE` | Direct agent-to-agent query |
| `SYNC` | Synchronization point for parallel work |

## Token Budget Management

```
┌─────────────────────────────────────────────────────────────┐
│                    Token Budget Flow                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Agent Request                                               │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────┐   Yes    ┌──────────────┐                 │
│  │ Budget       │─────────▶│   Execute    │                 │
│  │ Available?   │          │   LLM Call   │                 │
│  └──────────────┘          └──────────────┘                 │
│       │ No                        │                          │
│       ▼                           ▼                          │
│  ┌──────────────┐          ┌──────────────┐                 │
│  │ Burst        │─────────▶│   Update     │                 │
│  │ Available?   │          │   Budget     │                 │
│  └──────────────┘          └──────────────┘                 │
│       │ No                                                   │
│       ▼                                                      │
│  ┌──────────────┐                                           │
│  │ Throttle/    │                                           │
│  │ Queue        │                                           │
│  └──────────────┘                                           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## LLM Fallback Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                   LLM Fallback Chain                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Primary LLM (e.g., GPT-4)                               │
│       │                                                      │
│       ▼ (3 consecutive failures)                             │
│  2. Secondary LLM (e.g., Claude)                            │
│       │                                                      │
│       ▼ (3 consecutive failures)                             │
│  3. Local Model (e.g., Llama)                               │
│       │                                                      │
│       ▼ (fallback exhausted)                                 │
│  4. Cached Response / Graceful Degradation                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Governance Integration

Agents integrate with PLATO for policy compliance:

1. **Pre-execution check** - Policy evaluated before high-risk actions
2. **Bounded autonomy** - Risk tier determines execution mode
3. **Audit logging** - All decisions recorded for compliance
4. **Kill switch** - Emergency halt of autonomous operations

## Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                     Agent Data Flow                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐     │
│  │  Task   │──▶│ CORTEX  │──▶│ Thought │──▶│ Kafka   │     │
│  │ Input   │   │ Agent   │   │ Events  │   │ Topic   │     │
│  └─────────┘   └─────────┘   └─────────┘   └─────────┘     │
│                     │                            │           │
│                     ▼                            ▼           │
│              ┌─────────────┐            ┌─────────────┐     │
│              │   SYNAPSE   │            │ Visualizer  │     │
│              │  (Tools)    │            │   (UI)      │     │
│              └─────────────┘            └─────────────┘     │
│                     │                                        │
│                     ▼                                        │
│              ┌─────────────┐                                │
│              │   CAPSULE   │                                │
│              │  (History)  │                                │
│              └─────────────┘                                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Best Practices

1. **Task Decomposition** - Break complex tasks into agent-sized pieces
2. **Context Management** - Prune context to stay within token limits
3. **Error Boundaries** - Each agent handles its own failures
4. **Observability** - Instrument all agent operations
5. **Testing** - Use E2E scenarios to validate agent behavior

## Related Documentation

- [CORTEX Service](../services/cortex.md)
- [CORTEX Integration](../integration/cortex-integration.md)
- [PLATO Governance](../architecture/governance-model.md)
