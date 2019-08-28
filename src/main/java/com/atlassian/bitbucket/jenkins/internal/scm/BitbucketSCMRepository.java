package com.atlassian.bitbucket.jenkins.internal.scm;

import javax.annotation.Nullable;

public class BitbucketSCMRepository {

    private final String credentialsId;
    private final String projectKey;
    private final String repositorySlug;
    private final String serverId;
    private final String mirrorName;

    public BitbucketSCMRepository(String credentialsId, String projectKey,
                                  String repositorySlug, String serverId, String mirrorName) {
        this.credentialsId = credentialsId;
        this.projectKey = projectKey;
        this.repositorySlug = repositorySlug;
        this.serverId = serverId;
        this.mirrorName = mirrorName;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getRepositorySlug() {
        return repositorySlug;
    }

    public String getServerId() {
        return serverId;
    }

    @Nullable
    public String getMirrorName() {
        return mirrorName;
    }
}
