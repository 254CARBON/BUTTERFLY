package com.z254.butterfly.cortex.orchestration.pattern;

import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.domain.model.*;
import com.z254.butterfly.cortex.orchestration.AgentMessageBus;
import com.z254.butterfly.cortex.orchestration.ResultAggregator;
import com.z254.butterfly.cortex.orchestration.TaskDecomposer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debate coordination pattern.
 * Multiple agents argue different positions and reach consensus through structured debate.
 * 
 * Flow:
 * 1. All agents independently form initial positions
 * 2. Agents present their positions
 * 3. Agents critique each other's positions
 * 4. Multiple rounds of argumentation
 * 5. Consensus is reached or majority wins
 */
@Component
@Slf4j
public class DebateCoordinator implements CoordinationPatternExecutor {

    @Override
    public CoordinationConfig.CoordinationPattern getPattern() {
        return CoordinationConfig.CoordinationPattern.DEBATE;
    }

    @Override
    public boolean supportsTeam(AgentTeam team) {
        // Debate pattern requires at least 2 agents
        return team.getMemberCount() >= 2;
    }

    @Override
    public Flux<TeamThought> execute(TeamTask task, AgentTeam team, AgentService agentService,
                                      TaskDecomposer taskDecomposer, ResultAggregator resultAggregator,
                                      AgentMessageBus messageBus) {
        log.info("Executing debate coordination for task: {}", task.getId());
        
        Sinks.Many<TeamThought> thoughtSink = Sinks.many().multicast().onBackpressureBuffer();
        CoordinationConfig config = team.getCoordinationConfig();
        
        Map<String, Position> positions = new ConcurrentHashMap<>();
        
        task.markDebating(1);
        
        // Phase 1: Initial positions
        getInitialPositions(task, team, agentService, thoughtSink, positions)
                // Phase 2: Debate rounds
                .thenMany(Flux.range(1, config.getMaxDebateRounds())
                        .concatMap(round -> executeDebateRound(task, team, round, agentService, 
                                messageBus, thoughtSink, positions))
                        .takeUntil(consensus -> checkConsensus(positions, config.getConsensusThreshold())))
                // Phase 3: Final consensus or majority vote
                .then(Mono.defer(() -> determineFinalResult(task, team, thoughtSink, positions)))
                .doOnSuccess(result -> {
                    task.setFinalOutput(result);
                    thoughtSink.tryEmitComplete();
                })
                .doOnError(error -> {
                    log.error("Debate coordination failed: {}", error.getMessage());
                    thoughtSink.tryEmitNext(TeamThought.error(task.getId(), null, error.getMessage()));
                    thoughtSink.tryEmitComplete();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        
        return thoughtSink.asFlux();
    }

    private Mono<Void> getInitialPositions(TeamTask task, AgentTeam team, AgentService agentService,
                                           Sinks.Many<TeamThought> thoughtSink,
                                           Map<String, Position> positions) {
        String prompt = String.format("""
                You are participating in a debate about the following question:
                %s
                
                Please state your initial position clearly and concisely.
                Provide reasoning and evidence for your position.
                Be prepared to defend and potentially revise your position.
                """, task.getInput());

        return Flux.fromIterable(team.getMembers())
                .filter(member -> member.getRole() != TeamRole.ORCHESTRATOR)
                .flatMap(member -> {
                    AgentTask agentTask = AgentTask.builder()
                            .agentId(member.getAgentId())
                            .input(prompt)
                            .userId(task.getUserId())
                            .namespace(task.getNamespace())
                            .status(AgentTask.TaskStatus.PENDING)
                            .build();

                    return agentService.executeTask(agentTask)
                            .doOnNext(result -> {
                                Position pos = new Position();
                                pos.agentId = member.getAgentId();
                                pos.content = result.getOutput();
                                pos.confidence = 0.7;
                                pos.round = 0;
                                positions.put(member.getAgentId(), pos);
                                
                                thoughtSink.tryEmitNext(TeamThought.positionStated(
                                        task.getId(), member.getAgentId(), 
                                        getAgentName(team, member.getAgentId()),
                                        result.getOutput(), 0, 0.7));
                            });
                }, 3) // Limit concurrency
                .then();
    }

    private Mono<Boolean> executeDebateRound(TeamTask task, AgentTeam team, int round,
                                              AgentService agentService, AgentMessageBus messageBus,
                                              Sinks.Many<TeamThought> thoughtSink,
                                              Map<String, Position> positions) {
        log.info("Executing debate round {} for task {}", round, task.getId());
        task.markDebating(round);
        
        // Build context with all positions
        StringBuilder positionsContext = new StringBuilder();
        positionsContext.append("Current positions from other participants:\n\n");
        for (Position pos : positions.values()) {
            positionsContext.append("Agent ").append(pos.agentId).append(":\n");
            positionsContext.append(pos.content).append("\n\n");
        }

        return Flux.fromIterable(team.getMembers())
                .filter(member -> member.getRole() != TeamRole.ORCHESTRATOR)
                .flatMap(member -> {
                    Position currentPos = positions.get(member.getAgentId());
                    
                    String prompt = String.format("""
                            Debate Round %d
                            
                            Original question: %s
                            
                            %s
                            
                            Your current position:
                            %s
                            
                            Please:
                            1. Critique the other positions
                            2. Respond to any critiques of your position
                            3. State your updated position (which may be the same, revised, or you may agree with another position)
                            4. Rate your confidence in your position (0.0 - 1.0)
                            
                            Format your response as:
                            CRITIQUE: [your critique of other positions]
                            RESPONSE: [your response to critiques]
                            UPDATED POSITION: [your current position]
                            CONFIDENCE: [0.0-1.0]
                            """, round, task.getInput(), positionsContext,
                            currentPos != null ? currentPos.content : "No previous position");

                    AgentTask agentTask = AgentTask.builder()
                            .agentId(member.getAgentId())
                            .input(prompt)
                            .userId(task.getUserId())
                            .namespace(task.getNamespace())
                            .status(AgentTask.TaskStatus.PENDING)
                            .build();

                    return agentService.executeTask(agentTask)
                            .doOnNext(result -> {
                                // Parse response
                                ParsedResponse parsed = parseDebateResponse(result.getOutput());
                                
                                // Update position
                                Position newPos = new Position();
                                newPos.agentId = member.getAgentId();
                                newPos.content = parsed.position;
                                newPos.confidence = parsed.confidence;
                                newPos.round = round;
                                positions.put(member.getAgentId(), newPos);
                                
                                // Emit thoughts
                                if (parsed.critique != null) {
                                    thoughtSink.tryEmitNext(TeamThought.builder()
                                            .id(UUID.randomUUID().toString())
                                            .teamTaskId(task.getId())
                                            .agentId(member.getAgentId())
                                            .agentName(getAgentName(team, member.getAgentId()))
                                            .type(TeamThought.TeamThoughtType.ARGUMENT_MADE)
                                            .content(parsed.critique)
                                            .round(round)
                                            .phase("debate")
                                            .timestamp(Instant.now())
                                            .build());
                                }
                                
                                thoughtSink.tryEmitNext(TeamThought.positionStated(
                                        task.getId(), member.getAgentId(),
                                        getAgentName(team, member.getAgentId()),
                                        parsed.position, round, parsed.confidence));
                                
                                // Send message to other agents
                                AgentMessage msg = AgentMessage.proposal(
                                        task.getId(), member.getAgentId(),
                                        parsed.position, round, parsed.confidence);
                                messageBus.send(msg).subscribe();
                            });
                }, 2) // Lower concurrency for debate rounds
                .then(Mono.just(checkConsensus(positions, team.getCoordinationConfig().getConsensusThreshold())));
    }

    private boolean checkConsensus(Map<String, Position> positions, double threshold) {
        if (positions.size() < 2) {
            return true;
        }
        
        // Simple consensus check: high confidence positions that are similar
        List<Position> highConfidence = positions.values().stream()
                .filter(p -> p.confidence >= threshold)
                .toList();
        
        if (highConfidence.isEmpty()) {
            return false;
        }
        
        // Check if majority agree (simplified - in practice would use semantic similarity)
        double avgConfidence = positions.values().stream()
                .mapToDouble(p -> p.confidence)
                .average()
                .orElse(0.0);
        
        return avgConfidence >= threshold;
    }

    private Mono<String> determineFinalResult(TeamTask task, AgentTeam team,
                                               Sinks.Many<TeamThought> thoughtSink,
                                               Map<String, Position> positions) {
        // Find highest confidence position(s)
        Position best = positions.values().stream()
                .max(Comparator.comparingDouble(p -> p.confidence))
                .orElse(null);
        
        if (best == null) {
            return Mono.just("No consensus reached");
        }
        
        // Calculate consensus level
        double avgConfidence = positions.values().stream()
                .mapToDouble(p -> p.confidence)
                .average()
                .orElse(0.0);
        
        task.setConsensusLevel(avgConfidence);
        
        thoughtSink.tryEmitNext(TeamThought.consensusReached(
                task.getId(), best.content, best.round, avgConfidence));
        
        return Mono.just(best.content);
    }

    private String getAgentName(AgentTeam team, String agentId) {
        return team.getMember(agentId)
                .map(m -> "Agent-" + agentId.substring(0, Math.min(8, agentId.length())))
                .orElse("Unknown");
    }

    private ParsedResponse parseDebateResponse(String response) {
        ParsedResponse parsed = new ParsedResponse();
        
        // Extract sections from response
        String[] lines = response.split("\n");
        StringBuilder currentSection = new StringBuilder();
        String currentKey = null;
        
        for (String line : lines) {
            String upper = line.toUpperCase();
            if (upper.startsWith("CRITIQUE:")) {
                if (currentKey != null) {
                    setSection(parsed, currentKey, currentSection.toString().trim());
                }
                currentKey = "CRITIQUE";
                currentSection = new StringBuilder(line.substring(9).trim());
            } else if (upper.startsWith("RESPONSE:")) {
                if (currentKey != null) {
                    setSection(parsed, currentKey, currentSection.toString().trim());
                }
                currentKey = "RESPONSE";
                currentSection = new StringBuilder(line.substring(9).trim());
            } else if (upper.startsWith("UPDATED POSITION:")) {
                if (currentKey != null) {
                    setSection(parsed, currentKey, currentSection.toString().trim());
                }
                currentKey = "POSITION";
                currentSection = new StringBuilder(line.substring(17).trim());
            } else if (upper.startsWith("CONFIDENCE:")) {
                if (currentKey != null) {
                    setSection(parsed, currentKey, currentSection.toString().trim());
                }
                currentKey = "CONFIDENCE";
                currentSection = new StringBuilder(line.substring(11).trim());
            } else if (currentKey != null) {
                currentSection.append("\n").append(line);
            }
        }
        
        if (currentKey != null) {
            setSection(parsed, currentKey, currentSection.toString().trim());
        }
        
        // If parsing failed, use full response as position
        if (parsed.position == null || parsed.position.isBlank()) {
            parsed.position = response;
        }
        if (parsed.confidence <= 0 || parsed.confidence > 1) {
            parsed.confidence = 0.5;
        }
        
        return parsed;
    }

    private void setSection(ParsedResponse parsed, String key, String value) {
        switch (key) {
            case "CRITIQUE" -> parsed.critique = value;
            case "RESPONSE" -> parsed.response = value;
            case "POSITION" -> parsed.position = value;
            case "CONFIDENCE" -> {
                try {
                    parsed.confidence = Double.parseDouble(value.replaceAll("[^0-9.]", ""));
                } catch (NumberFormatException e) {
                    parsed.confidence = 0.5;
                }
            }
        }
    }

    private static class Position {
        String agentId;
        String content;
        double confidence;
        int round;
    }

    private static class ParsedResponse {
        String critique;
        String response;
        String position;
        double confidence = 0.5;
    }
}
