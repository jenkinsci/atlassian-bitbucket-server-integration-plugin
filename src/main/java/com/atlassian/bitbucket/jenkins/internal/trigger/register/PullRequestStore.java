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

    /**
     * Changes how long before we first run removeClosedPullRequests. Default is 12 hours.
     * @param newDelay
     */
    void setDelay(Long newDelay);

    /**
     * Changes the gap between successive calls to removeClosedPullRequests. Default is 12 hours.
     * @param newPeriod
     */
    void setPeriod(Long newPeriod);


    /**
     * When a new (not outdated) pull request enters, it gets added to the store or the state is updated.
     * Handles both open and closing of pull requests.
     * @param serverId
     * @param pullRequest
     */
    void updatePullRequest(String serverId, BitbucketPullRequest pullRequest);

    /**
     * In the case that Jenkins misses a pull request deleted webhook, the pr no longer exists in bbs and so fetching
     * from bbs will not return it (hence the pr in our store is not updated to closed).
     * This method provides a REST endpoint for a customer to updated a pr in store to close in such case.
     * @param projectKey
     * @param slug
     * @param serverId
     * @param fromBranch
     * @param toBranch
     */
    void closePullRequest(String projectKey, String slug, String serverId, String fromBranch, String toBranch);

    /**
     * Figures out if this store contains a given branch (if it does, this means the branch has open pull requests).
     * @param branchName
     * @param repository
     * @return boolean on if provided branch has open pull requests or not
     */
    boolean hasOpenPullRequests(String branchName, BitbucketSCMRepository repository);

    /**
     * Retrieves a pull request given ids and keys.
     * @param projectKey
     * @param slug
     * @param serverId
     * @param fromBranch
     * @param toBranch
     * @return desired pull request else Optional.empty()
     */
    Optional<MinimalPullRequest> getPullRequest(String projectKey, String slug, String serverId, String fromBranch, String toBranch);

    /**
     * Given a list of pull requests retrieved from a call to bbs, update and sync up our pullRequestStore.
     * @param projectKey
     * @param slug
     * @param serverId
     * @param bbsPullRequests
     */
    void refreshStore(String projectKey, String slug, String serverId, Stream<BitbucketPullRequest> bbsPullRequests);

    /**
     * Clear out closed pull requests that are older than a certain date to minimise the size of our store.
     * @param date
     */
    void removeClosedPullRequests(long date);

    /**
     * Determines if a repoStore in our prStore contains any pull requests.
     * Helps when Jenkins starts up, and our store has no prs - we don't want to fetch all prs as it is too time-consuming
     * and we only fetch open prs instead.
     * @param projectKey
     * @param slug
     * @param serverId
     * @return
     */
    boolean hasPRForRepository(String projectKey, String slug, String serverId);
}
