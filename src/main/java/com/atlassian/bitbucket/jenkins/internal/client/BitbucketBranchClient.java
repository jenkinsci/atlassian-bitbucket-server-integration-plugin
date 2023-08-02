package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;

import java.util.stream.Stream;

public interface BitbucketBranchClient {

    /**
     * Gets all branches from the given repository.
     * The returned stream will make paged calls to Bitbucket to ensure that all branches are returned.
     * Consumers are advised that this can return large amounts of data and are <strong>strongly</strong> encouraged to
     * not collect to a list or similar before processing items, but rather process them as they come in.
     *
     * @return Stream of bitbucket branches
     */
    Stream<BitbucketDefaultBranch> getRemoteBranches();
}
