package com.atlassian.bitbucket.jenkins.internal.scm;

import javax.annotation.Nullable;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class BitbucketSCMRepository {

    private final String credentialsId;
    private final String sshCredentialsId;
    private final String projectKey;
    private final String projectName;
    private final String repositoryName;
    private final String repositorySlug;
    private final String serverId;
    private final String mirrorName;

    public BitbucketSCMRepository(@Nullable String credentialsId, @Nullable String sshCredentialsId, String projectName, String projectKey,
                                  String repositoryName, String repositorySlug, @Nullable String serverId,
                                  String mirrorName) {
        this.credentialsId = credentialsId;
        this.sshCredentialsId = sshCredentialsId;
        this.projectName = projectName;
        this.projectKey = projectKey;
        this.repositoryName = repositoryName;
        this.repositorySlug = repositorySlug;
        this.serverId = serverId;
        this.mirrorName = mirrorName;
    }

    @Nullable
    public String getCredentialsId() {
        return credentialsId;
    }

    @Nullable
    public String getSshCredentialsId() {
        return sshCredentialsId;
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

    public String getMirrorName() {
        return mirrorName;
    }

    public boolean isMirrorConfigured() {
        return !isEmpty(mirrorName);
    }

    public boolean isPrivate() {
        return projectKey.startsWith("~");
    }
}
