package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;

import java.util.stream.Stream;

public interface BitbucketBranchClient {

    /**
     * Hits the bitbucket 'find branches' REST endpoint.
     * Retrieve the branches matching the supplied filterText param.
     * Default filterTest is empty. Which gets all branches.
     * The authenticated user must have REPO_READ permission for the specified repository to call this resource.
     * @return
     */
    Stream<BitbucketDefaultBranch> getRemoteBranches();
}
