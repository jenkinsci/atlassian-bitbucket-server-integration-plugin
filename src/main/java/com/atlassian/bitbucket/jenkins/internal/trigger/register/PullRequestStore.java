package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.google.inject.ImplementedBy;

import java.util.Optional;

/**
 * local copy of all open pull requests to support selectBranchTrait when we only want to build/display branches with
 * open pull requests
 * @Since 2.1.2
 */

@ImplementedBy(PullRequestStoreImpl.class)
public interface PullRequestStore {

    void addPullRequest(String serverId, BitbucketPullRequest pullRequest);

    void removePullRequest(String serverId, BitbucketPullRequest pullRequest);

    boolean hasOpenPullRequests(String branchName, BitbucketSCMRepository repository);

    Optional<BitbucketPullRequest> getPullRequest(String key, String slug, String serverId, int pullRequestId);
}
