package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCommit;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

public class BitbucketCommitClientImpl implements BitbucketCommitClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String projectKey;
    private final String repositorySlug;

    public BitbucketCommitClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor,
                                     String projectKey,
                                     String repositorySlug) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        this.projectKey = projectKey;
        this.repositorySlug = repositorySlug;
    }

    @Override
    @Nullable
    public BitbucketCommit getCommit(String identifier) {
        HttpUrl url = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repositorySlug)
                .addPathSegment("commits")
                .addQueryParameter("until", identifier)
                .addQueryParameter("start", "0")
                .addQueryParameter("limit", "1")
                .build();

        BitbucketPage<BitbucketCommit> firstPage =
                bitbucketRequestExecutor.makeGetRequest(url,
                        new TypeReference<BitbucketPage<BitbucketCommit>>() {
                        }).getBody();

        return BitbucketPageStreamUtil.toStream(firstPage, new BitbucketCommitClientImpl.OnlyPageFetcherImpl())
                .map(BitbucketPage::getValues)
                .flatMap(Collection::stream)
                .findFirst().orElse(null);
    }

    static class OnlyPageFetcherImpl implements NextPageFetcher<BitbucketCommit> {

        @Override
        public BitbucketPage<BitbucketCommit> next(BitbucketPage<BitbucketCommit> previous) {
            if (previous.isLastPage()) {
                throw new IllegalArgumentException("Last page does not have next page");
            }

            BitbucketPage<BitbucketCommit> lastPage = new BitbucketPage<>();
            lastPage.setValues(Collections.emptyList());
            lastPage.setLastPage(true);
            return lastPage;
        }
    }
}
