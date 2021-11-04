package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import hudson.model.Action;
import hudson.model.InvisibleAction;

import java.io.Serializable;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class BitbucketRepositoryMetadataAction extends InvisibleAction {

    private final BitbucketSCMRepository repository;
    private final BitbucketDefaultBranch defaultBranch;

    public BitbucketRepositoryMetadataAction(BitbucketSCMRepository repository, @Nullable BitbucketDefaultBranch defaultBranch) {
        this.repository = repository;
        this.defaultBranch = defaultBranch;
    }

    public BitbucketSCMRepository getBitbucketSCMRepository() {
        return repository;
    }

    @Nullable
    public BitbucketDefaultBranch getBitbucketDefaultBranch() {
        return defaultBranch;
    }
}
