package com.z254.butterfly.cortex.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AgentTeam domain model.
 */
class AgentTeamTest {

    @Test
    void shouldCreateHierarchicalTeam() {
        String orchestratorId = "orchestrator-001";
        AgentTeam team = AgentTeam.hierarchical("Test Team", orchestratorId);

        assertThat(team.getName()).isEqualTo("Test Team");
        assertThat(team.getTeamType()).isEqualTo(TeamType.HIERARCHICAL);
        assertThat(team.getOrchestratorAgentId()).isEqualTo(orchestratorId);
        assertThat(team.getMemberCount()).isEqualTo(1);
        assertThat(team.getOrchestrator()).isPresent();
    }

    @Test
    void shouldCreateDebateTeam() {
        List<String> agentIds = List.of("agent1", "agent2", "agent3");
        AgentTeam team = AgentTeam.debate("Debate Team", agentIds);

        assertThat(team.getTeamType()).isEqualTo(TeamType.DEBATE);
        assertThat(team.getMemberCount()).isEqualTo(3);
        assertThat(team.getCoordinationConfig().getPattern())
                .isEqualTo(CoordinationConfig.CoordinationPattern.DEBATE);
    }

    @Test
    void shouldAddMember() {
        AgentTeam team = AgentTeam.hierarchical("Test Team", "orchestrator-001");
        
        TeamMember specialist = TeamMember.specialist("specialist-001", Set.of("analysis"));
        team.addMember(specialist);

        assertThat(team.getMemberCount()).isEqualTo(2);
        assertThat(team.getMember("specialist-001")).isPresent();
    }

    @Test
    void shouldRemoveMember() {
        AgentTeam team = AgentTeam.hierarchical("Test Team", "orchestrator-001");
        team.addMember(TeamMember.specialist("specialist-001", Set.of("analysis")));
        
        boolean removed = team.removeMember("specialist-001");

        assertThat(removed).isTrue();
        assertThat(team.getMemberCount()).isEqualTo(1);
    }

    @Test
    void shouldFindMembersByRole() {
        AgentTeam team = AgentTeam.hierarchical("Test Team", "orchestrator-001");
        team.addMember(TeamMember.specialist("specialist-001", Set.of("analysis")));
        team.addMember(TeamMember.specialist("specialist-002", Set.of("code")));
        team.addMember(TeamMember.critic("critic-001"));

        List<TeamMember> specialists = team.getMembersByRole(TeamRole.SPECIALIST);
        List<TeamMember> critics = team.getMembersByRole(TeamRole.CRITIC);

        assertThat(specialists).hasSize(2);
        assertThat(critics).hasSize(1);
    }

    @Test
    void shouldFindMembersWithSpecialization() {
        AgentTeam team = AgentTeam.hierarchical("Test Team", "orchestrator-001");
        team.addMember(TeamMember.specialist("specialist-001", Set.of("analysis", "data")));
        team.addMember(TeamMember.specialist("specialist-002", Set.of("code")));

        List<TeamMember> analysts = team.getMembersWithSpecialization("analysis");

        assertThat(analysts).hasSize(1);
        assertThat(analysts.get(0).getAgentId()).isEqualTo("specialist-001");
    }

    @Test
    void shouldBeReadyWhenActive() {
        AgentTeam team = AgentTeam.hierarchical("Test Team", "orchestrator-001");
        team.setStatus(TeamStatus.ACTIVE);

        assertThat(team.isReady()).isTrue();
    }

    @Test
    void shouldNotBeReadyWhenDraft() {
        AgentTeam team = AgentTeam.hierarchical("Test Team", "orchestrator-001");
        team.setStatus(TeamStatus.DRAFT);

        assertThat(team.isReady()).isFalse();
    }

    @Test
    void shouldNotBeReadyWithoutOrchestrator() {
        AgentTeam team = AgentTeam.builder()
                .name("Test Team")
                .teamType(TeamType.HIERARCHICAL)
                .status(TeamStatus.ACTIVE)
                .build();

        assertThat(team.isReady()).isFalse();
    }

    @Test
    void shouldActivateWhenReady() {
        AgentTeam team = AgentTeam.hierarchical("Test Team", "orchestrator-001");
        team.activate();

        assertThat(team.getStatus()).isEqualTo(TeamStatus.ACTIVE);
    }

    @Test
    void shouldFailActivationWhenNotReady() {
        AgentTeam team = AgentTeam.builder()
                .name("Test Team")
                .teamType(TeamType.HIERARCHICAL)
                .status(TeamStatus.DRAFT)
                .build();

        assertThatThrownBy(team::activate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRecordTaskCompletion() {
        AgentTeam team = AgentTeam.hierarchical("Test Team", "orchestrator-001");
        
        team.recordTaskCompletion(true, 5000);
        team.recordTaskCompletion(true, 3000);
        team.recordTaskCompletion(false, 10000);

        assertThat(team.getTotalTasks()).isEqualTo(3);
        assertThat(team.getSuccessfulTasks()).isEqualTo(2);
        assertThat(team.getSuccessRate()).isCloseTo(0.666, org.assertj.core.data.Offset.offset(0.01));
    }
}
