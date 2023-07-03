package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Adds the pull request origin branch to the underlying {@link GitSCM} environment variables.
 *
 * @since 4.0.0
 */
public class BitbucketPullRequestSourceBranch extends GitSCMExtension {

    public static final String PULL_REQUEST_SOURCE_BRANCH = "PULL_REQUEST_SOURCE_BRANCH";

    private final MinimalPullRequest pullRequest;

    public BitbucketPullRequestSourceBranch(MinimalPullRequest pullRequest) {
        this.pullRequest = requireNonNull(pullRequest, "pullRequest");
    }

    @Override
    public void populateEnvironmentVariables(GitSCM scm, Map<String, String> env) {
        env.put(PULL_REQUEST_SOURCE_BRANCH, pullRequest.getFromRefDisplayId());
    }
}
