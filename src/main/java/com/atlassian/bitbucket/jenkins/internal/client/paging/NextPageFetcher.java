package com.atlassian.bitbucket.jenkins.internal.client.paging;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;

public interface NextPageFetcher {

    <T> BitbucketPage<T> next(BitbucketPage<T> previous);
}