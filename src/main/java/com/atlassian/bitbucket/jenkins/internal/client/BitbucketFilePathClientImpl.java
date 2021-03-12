package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.*;
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

        BitbucketFilePage firstPage = bitbucketRequestExecutor.makeGetRequest(url, BitbucketFilePage.class).getBody();
        return BitbucketPageStreamUtil.toStream(firstPage, new FileNextPageFetcher(url, bitbucketRequestExecutor))
                .map(page -> ((BitbucketFilePage) page).getLines())
                .flatMap(Collection::stream)
                .collect(Collectors.joining());
    }

    public List<BitbucketSCMFile> getDirectoryContent(BitbucketSCMFile directory) {
        HttpUrl url = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repositorySlug)
                .addPathSegment("browse")
                .addQueryParameter("at", directory.getRef())
                .build();

        BitbucketPage<BitbucketDirectoryChild> firstPage = bitbucketRequestExecutor.makeGetRequest(url, BitbucketDirectory.class)
                .getBody().getChildren();
        return BitbucketPageStreamUtil.toStream(firstPage, new DirectoryNextPageFetcher(url, bitbucketRequestExecutor))
                .map(BitbucketPage::getValues)
                .flatMap(Collection::stream)
                // This gets the first element in the component of a child path, which is the immediate directory name
                .map(child -> new BitbucketSCMFile(directory, child.getPath().getComponents().get(0)))
                .collect(Collectors.toList());
    }

    static class DirectoryNextPageFetcher implements NextPageFetcher<BitbucketDirectoryChild> {

        private final HttpUrl url;
        private final BitbucketRequestExecutor bitbucketRequestExecutor;

        DirectoryNextPageFetcher(HttpUrl url,
                            BitbucketRequestExecutor bitbucketRequestExecutor) {
            this.url = url;
            this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        }

        @Override
        public BitbucketPage<BitbucketDirectoryChild> next(BitbucketPage<BitbucketDirectoryChild> previous) {
            if (previous.isLastPage()) {
                throw new IllegalArgumentException("Last page does not have next page");
            }
            return bitbucketRequestExecutor.makeGetRequest(
                    nextPageUrl(previous),
                    new TypeReference<BitbucketPage<BitbucketDirectoryChild>>() {}).getBody();
        }

        private HttpUrl nextPageUrl(BitbucketPage<BitbucketDirectoryChild> previous) {
            return url.newBuilder().addQueryParameter("start", valueOf(previous.getNextPageStart())).build();
        }
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
