package com.z254.butterfly.cortex.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AgentSpecialization domain model.
 */
class AgentSpecializationTest {

    @Test
    void shouldCheckDomain() {
        AgentSpecialization spec = AgentSpecialization.builder()
                .domains(Set.of("finance", "analysis"))
                .build();

        assertThat(spec.hasDomain("finance")).isTrue();
        assertThat(spec.hasDomain("ANALYSIS")).isTrue(); // Case insensitive
        assertThat(spec.hasDomain("code")).isFalse();
    }

    @Test
    void shouldCheckCapability() {
        AgentSpecialization spec = AgentSpecialization.builder()
                .capabilities(Set.of("generation", "review"))
                .build();

        assertThat(spec.hasCapability("generation")).isTrue();
        assertThat(spec.hasCapability("REVIEW")).isTrue(); // Case insensitive
        assertThat(spec.hasCapability("debugging")).isFalse();
    }

    @Test
    void shouldCalculateMatchScore() {
        AgentSpecialization spec = AgentSpecialization.builder()
                .domains(Set.of("finance", "analysis"))
                .capabilities(Set.of("generation", "review"))
                .domainScores(Map.of("finance", 0.9, "analysis", 0.8))
                .capabilityScores(Map.of("generation", 0.85, "review", 0.9))
                .build();

        // Full match
        double fullMatch = spec.calculateMatchScore(
                Set.of("finance"),
                Set.of("generation"));
        assertThat(fullMatch).isGreaterThan(0.5);

        // Partial match
        double partialMatch = spec.calculateMatchScore(
                Set.of("finance", "unknown"),
                Set.of("generation"));
        assertThat(partialMatch).isLessThan(fullMatch);

        // No match
        double noMatch = spec.calculateMatchScore(
                Set.of("unknown"),
                Set.of("unknown"));
        assertThat(noMatch).isEqualTo(0.0);
    }

    @Test
    void shouldUpdateDomainScore() {
        AgentSpecialization spec = AgentSpecialization.builder()
                .domains(Set.of("finance"))
                .domainScores(Map.of("finance", 0.5))
                .build();

        spec.updateDomainScore("finance", 1.0);

        // EMA update: 0.5 * 0.8 + 1.0 * 0.2 = 0.6
        assertThat(spec.getDomainScore("finance")).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void shouldCreateGeneralPurposeSpecialization() {
        AgentSpecialization spec = AgentSpecialization.generalPurpose();

        assertThat(spec.hasDomain("general")).isTrue();
        assertThat(spec.hasCapability("analysis")).isTrue();
        assertThat(spec.getConfidenceThreshold()).isEqualTo(0.5);
    }

    @Test
    void shouldCreateCodeSpecialist() {
        AgentSpecialization spec = AgentSpecialization.codeSpecialist();

        assertThat(spec.hasDomain("code")).isTrue();
        assertThat(spec.hasDomain("software")).isTrue();
        assertThat(spec.hasCapability("debugging")).isTrue();
        assertThat(spec.getConfidenceThreshold()).isEqualTo(0.7);
    }

    @Test
    void shouldCreateResearchSpecialist() {
        AgentSpecialization spec = AgentSpecialization.researchSpecialist();

        assertThat(spec.hasDomain("research")).isTrue();
        assertThat(spec.hasCapability("fact-checking")).isTrue();
        assertThat(spec.getConfidenceThreshold()).isEqualTo(0.8);
    }

    @Test
    void shouldReturnNeutralScoreForEmptyRequirements() {
        AgentSpecialization spec = AgentSpecialization.generalPurpose();

        double score = spec.calculateMatchScore(null, null);
        assertThat(score).isEqualTo(0.5);

        double emptyScore = spec.calculateMatchScore(Set.of(), Set.of());
        assertThat(emptyScore).isEqualTo(0.5);
    }
}
