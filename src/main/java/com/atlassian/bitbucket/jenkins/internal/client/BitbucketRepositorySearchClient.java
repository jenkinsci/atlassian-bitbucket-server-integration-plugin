package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;

public interface BitbucketRepositorySearchClient
        extends BitbucketClient<BitbucketPage<BitbucketRepository>> {

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
     * @param filter the terms to use when searching for repositories
     * @return a page of repositories matching the filter
     */
    BitbucketPage<BitbucketRepository> get(String filter);
}
