package com.atlassian.bitbucket.jenkins.internal.scm;

import javax.annotation.Nullable;

import static com.google.common.base.Objects.firstNonNull;

public class BitbucketSCMRepository {

    private final String credentialsId;
    private final String projectKey;
    private final String projectName;
    private final String repositoryName;
    private final String repositorySlug;
    private final String serverId;
    private final boolean isMirror;

    public BitbucketSCMRepository(String credentialsId, String projectName, @Nullable String projectKey,
                                  String repositoryName, @Nullable String repositorySlug, String serverId,
                                  boolean isMirror) {
        this.credentialsId = credentialsId;
        this.projectName = projectName;
        this.projectKey = firstNonNull(projectKey, projectName);
        this.repositoryName = repositoryName;
        this.repositorySlug = firstNonNull(repositorySlug, repositoryName);
        this.serverId = serverId;
        this.isMirror = isMirror;
    }

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

    public String getServerId() {
        return serverId;
    }

    public boolean isMirror() {
        return isMirror;
    }
}
