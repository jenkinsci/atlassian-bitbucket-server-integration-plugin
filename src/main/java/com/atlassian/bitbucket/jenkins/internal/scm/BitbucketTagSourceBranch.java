package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public class BitbucketTagSourceBranch extends GitSCMExtension {

    public static final String TAG_SOURCE_COMMIT = "TAG_SOURCE_COMMIT";

    private final BitbucketTagSCMHead tag;

    public BitbucketTagSourceBranch(BitbucketTagSCMHead tag) {
        this.tag = requireNonNull(tag, "tag");
    }

    @Override
    public void populateEnvironmentVariables(GitSCM scm, Map<String, String> env) {
        env.put(TAG_SOURCE_COMMIT, tag.getFullRef());
    }
}
