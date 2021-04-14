package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketRepositoryClientImpl implements BitbucketRepositoryClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String projectKey;
    private final String repositorySlug;

    BitbucketRepositoryClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, String projectKey, String repositorySlug) {
        this.bitbucketRequestExecutor = requireNonNull(bitbucketRequestExecutor, "bitbucketRequestExecutor");
        this.projectKey = requireNonNull(stripToNull(projectKey), "projectKey");
        this.repositorySlug = requireNonNull(stripToNull(repositorySlug), "repositorySlug");
    }

    @Override
    public BitbucketRepository getRepository() {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repositorySlug);

        return bitbucketRequestExecutor.makeGetRequest(urlBuilder.build(), BitbucketRepository.class).getBody();
    }

    @Override
    public BitbucketWebhookClient getWebhookClient() {
        return new BitbucketWebhookClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug);
    }

    public BitbucketFilePathClient getFilePathClient() {
        return new BitbucketFilePathClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug);
    }
}
