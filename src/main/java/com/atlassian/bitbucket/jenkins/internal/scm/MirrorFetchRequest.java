package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.Item;

import javax.annotation.CheckForNull;

public class MirrorFetchRequest {

    private final String bitbucketServerBaseUrl;
    private final Item context;
    private final String credentialsId;
    private final String existingMirrorSelection;
    private final String projectNameOrKey;
    private final String repoNameOrSlug;
    public MirrorFetchRequest(String bitbucketServerBaseUrl,
                              @CheckForNull Item context,
                              @CheckForNull String credentialsId,
                              String projectNameOrKey,
                              String repoNameOrSlug,
                              String existingMirrorSelection) {
        this.bitbucketServerBaseUrl = bitbucketServerBaseUrl;
        this.context = context;
        this.credentialsId = credentialsId;
        this.projectNameOrKey = projectNameOrKey;
        this.repoNameOrSlug = repoNameOrSlug;
        this.existingMirrorSelection = existingMirrorSelection;
    }

    public String getBitbucketServerBaseUrl() {
        return bitbucketServerBaseUrl;
    }

    @CheckForNull
    public Item getContext() {
        return context;
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    public String getExistingMirrorSelection() {
        return existingMirrorSelection;
    }

    public String getProjectNameOrKey() {
        return projectNameOrKey;
    }

    public String getRepoNameOrSlug() {
        return repoNameOrSlug;
    }
}
