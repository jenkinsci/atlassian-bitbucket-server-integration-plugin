package com.atlassian.bitbucket.jenkins.internal.model;

public enum BitbucketMirroredRepositoryStatus {
    NOT_MIRRORED,
    INITIALIZING,
    AVAILABLE,
    ERROR_INITIALIZING,
    ERROR_AVAILABLE
}
