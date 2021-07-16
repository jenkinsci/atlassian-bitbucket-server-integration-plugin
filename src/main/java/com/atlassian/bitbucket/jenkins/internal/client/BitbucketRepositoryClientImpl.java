package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.provider.InstanceKeyPairProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.google.common.annotations.VisibleForTesting;
import okhttp3.HttpUrl;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

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

    @VisibleForTesting
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
}
