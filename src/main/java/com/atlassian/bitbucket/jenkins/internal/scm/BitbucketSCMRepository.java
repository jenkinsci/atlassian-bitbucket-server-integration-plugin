package com.atlassian.bitbucket.jenkins.internal.scm;

import javax.annotation.Nullable;

public class BitbucketSCMRepository {

    private final String credentialsId;
    private final String projectKey;
    private final String projectName;
    private final String repositoryName;
    private final String repositorySlug;
    private final String serverId;
    private final boolean isMirror;

    public BitbucketSCMRepository(@Nullable String credentialsId, String projectName, String projectKey,
                                  String repositoryName, String repositorySlug, @Nullable String serverId,
                                  boolean isMirror) {
        this.credentialsId = credentialsId;
        this.projectName = projectName;
        this.projectKey = projectKey;
        this.repositoryName = repositoryName;
        this.repositorySlug = repositorySlug;
        this.serverId = serverId;
        this.isMirror = isMirror;
    }

    @Nullable
    public String getCredentialsId() {
        return credentialsId;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getRepositorySlug() {
        return repositorySlug;
    }

    @Nullable
    public String getServerId() {
        return serverId;
    }

    public boolean isMirror() {
        return isMirror;
    }
}
