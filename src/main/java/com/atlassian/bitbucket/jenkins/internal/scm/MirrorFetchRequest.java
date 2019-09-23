package com.atlassian.bitbucket.jenkins.internal.scm;

class MirrorFetchRequest {

    private final String serverId;
    private final String jobCredentials;
    private final String projectNameOrKey;
    private final String repoNameOrSlug;
    private final String existingMirrorSelection;

    MirrorFetchRequest(String serverId,
                       String jobCredentials,
                       String projectNameOrKey,
                       String repoNameOrSlug,
                       String existingMirrorSelection) {
        this.serverId = serverId;
        this.jobCredentials = jobCredentials;
        this.projectNameOrKey = projectNameOrKey;
        this.repoNameOrSlug = repoNameOrSlug;
        this.existingMirrorSelection = existingMirrorSelection;
    }

    public String getExistingMirrorSelection() {
        return existingMirrorSelection;
    }

    public String getJobCredentials() {
        return jobCredentials;
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