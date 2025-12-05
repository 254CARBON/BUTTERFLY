package com.z254.butterfly.cortex.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuration properties for CORTEX service.
 */
@Data
@Component
@ConfigurationProperties(prefix = "cortex")
public class CortexProperties {

    private LLMProperties llm = new LLMProperties();
    private MemoryProperties memory = new MemoryProperties();
    private SafetyProperties safety = new SafetyProperties();
    private AgentProperties agent = new AgentProperties();
    private MultiAgentProperties multiAgent = new MultiAgentProperties();
    private KafkaProperties kafka = new KafkaProperties();
    private ClientProperties clients = new ClientProperties();
    private SecurityProperties security = new SecurityProperties();

    @Data
    public static class LLMProperties {
        private String defaultProvider = "openai";
        private boolean cacheEnabled = true;
        private Duration cacheTtl = Duration.ofMinutes(60);
        private int maxCacheSize = 1000;
        private OpenAIProperties openai = new OpenAIProperties();
        private AnthropicProperties anthropic = new AnthropicProperties();
        private OllamaProperties ollama = new OllamaProperties();

        @Data
        public static class OpenAIProperties {
            private boolean enabled = true;
            private String apiKey;
            private String baseUrl = "https://api.openai.com/v1";
            private String model = "gpt-4-turbo";
            private String embeddingModel = "text-embedding-3-small";
            private int maxTokens = 4096;
            private double temperature = 0.7;
            private Duration timeout = Duration.ofSeconds(120);
            private int maxRetries = 3;
        }

        @Data
        public static class AnthropicProperties {
            private boolean enabled = true;
            private String apiKey;
            private String baseUrl = "https://api.anthropic.com/v1";
            private String model = "claude-3-sonnet-20240229";
            private int maxTokens = 4096;
            private double temperature = 0.7;
            private Duration timeout = Duration.ofSeconds(120);
            private int maxRetries = 3;
        }

        @Data
        public static class OllamaProperties {
            private boolean enabled = false;
            private String baseUrl = "http://localhost:11434";
            private String model = "llama3";
            private String embeddingModel = "nomic-embed-text";
            private int maxTokens = 4096;
            private double temperature = 0.7;
            private Duration timeout = Duration.ofSeconds(300);
        }
    }

    @Data
    public static class MemoryProperties {
        private EpisodicProperties episodic = new EpisodicProperties();
        private SemanticProperties semantic = new SemanticProperties();
        private WorkingProperties working = new WorkingProperties();
        private CapsuleProperties capsule = new CapsuleProperties();

        @Data
        public static class EpisodicProperties {
            private int maxEpisodes = 100;
            private Duration retentionPeriod = Duration.ofHours(168); // 7 days
            private String redisKeyPrefix = "cortex:episodic:";
            private boolean pruningEnabled = true;
            private Duration pruningInterval = Duration.ofHours(1);
        }

        @Data
        public static class SemanticProperties {
            private String vectorStore = "in-memory"; // in-memory, pinecone, milvus, pgvector
            private int embeddingDimension = 1536;
            private int topK = 10;
            private double similarityThreshold = 0.7;
            private boolean compressionEnabled = true;
            private int compressionThreshold = 1000; // Compress after this many entries
        }

        @Data
        public static class WorkingProperties {
            private int maxEntries = 100;
            private Duration entryTtl = Duration.ofHours(24);
        }

        @Data
        public static class CapsuleProperties {
            private boolean enabled = true;
            private boolean syncOnComplete = true;
            private Duration snapshotInterval = Duration.ofMinutes(5);
        }
    }

    @Data
    public static class SafetyProperties {
        private boolean enabled = true;
        private TokenBudgetProperties tokenBudget = new TokenBudgetProperties();
        private GuardrailsProperties guardrails = new GuardrailsProperties();
        private QuotaProperties quotas = new QuotaProperties();
        private ToolSandboxProperties toolSandbox = new ToolSandboxProperties();

        @Data
        public static class TokenBudgetProperties {
            private int maxPerTask = 16000;
            private int maxPerConversation = 100000;
            private int maxPerDay = 1000000;
            private boolean strictEnforcement = true;
            private double warningThreshold = 0.8; // Warn at 80% of budget
        }

        @Data
        public static class GuardrailsProperties {
            private boolean piiDetectionEnabled = true;
            private boolean contentFilteringEnabled = true;
            private boolean outputValidationEnabled = true;
            private Set<String> blockedPatterns = new HashSet<>();
            private Set<String> allowedDomains = new HashSet<>();
        }

        @Data
        public static class QuotaProperties {
            private int maxTasksPerUserPerDay = 100;
            private int maxTasksPerAgentPerDay = 500;
            private int maxTasksPerNamespacePerDay = 1000;
            private int maxRequestsPerMinute = 30;
            private int maxConcurrentTasks = 10;
            private Duration quotaWindow = Duration.ofHours(1);
        }

        @Data
        public static class ToolSandboxProperties {
            private boolean enabled = true;
            private Duration defaultTimeout = Duration.ofSeconds(30);
            private int maxConcurrentCalls = 5;
            private int maxCallsPerTask = 20;
            private int maxConsecutiveSameToolCalls = 3;
            private int maxParameterCount = 10;
            private int maxParameterSize = 2048;
            private Duration highRiskTimeout = Duration.ofSeconds(15);
            private boolean mockExternalCalls = false;
        }
    }

    @Data
    public static class AgentProperties {
        private ReActProperties react = new ReActProperties();
        private PlanAndExecuteProperties planAndExecute = new PlanAndExecuteProperties();
        private ReflexiveProperties reflexive = new ReflexiveProperties();
        private ExecutionProperties execution = new ExecutionProperties();

