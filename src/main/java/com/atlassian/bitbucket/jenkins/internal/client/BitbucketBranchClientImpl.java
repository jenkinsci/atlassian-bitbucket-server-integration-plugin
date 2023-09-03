package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.atlassian.bitbucket.jenkins.internal.util.SystemPropertiesConstants.REMOTE_BRANCHES_RETRIEVAL_MAX_PAGES;
import static com.atlassian.bitbucket.jenkins.internal.util.SystemPropertiesConstants.REMOTE_BRANCHES_RETRIEVAL_PAGE_SIZE;
import static java.lang.String.valueOf;

public class BitbucketBranchClientImpl implements BitbucketBranchClient {

    private static final int MAX_PAGES = Integer.getInteger(REMOTE_BRANCHES_RETRIEVAL_MAX_PAGES, 5);
    private static final int PAGE_SIZE = Integer.getInteger(REMOTE_BRANCHES_RETRIEVAL_PAGE_SIZE, 1000);

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String projectKey;
    private final String repositorySlug;

    public BitbucketBranchClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor,
                                     String projectKey,
                                     String repositorySlug) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        this.projectKey = projectKey;
        this.repositorySlug = repositorySlug;
    }

    @Override
    public Stream<BitbucketDefaultBranch> getRemoteBranches() {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repositorySlug)
                .addPathSegment("branches")
                .addQueryParameter("limit", String.valueOf(PAGE_SIZE))
                .addQueryParameter("orderBy", "modification"); // the most recently-modified branches first

        HttpUrl url = urlBuilder.build();
        BitbucketPage<BitbucketDefaultBranch> firstPage =
                bitbucketRequestExecutor.makeGetRequest(url,
                        new TypeReference<BitbucketPage<BitbucketDefaultBranch>>() {
                }).getBody();
        return BitbucketPageStreamUtil.toStream(firstPage, new NextPageFetcherImpl(url, bitbucketRequestExecutor,
                        MAX_PAGES))
                .map(BitbucketPage::getValues)
                .flatMap(Collection::stream);
    }

    static class NextPageFetcherImpl implements NextPageFetcher<BitbucketDefaultBranch> {

        private final BitbucketRequestExecutor bitbucketRequestExecutor;
        private final AtomicInteger currentPage = new AtomicInteger();
        private final int maxPages;
        private final HttpUrl url;

        NextPageFetcherImpl(HttpUrl url,
                            BitbucketRequestExecutor bitbucketRequestExecutor,
                            int maxPages) {
            this.url = url;
            this.bitbucketRequestExecutor = bitbucketRequestExecutor;
            this.maxPages = maxPages;
        }

        @Override
        public BitbucketPage<BitbucketDefaultBranch> next(BitbucketPage<BitbucketDefaultBranch> previous) {
            if (previous.isLastPage()) {
                throw new IllegalArgumentException("Last page does not have next page");
            }

            if (currentPage.incrementAndGet() >= maxPages) {
                // We've reached the maximum number of pages, so we return an "empty last page" to stop the iterator.
                BitbucketPage<BitbucketDefaultBranch> lastPage = new BitbucketPage<>();
                lastPage.setValues(Collections.emptyList());
                lastPage.setLastPage(true);
                return lastPage;
            }

            return bitbucketRequestExecutor.makeGetRequest(
                    nextPageUrl(previous),
                    new TypeReference<BitbucketPage<BitbucketDefaultBranch>>() {
                    }).getBody();
        }

        private HttpUrl nextPageUrl(BitbucketPage<BitbucketDefaultBranch> previous) {
            return url.newBuilder().addQueryParameter("start", valueOf(previous.getNextPageStart())).build();
        }
    }
}
