package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCommit;
import okhttp3.HttpUrl;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

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


        return bitbucketRequestExecutor.makeGetRequest(url, BitbucketCommit.class).getBody();
    }
}
