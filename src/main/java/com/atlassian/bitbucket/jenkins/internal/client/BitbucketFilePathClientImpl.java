package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.scm.filesystem.BitbucketSCMFile;
import com.fasterxml.jackson.core.type.TypeReference;
import jenkins.scm.api.SCMFile;
import okhttp3.HttpUrl;
import jenkins.scm.api.SCMFile.Type;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.valueOf;
import static jenkins.scm.api.SCMFile.Type.*;

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

    @Override
    public List<SCMFile> getDirectoryContent(BitbucketSCMFile directory) {
        HttpUrl url = getUrl(directory);

        BitbucketPage<BitbucketDirectoryChild> firstPage =
                bitbucketRequestExecutor.makeGetRequest(url, BitbucketDirectory.class)
                        .getBody().getChildren();
        return BitbucketPageStreamUtil.toStream(firstPage, new DirectoryNextPageFetcher(url, bitbucketRequestExecutor))
                .map(BitbucketPage::getValues)
                .flatMap(Collection::stream)
                // This gets the first element in the component of a child path, which is the immediate directory name
                .map(child -> {
                    Type type = "FILE".equals(child.getType()) ? REGULAR_FILE : DIRECTORY;
                    return new BitbucketSCMFile(directory, child.getPath().getComponents().get(0), type);
                })
                .collect(Collectors.toList());
    }

    @Override
    public InputStream getRawFileStream(BitbucketSCMFile scmFile) {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repositorySlug)
                .addPathSegment("raw")
                .addPathSegment(scmFile.getFilePath());
        scmFile.getRef().map(ref -> urlBuilder.addQueryParameter("at", ref));
        HttpUrl url = urlBuilder.build();

        return bitbucketRequestExecutor.makeStreamingGetRequest(url);
    }
    private HttpUrl getUrl(BitbucketSCMFile scmFile) {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repositorySlug)
                .addPathSegment("browse")
                .addPathSegments(scmFile.getFilePath());
        scmFile.getRef().map(ref -> urlBuilder.addQueryParameter("at", ref));

        return urlBuilder.build();
    }

    static class DirectoryNextPageFetcher implements NextPageFetcher<BitbucketDirectoryChild> {

        private final BitbucketRequestExecutor bitbucketRequestExecutor;
        private final HttpUrl url;

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
}
