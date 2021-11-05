package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequestState;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.provider.InstanceKeyPairProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

import java.util.Collection;
import java.util.stream.Stream;

import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketRepositoryClientImpl implements BitbucketRepositoryClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String projectKey;
    private final String repositorySlug;

    BitbucketRepositoryClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, String projectKey,
                                  String repositorySlug) {
        this.bitbucketRequestExecutor = requireNonNull(bitbucketRequestExecutor, "bitbucketRequestExecutor");
        this.projectKey = requireNonNull(stripToNull(projectKey), "projectKey");
        this.repositorySlug = requireNonNull(stripToNull(repositorySlug), "repositorySlug");
    }

    public BitbucketBuildStatusClient getBuildStatusClient(String revisionSha,
                                                           BitbucketSCMRepository bitbucketSCMRepo,
                                                           BitbucketCICapabilities ciCapabilities,
                                                           InstanceKeyPairProvider instanceKeyPairProvider,
                                                           DisplayURLProvider displayURLProvider) {
        if (ciCapabilities.supportsRichBuildStatus()) {
            return new ModernBitbucketBuildStatusClientImpl(bitbucketRequestExecutor, bitbucketSCMRepo.getProjectKey(),
                    bitbucketSCMRepo.getRepositorySlug(), revisionSha, instanceKeyPairProvider, displayURLProvider);
        }
        return new BitbucketBuildStatusClientImpl(bitbucketRequestExecutor, revisionSha);
    }

    @Override
    public BitbucketBuildStatusClient getBuildStatusClient(String revisionSha, BitbucketCICapabilities ciCapabilities) {
        if (ciCapabilities.supportsRichBuildStatus()) {
            return new ModernBitbucketBuildStatusClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug,
                    revisionSha);
        }
        return new BitbucketBuildStatusClientImpl(bitbucketRequestExecutor, revisionSha);
    }

    @Override
    public BitbucketDeploymentClient getDeploymentClient(String revisionSha) {
        return new BitbucketDeploymentClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug, revisionSha);
    }

    @Override
    public BitbucketFilePathClient getFilePathClient() {
        return new BitbucketFilePathClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug);
    }

    @Override
    public Stream<BitbucketPullRequest> getPullRequests(BitbucketPullRequestState state) {
        return getPullRequestsWithState(state.toString());
    }

    @Override
    public Stream<BitbucketPullRequest> getPullRequests() {
        return getPullRequestsWithState("ALL");
    }

    @Override
    public BitbucketRepository getRepository() {
        return bitbucketRequestExecutor.makeGetRequest(getRepositoryUrl().build(), BitbucketRepository.class).getBody();
    }

    @Override
    public BitbucketWebhookClient getWebhookClient() {
        return new BitbucketWebhookClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug);
    }

    @Override
    public BitbucketDefaultBranch getDefaultBranch() {
        return bitbucketRequestExecutor.makeGetRequest(getDefaultBranchyUrl().build(), BitbucketDefaultBranch.class)
                .getBody();
    }

    private Stream<BitbucketPullRequest> getBitbucketPullRequestStream(HttpUrl.Builder urlBuilder) {
        HttpUrl url = urlBuilder.build();
        BitbucketPage<BitbucketPullRequest> firstPage =
                bitbucketRequestExecutor.makeGetRequest(url, new TypeReference<BitbucketPage<BitbucketPullRequest>>() {}).getBody();
        return BitbucketPageStreamUtil.toStream(firstPage, new NextPageFetcherImpl(url, bitbucketRequestExecutor))
                .map(BitbucketPage::getValues).flatMap(Collection::stream);
    }

    private Stream<BitbucketPullRequest> getPullRequestsWithState(String stateQuery) {
        return getBitbucketPullRequestStream(getRepositoryUrl()
                .addPathSegment("pull-requests")
                .addQueryParameter("withAttributes", "false")
                .addQueryParameter("withProperties", "false")
                .addQueryParameter("state", stateQuery));
    }

    private HttpUrl.Builder getRepositoryUrl() {
        return bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repositorySlug);
    }
    
    private HttpUrl.Builder getDefaultBranchyUrl() {
        return getRepositoryUrl()
                .addPathSegment("default-branch");
    }

    static class NextPageFetcherImpl implements NextPageFetcher<BitbucketPullRequest> {

        private final BitbucketRequestExecutor bitbucketRequestExecutor;
        private final HttpUrl url;

        NextPageFetcherImpl(HttpUrl url,
                            BitbucketRequestExecutor bitbucketRequestExecutor) {
            this.url = url;
            this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        }

        @Override
        public BitbucketPage<BitbucketPullRequest> next(BitbucketPage<BitbucketPullRequest> previous) {
            if (previous.isLastPage()) {
                throw new IllegalArgumentException("Last page does not have next page");
            }
            return bitbucketRequestExecutor.makeGetRequest(
                    nextPageUrl(previous),
                    new TypeReference<BitbucketPage<BitbucketPullRequest>>() {}).getBody();
        }

        private HttpUrl nextPageUrl(BitbucketPage<BitbucketPullRequest> previous) {
            return url.newBuilder().addQueryParameter("start", valueOf(previous.getNextPageStart())).build();
        }
    }
}
