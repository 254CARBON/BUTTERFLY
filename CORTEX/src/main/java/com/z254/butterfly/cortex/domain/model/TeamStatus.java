package com.z254.butterfly.cortex.domain.model;

/**
 * Status of an agent team.
 */
public enum TeamStatus {

    /**
     * Team is being configured and is not yet ready.
     */
    DRAFT,

    /**
     * Team is active and can accept tasks.
     */
    ACTIVE,

    /**
     * Team is temporarily paused.
     */
    PAUSED,

    /**
     * Team is suspended due to issues.
     */
    SUSPENDED,

    /**
     * Team is archived and no longer in use.
     */
    ARCHIVED
}
