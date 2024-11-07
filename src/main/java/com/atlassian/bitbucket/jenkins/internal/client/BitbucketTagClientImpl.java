package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import hudson.model.TaskListener;
import okhttp3.HttpUrl;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.atlassian.bitbucket.jenkins.internal.util.SystemPropertiesConstants.REMOTE_TAGS_RETRIEVAL_MAX_PAGES;
import static com.atlassian.bitbucket.jenkins.internal.util.SystemPropertiesConstants.REMOTE_TAGS_RETRIEVAL_PAGE_SIZE;
import static java.lang.String.valueOf;

public class BitbucketTagClientImpl implements BitbucketTagClient {

    private static final int MAX_PAGES = Integer.getInteger(REMOTE_TAGS_RETRIEVAL_MAX_PAGES, 5);
    private static final int PAGE_SIZE = Integer.getInteger(REMOTE_TAGS_RETRIEVAL_PAGE_SIZE, 1000);

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String projectKey;
    private final String repositorySlug;
    private final TaskListener taskListener;

    public BitbucketTagClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor,
                                  String projectKey,
                                  String repositorySlug,
                                  TaskListener taskListener) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        this.projectKey = projectKey;
        this.repositorySlug = repositorySlug;
        this.taskListener = taskListener;
    }

    @Override
    public Stream<BitbucketTag> getRemoteTags() {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repositorySlug)
                .addPathSegment("tags")
                .addQueryParameter("limit", String.valueOf(PAGE_SIZE))
                .addQueryParameter("orderBy", "modification"); // the most recently-modified tags first

        HttpUrl url = urlBuilder.build();
        BitbucketPage<BitbucketTag> firstPage =
                bitbucketRequestExecutor.makeGetRequest(url,
                        new TypeReference<BitbucketPage<BitbucketTag>>() {
                        }).getBody();

        return BitbucketPageStreamUtil.toStream(firstPage, new BitbucketTagClientImpl.NextPageFetcherImpl(url, bitbucketRequestExecutor,
                        MAX_PAGES, taskListener))
                .map(BitbucketPage::getValues)
                .flatMap(Collection::stream);
    }

    public BitbucketTag getRemoteTag(String tagName) {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                    .addPathSegment("projects")
                    .addPathSegment(projectKey)
                    .addPathSegment("repos")
                    .addPathSegment(repositorySlug)
                    .addPathSegment("commits")
                    .addQueryParameter("until", tagName)
                    .addQueryParameter("start", "0")
                    .addQueryParameter("limit", "1");


        HttpUrl url = urlBuilder.build();
        BitbucketCommit commits =  bitbucketRequestExecutor.makeGetRequest(url, BitbucketCommit.class).getBody();
        return new BitbucketTag(commits.getId(), commits.getDisplayId(), commits.getId());
    }

    static class NextPageFetcherImpl implements NextPageFetcher<BitbucketTag> {

        private final BitbucketRequestExecutor bitbucketRequestExecutor;
        private final AtomicInteger currentPage = new AtomicInteger();
        private final int maxPages;
        private final TaskListener taskListener;
        private final HttpUrl url;

        NextPageFetcherImpl(HttpUrl url,
                            BitbucketRequestExecutor bitbucketRequestExecutor,
                            int maxPages,
                            TaskListener taskListener) {
            this.url = url;
            this.bitbucketRequestExecutor = bitbucketRequestExecutor;
            this.maxPages = maxPages;
            this.taskListener = taskListener;
        }

        @Override
        public BitbucketPage<BitbucketTag> next(BitbucketPage<BitbucketTag> previous) {
            if (previous.isLastPage()) {
                throw new IllegalArgumentException("Last page does not have next page");
            }

            if (currentPage.incrementAndGet() >= maxPages) {
                // We've reached the maximum number of pages, so we return an "empty last page" to stop the iterator.
                taskListener.getLogger().println("Max number of pages for tag retrieval reached.");
                BitbucketPage<BitbucketTag> lastPage = new BitbucketPage<>();
                lastPage.setValues(Collections.emptyList());
                lastPage.setLastPage(true);
                return lastPage;
            }

            return bitbucketRequestExecutor.makeGetRequest(
                    nextPageUrl(previous),
                    new TypeReference<BitbucketPage<BitbucketTag>>() {
                    }).getBody();
        }

        private HttpUrl nextPageUrl(BitbucketPage<BitbucketTag> previous) {
            return url.newBuilder().addQueryParameter("start", valueOf(previous.getNextPageStart())).build();
        }
    }
}
