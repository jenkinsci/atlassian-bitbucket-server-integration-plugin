package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullState;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.google.inject.ImplementedBy;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * local copy of all open pull requests to support selectBranchTrait when we only want to build/display branches with
 * open pull requests
 * @Since 2.1.3
 */

@ImplementedBy(PullRequestStoreImpl.class)
public interface PullRequestStore {

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
     * REST layer can call upon this method for a customer to update a pr in store to close in such case.
     * @param projectKey
     * @param slug
     * @param serverId
     * @param fromBranch
     * @param toBranch
     */
    void setState(String projectKey, String slug, String serverId, String fromBranch, String toBranch, BitbucketPullState state);

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
     * Clear out closed pull requests that are older than the given date.
     * @param date
     */
    void removeClosedPullRequests(long date);

    /**
     * Determines if the store contains any pull requests for the given repository.
     * @param projectKey
     * @param slug
     * @param serverId
     * @return
     */
    boolean hasPullRequestForRepository(String projectKey, String slug, String serverId);
}
