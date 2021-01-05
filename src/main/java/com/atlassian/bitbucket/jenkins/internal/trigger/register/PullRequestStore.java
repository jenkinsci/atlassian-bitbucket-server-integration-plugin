package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.google.inject.ImplementedBy;

import java.util.concurrent.ConcurrentMap;

@ImplementedBy(PullRequestStoreImpl.class)
public interface PullRequestStore {

    void addPullRequest(String serverId, BitbucketPullRequest pullRequest);

    boolean hasOpenPullRequests(String branchName, BitbucketSCMRepository repository);

    ConcurrentMap<?, ?> getPullRequests();
}
