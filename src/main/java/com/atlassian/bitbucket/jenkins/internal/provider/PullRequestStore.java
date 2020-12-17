package com.atlassian.bitbucket.jenkins.internal.provider;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.google.inject.ImplementedBy;

@ImplementedBy(PullRequestStoreImpl.class)
public interface PullRequestStore {

    boolean hasOpenPullRequests (String branchName, BitbucketSCMRepository repository);

    void addPullRequest (String serverId, String repository, String project, BitbucketPullRequest pullRequest);
}
