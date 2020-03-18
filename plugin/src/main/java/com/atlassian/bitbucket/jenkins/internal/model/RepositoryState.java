package com.atlassian.bitbucket.jenkins.internal.model;

@SuppressWarnings("unused")

/** State of the repository as declared in the Bitbucket Server javadoc. */
public enum RepositoryState {
    /**
     * Repository is available and ready to be cloned.
     */
    AVAILABLE,
    /**
     * Repository failed to initialize and cannot be cloned.
     */
    INITIALISATION_FAILED,
    /**
     * Repository is in the process of being initialized and cannot be clone yet.
     */
    INITIALISING
}
