package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketFilePage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketResponse;
import com.atlassian.bitbucket.jenkins.internal.scm.filesystem.BitbucketSCMFile;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.valueOf;

public class BitbucketFilePathClientImpl implements BitbucketFilePathClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String projectKey;
    private final String repositorySlug;

    public BitbucketFilePathClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor,
                                       String projectKey,
                                       String repositorySlug) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        this.projectKey = projectKey;
        this.repositorySlug = repositorySlug;
    }

    public String getFileContent(BitbucketSCMFile file) {
        HttpUrl url = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repositorySlug)
                .addPathSegment("browse")
                .addQueryParameter("at", file.getRef())
                .build();

        BitbucketResponse<BitbucketFilePage> firstPage = bitbucketRequestExecutor.makeGetRequest(url, BitbucketFilePage.class);
        return BitbucketPageStreamUtil.toStream(firstPage.getBody(), new FileNextPageFetcher(url, bitbucketRequestExecutor))
                .map(page -> ((BitbucketFilePage) page).getLines())
                .flatMap(Collection::stream)
                .collect(Collectors.joining());
    }

    static class FileNextPageFetcher implements NextPageFetcher<String> {

        private final HttpUrl url;
        private final BitbucketRequestExecutor bitbucketRequestExecutor;

        FileNextPageFetcher(HttpUrl url,
                            BitbucketRequestExecutor bitbucketRequestExecutor) {
            this.url = url;
            this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        }

        @Override
        public BitbucketPage<String> next(BitbucketPage<String> previous) {
            if (previous.isLastPage()) {
                throw new IllegalArgumentException("Last page does not have next page");
            }
            return bitbucketRequestExecutor.makeGetRequest(
                    nextPageUrl(previous),
                    new TypeReference<BitbucketPage<String>>() {}).getBody();
        }

        private HttpUrl nextPageUrl(BitbucketPage<String> previous) {
            return url.newBuilder().addQueryParameter("start", valueOf(previous.getNextPageStart())).build();
        }
    }
}
