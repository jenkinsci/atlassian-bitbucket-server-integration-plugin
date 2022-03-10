package com.atlassian.bitbucket.jenkins.internal.scm;

import javax.annotation.CheckForNull;

import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class BitbucketSCMRepository {

    private final String credentialsId;
    private final String mirrorName;
    private final String projectKey;
    private final String projectName;
    private final String repositoryName;
    private final String repositorySlug;
    private final String serverId;
    private final String sshCredentialsId;

    public BitbucketSCMRepository(@CheckForNull String credentialsId, @CheckForNull String sshCredentialsId,
                                  @CheckForNull String projectName, @CheckForNull String projectKey,
                                  @CheckForNull String repositoryName, @CheckForNull String repositorySlug,
                                  @CheckForNull String serverId,
                                  @CheckForNull String mirrorName) {
        this.credentialsId = credentialsId;
        this.sshCredentialsId = sshCredentialsId;
        this.projectName = Objects.toString(projectName, "");
        this.projectKey = Objects.toString(projectKey, "");
        this.repositoryName = Objects.toString(repositoryName, "");
        this.repositorySlug = Objects.toString(repositorySlug, "");
        this.serverId = serverId;
        this.mirrorName = Objects.toString(mirrorName, "");
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    public String getMirrorName() {
        return mirrorName;
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

    @CheckForNull
    public String getServerId() {
        return serverId;
    }

    @CheckForNull
    public String getSshCredentialsId() {
        return sshCredentialsId;
    }

    public boolean isMirrorConfigured() {
        return !isBlank(mirrorName);
    }

    public boolean isPersonal() {
        return projectKey.startsWith("~");
    }

    public boolean isValid() {
        return isNotBlank(projectKey) && 
               isNotBlank(projectName) &&
               isNotBlank(repositoryName) && 
               isNotBlank(repositorySlug) && 
               isNotBlank(serverId);
    }

    // Returns the protocol used to perform fetch and retrieve operations. If no SSH credentials are provided, cloning is done by HTTP
    public CloneProtocol getCloneProtocol() {
        return isBlank(sshCredentialsId) ? CloneProtocol.HTTP : CloneProtocol.SSH;
    }

    public String getCloneCredentialsId() {
        return isBlank(sshCredentialsId) ? credentialsId : sshCredentialsId;
    }
}
