package com.atlassian.bitbucket.jenkins.internal.scm;

import javax.annotation.Nullable;

public class MirrorFetchRequest {

    private final String serverId;
    private final String credentialsId;
    private final String projectNameOrKey;
    private final String repoNameOrSlug;
    private final String existingMirrorSelection;

    public MirrorFetchRequest(String serverId,
                       @Nullable String credentialsId,
                       String projectNameOrKey,
                       String repoNameOrSlug,
                       String existingMirrorSelection) {
        this.serverId = serverId;
        this.credentialsId = credentialsId;
        this.projectNameOrKey = projectNameOrKey;
        this.repoNameOrSlug = repoNameOrSlug;
        this.existingMirrorSelection = existingMirrorSelection;
    }

    public String getExistingMirrorSelection() {
        return existingMirrorSelection;
    }

    @Nullable
    public String getCredentialsId() {
        return credentialsId;
    }

    public String getProjectNameOrKey() {
        return projectNameOrKey;
    }

    public String getRepoNameOrSlug() {
        return repoNameOrSlug;
    }

    public String getServerId() {
        return serverId;
    }
}