package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;

import javax.annotation.CheckForNull;

public interface BitbucketProjectSearchClient
        extends BitbucketClient<BitbucketPage<BitbucketProject>> {

    /**
     * Search for Bitbucket Server projects whose names contain the provided value. Matching is
     * performed in a case-insensitive manner, and will match anywhere within the projects' names.
     *
     * <p>Note: Values containing only whitespace are <i>ignored</i>, and will not be applied.
     * Additionally, leading and trailing whitespace are trimmed. A filter that is empty will result
     * in the first page of all (accessible) projects being returned
     *
     * @param name the query on which to search for projects
     * @return a page of projects matching the query
     */
    BitbucketPage<BitbucketProject> get(@CheckForNull String name);
}
