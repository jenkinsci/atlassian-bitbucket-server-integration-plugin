package com.atlassian.bitbucket.jenkins.internal.client.paging;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;

/**
 * In order to support multiple paging, implementation should provide a way to fetch next page based on previous page.
 *
 * @param <T>
 */
public interface NextPageFetcher<T> {

    /**
     * Returns the next page based on a page.
     *
     * @param previous the previous page
     * @return the next page.
     */
    BitbucketPage<T> next(BitbucketPage<T> previous);
}