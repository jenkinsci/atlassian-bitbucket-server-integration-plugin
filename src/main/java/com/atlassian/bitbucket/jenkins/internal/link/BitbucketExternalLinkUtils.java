package com.atlassian.bitbucket.jenkins.internal.link;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import hudson.util.FormValidation;
import okhttp3.HttpUrl;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;
import java.util.Optional;

/**
 * @since 2.1.4
 */
@Singleton
public class BitbucketExternalLinkUtils {

    @Inject
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;

    public BitbucketExternalLinkUtils() { }

    public BitbucketExternalLinkUtils(BitbucketPluginConfiguration bitbucketPluginConfiguration) {
        this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
    }

    public Optional<BitbucketExternalLink> createBranchDiffLink(BitbucketSCMRepository bitbucketRepository,
                                                                String branch) {
        BitbucketServerConfiguration configuration = getConfiguration(bitbucketRepository);
        if (configuration == null) {
            return Optional.empty();
        }

        String url = HttpUrl.get(configuration.getBaseUrl()).newBuilder()
                .addPathSegment("projects")
                .addPathSegment(bitbucketRepository.getProjectKey())
                .addPathSegment("repos")
                .addPathSegment(bitbucketRepository.getRepositorySlug())
                .addPathSegment("compare")
                .addPathSegment("commits")
                .addQueryParameter("sourceBranch", "refs/heads/" + branch)
                .toString();

        return Optional.of(new BitbucketExternalLink(url, BitbucketLinkType.BRANCH));
    }

    public Optional<BitbucketExternalLink> createRepoLink(BitbucketSCMRepository bitbucketRepository) {
        BitbucketServerConfiguration configuration = getConfiguration(bitbucketRepository);
        if (configuration == null) {
            return Optional.empty();
        }

        String url = HttpUrl.get(configuration.getBaseUrl()).newBuilder()
                .addPathSegment("projects")
                .addPathSegment(bitbucketRepository.getProjectKey())
                .addPathSegment("repos")
                .addPathSegment(bitbucketRepository.getRepositorySlug())
                .toString();

        return Optional.of(new BitbucketExternalLink(url, BitbucketLinkType.REPOSITORY));
    }

    @Nullable
    private BitbucketServerConfiguration getConfiguration(BitbucketSCMRepository bitbucketRepository) {
        Optional<BitbucketServerConfiguration> maybeConfig = bitbucketPluginConfiguration.getServerById(
                Objects.toString(bitbucketRepository.getServerId(), ""));
        FormValidation configValid = maybeConfig.map(BitbucketServerConfiguration::validate)
                .orElse(FormValidation.error("Valid config is not present"));

        if (configValid.kind == FormValidation.Kind.ERROR) {
            return null;
        }
        return maybeConfig.get();
    }
}
