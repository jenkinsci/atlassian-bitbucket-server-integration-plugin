package com.atlassian.bitbucket.jenkins.internal.scm;

public class MirrorFetchRequest {

    private final String serverId;
    private final String jobCredentials;
    private final BitbucketRepo bitbucketRepo;
    private final String existingMirrorSelection;

    public MirrorFetchRequest(String serverId,
                              String jobCredentials,
                              BitbucketRepo bitbucketRepo,
                              String existingMirrorSelection) {
        this.serverId = serverId;
        this.jobCredentials = jobCredentials;
        this.bitbucketRepo = bitbucketRepo;
        this.existingMirrorSelection = existingMirrorSelection;
    }

    public BitbucketRepo getBitbucketRepo() {
        return bitbucketRepo;
    }

    public String getExistingMirrorSelection() {
        return existingMirrorSelection;
    }

    public String getJobCredentials() {
        return jobCredentials;
    }

    public String getServerId() {
        return serverId;
    }
}