package com.atlassian.bitbucket.jenkins.internal.scm;

import static java.util.Objects.requireNonNull;

public class MirrorRequest {

    private final String serverId;
    private final String jobCredentials;
    private final BitbucketRepoDetail bitbucketRepoDetail;

    public MirrorRequest(String serverId,
                         String jobCredentials,
                         BitbucketRepoDetail bitbucketRepoDetail) {
        this.serverId = requireNonNull(serverId);
        this.jobCredentials = requireNonNull(jobCredentials);
        this.bitbucketRepoDetail = requireNonNull(bitbucketRepoDetail);
    }

    public BitbucketRepoDetail getBitbucketRepoDetail() {
        return bitbucketRepoDetail;
    }

    public String getJobCredentials() {
        return jobCredentials;
    }

    public String getServerId() {
        return serverId;
    }

    public static class BitbucketRepoDetail {

        private final String projectKey;
        private final String repoSlug;

        public BitbucketRepoDetail(String projectKey, String repoSlug) {
            this.projectKey = requireNonNull(projectKey);
            this.repoSlug = requireNonNull(repoSlug);
        }

        public String getProjectKey() {
            return projectKey;
        }

        public String getRepoSlug() {
            return repoSlug;
        }
    }
}
