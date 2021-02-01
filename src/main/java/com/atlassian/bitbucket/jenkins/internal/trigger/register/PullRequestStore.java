package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.google.inject.ImplementedBy;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * local copy of all open pull requests to support selectBranchTrait when we only want to build/display branches with
 * open pull requests
 * @Since 2.1.2
 */

@ImplementedBy(PullRequestStoreImpl.class)
public interface PullRequestStore {

    void addPullRequest(String serverId, BitbucketPullRequest pullRequest);

    void removePullRequest(String serverId, BitbucketPullRequest pullRequest);

    /**
     * figures out if this store contains a given branch (if it does, this means the branch has open pull requests)
     * @param branchName
     * @param repository
     * @return boolean on if provided branch has open pull requests or not
     */
    boolean hasOpenPullRequests(String branchName, BitbucketSCMRepository repository);

    /**
     * retrieves a pull request given ids and keys
     * @param projectKey
     * @param slug
     * @param serverId
     * @param fromBranch
     * @param toBranch
     * @return desired pull request else Optional.empty()
     */
    public Optional<MinimalPullRequest> getPullRequest(String projectKey, String slug, String serverId, String fromBranch, String toBranch);

    /**
     * Given a list of pull requests retrieved from a call to bbs, update and sync up our pullRequestStore.
     * @param projectKey
     * @param slug
     * @param serverId
     * @param bbsPullRequests
     */
    void refreshStore(String projectKey, String slug, String serverId, Stream<BitbucketPullRequest> bbsPullRequests);

    void deleteClosedPullRequests(long date);
}