        @Data
        public static class ReActProperties {
            private int maxIterations = 10;
            private int maxToolCalls = 20;
            private boolean streamThoughts = true;
            private Duration thinkingTimeout = Duration.ofSeconds(60);
        }

        @Data
        public static class PlanAndExecuteProperties {
            private int maxPlanSteps = 20;
            private boolean requireApproval = true;
            private boolean replanOnFailure = true;
            private int maxReplans = 3;
        }

        @Data
        public static class ReflexiveProperties {
            private int maxTokens = 2048;
            private boolean escalateOnComplexity = true;
            private double complexityThreshold = 0.7;
        }

        @Data
        public static class ExecutionProperties {
            private int threadPoolSize = 20;
            private int maxQueueSize = 1000;
            private Duration defaultTimeout = Duration.ofMinutes(5);
            private boolean metricsEnabled = true;
        }
    }

    /**
     * Multi-agent orchestration properties.
     */
    @Data
    public static class MultiAgentProperties {
        /**
         * Whether multi-agent orchestration is enabled.
         */
        private boolean enabled = true;

        /**
         * Maximum number of agents in a team.
         */
        private int maxTeamSize = 10;

        /**
         * Maximum number of subtasks per team task.
         */
        private int maxSubTasks = 20;

        /**
         * Maximum number of debate rounds.
         */
        private int maxDebateRounds = 5;

        /**
         * Consensus threshold for debate teams (0.0 - 1.0).
         */
        private double consensusThreshold = 0.7;

        /**
         * Default timeout for team tasks.
         */
        private Duration teamTaskTimeout = Duration.ofMinutes(10);

        /**
         * Default timeout for subtasks.
         */
        private Duration subTaskTimeout = Duration.ofMinutes(3);

        /**
         * Maximum concurrent subtasks per team.
         */
        private int maxConcurrentSubTasks = 5;

        /**
         * Token budget for team tasks.
         */
        private int tokenBudget = 50000;

        /**
         * Token budget per agent per task.
         */
        private int tokenBudgetPerAgent = 10000;

        /**
         * Default coordination pattern.
         */
        private String defaultPattern = "HIERARCHICAL";

        /**
         * Maximum messages in a team conversation.
         */
        private int maxMessages = 100;

        /**
         * Whether to deduplicate similar messages.
         */
        private boolean deduplicateMessages = true;

        /**
         * Whether to enable critic review by default.
         */
        private boolean enableCriticReview = true;

        /**
         * Whether to require governance approval for team tasks.
         */
        private boolean requireGovernanceApproval = false;

        /**
         * Whether to persist team conversations to CAPSULE.
         */
        private boolean persistToCapsule = true;
    }

    @Data
    public static class KafkaProperties {
        private String schemaRegistryUrl = "http://localhost:8081";
        private TopicProperties topics = new TopicProperties();

        @Data
        public static class TopicProperties {
            private String agentTasks = "cortex.agent.tasks";
            private String agentResults = "cortex.agent.results";
            private String agentThoughts = "cortex.agent.thoughts";
            private String conversations = "cortex.conversations";
            private String dlq = "cortex.dlq";
            
            // Multi-agent team topics
            private String teamTasks = "cortex.team.tasks";
            private String teamResults = "cortex.team.results";
            private String teamThoughts = "cortex.team.thoughts";
            private String agentMessages = "cortex.agent.messages";
        }
    }

    @Data
    public static class ClientProperties {
        private SynapseClientProperties synapse = new SynapseClientProperties();
        private PlatoClientProperties plato = new PlatoClientProperties();
        private CapsuleClientProperties capsule = new CapsuleClientProperties();
        private PerceptionClientProperties perception = new PerceptionClientProperties();
        private OdysseyClientProperties odyssey = new OdysseyClientProperties();

        @Data
        public static class SynapseClientProperties {
            private String baseUrl = "http://localhost:8085";
            private Duration timeout = Duration.ofSeconds(30);
            private int maxRetries = 3;
            private boolean circuitBreakerEnabled = true;
        }

        @Data
        public static class PlatoClientProperties {
            private String baseUrl = "http://localhost:8083";
            private Duration timeout = Duration.ofSeconds(30);
            private int maxRetries = 3;
            private boolean circuitBreakerEnabled = true;
            private int approvalPollMaxRetries = 12;
            private Duration approvalTimeout = Duration.ofMinutes(5);
        }

        @Data
        public static class CapsuleClientProperties {
            private String baseUrl = "http://localhost:8081";
            private Duration timeout = Duration.ofSeconds(30);
            private int maxRetries = 3;
            private boolean circuitBreakerEnabled = true;
        }

        @Data
        public static class PerceptionClientProperties {
            private String baseUrl = "http://localhost:8080";
            private Duration timeout = Duration.ofSeconds(30);
            private int maxRetries = 3;
        }

        @Data
        public static class OdysseyClientProperties {
            private String baseUrl = "http://localhost:8082";
            private Duration timeout = Duration.ofSeconds(30);
            private int maxRetries = 3;
        }
    }

    @Data
    public static class SecurityProperties {
        private ApiKeyProperties apiKey = new ApiKeyProperties();
        private JwtProperties jwt = new JwtProperties();

        @Data
        public static class ApiKeyProperties {
            private boolean enabled = true;
            private String headerName = "X-API-Key";
        }

        @Data
        public static class JwtProperties {
            private boolean enabled = true;
            private String issuerUri;
        }
    }

    /**
     * Agent types supported by CORTEX.
     */
    public enum AgentType {
        /**
         * Reason-Act-Observe loop agent for tool-using tasks.
         */
        REACT,
        
        /**
         * Plan first, then execute step-by-step.
         */
        PLAN_AND_EXECUTE,
        
        /**
         * Fast-path simple response agent.
         */
        REFLEXIVE
    }
}
