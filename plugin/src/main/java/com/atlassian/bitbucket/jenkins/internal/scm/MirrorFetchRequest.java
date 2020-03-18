package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;

import javax.annotation.Nullable;

public class MirrorFetchRequest {

    private final String bitbucketServerBaseUrl;
    private final String credentialsId;
    private final GlobalCredentialsProvider globalCredentialsProvider;
    private final String projectNameOrKey;
    private final String repoNameOrSlug;
    private final String existingMirrorSelection;

    public MirrorFetchRequest(String bitbucketServerBaseUrl,
                              @Nullable String credentialsId,
                              GlobalCredentialsProvider globalCredentialsProvider,
                              String projectNameOrKey,
                              String repoNameOrSlug,
                              String existingMirrorSelection) {
        this.bitbucketServerBaseUrl = bitbucketServerBaseUrl;
        this.globalCredentialsProvider = globalCredentialsProvider;
        this.credentialsId = credentialsId;
        this.projectNameOrKey = projectNameOrKey;
        this.repoNameOrSlug = repoNameOrSlug;
        this.existingMirrorSelection = existingMirrorSelection;
    }

    public String getBitbucketServerBaseUrl() {
        return bitbucketServerBaseUrl;
    }

    public String getExistingMirrorSelection() {
        return existingMirrorSelection;
    }

    @Nullable
    public String getCredentialsId() {
        return credentialsId;
    }

    public GlobalCredentialsProvider getGlobalCredentialsProvider() {
        return globalCredentialsProvider;
    }

    public String getProjectNameOrKey() {
        return projectNameOrKey;
    }

    public String getRepoNameOrSlug() {
        return repoNameOrSlug;
    }
}