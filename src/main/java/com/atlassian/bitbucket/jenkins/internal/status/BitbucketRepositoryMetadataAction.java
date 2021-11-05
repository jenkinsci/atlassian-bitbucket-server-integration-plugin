package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import hudson.model.InvisibleAction;

public class BitbucketRepositoryMetadataAction extends InvisibleAction {

    private final BitbucketSCMRepository repository;
    private final BitbucketDefaultBranch defaultBranch;

    public BitbucketRepositoryMetadataAction(BitbucketSCMRepository repository, BitbucketDefaultBranch defaultBranch) {
        this.repository = repository;
        this.defaultBranch = defaultBranch;
    }

    public BitbucketSCMRepository getBitbucketSCMRepository() {
        return repository;
    }

    public BitbucketDefaultBranch getBitbucketDefaultBranch() {
        return defaultBranch;
    }
}
