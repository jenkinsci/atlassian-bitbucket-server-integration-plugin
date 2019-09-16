package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;

public interface BitbucketSearchClient {

    /**
     * Search for Bitbucket Server projects whose names contain the provided value. Matching is
     * performed in a case-insensitive manner, and will match anywhere within the projects' names.
     *
     * <p>Note: Values containing only whitespace are <i>ignored</i>, and will not be applied.
     * Additionally, leading and trailing whitespace are trimmed. A filter that is empty will result
     * in the first page of all (accessible) projects being returned
     *
     * @return a page of projects matching the query
     */
    BitbucketPage<BitbucketProject> findProjects();

    /**
     * Search for Bitbucket Server repositories whose names match the provided value. Matching is
     * done with Elasticsearch so the filter will go through some tokenization before attempting to
     * match tokenized repository names. Repositories will only be searched for within the provided
     * project.
     *
     * <p>Note: Values containing only whitespace are <i>ignored</i>, and will not be applied.
     * Additionally, leading and trailing whitespace are trimmed. A filter that is empty will result
     * in all (accessible) repositories being returned
     *
     * @param repositoryName the terms to use when searching for repositories
     * @return a page of repositories matching the filter
     */
    BitbucketPage<BitbucketRepository> findRepositories(String repositoryName);
}
