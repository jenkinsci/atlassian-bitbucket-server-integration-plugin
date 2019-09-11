package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.client.exception.AuthorizationException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.BitbucketWebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegisterRequest;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegistrationFailed;

import javax.inject.Inject;

import static java.util.Objects.requireNonNull;

/**
 * Admin permission is needed to add webhooks. It is possible that credentials in job configuration is not admin. This retries
 * adding webhook in with alternate credentials. It retries in following fashion,
 * 1. Global admin is used. If failed then,
 * 2. Job credential is used. If failed then,
 * 3. Global credentials is used.
 */
public class RetryingWebhookHandler {

    private final BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private final InstanceBasedNameGenerator instanceBasedNameGenerator;
    private final JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    private final JenkinsProvider jenkinsProvider;
    private final BitbucketClientFactoryProvider provider;

    @Inject
    public RetryingWebhookHandler(
            JenkinsProvider jenkinsProvider,
            BitbucketClientFactoryProvider provider,
            InstanceBasedNameGenerator instanceBasedNameGenerator,
            JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials,
            BitbucketPluginConfiguration bitbucketPluginConfiguration) {
        this.jenkinsProvider = requireNonNull(jenkinsProvider);
        this.provider = requireNonNull(provider);
        this.instanceBasedNameGenerator = requireNonNull(instanceBasedNameGenerator);
        this.jenkinsToBitbucketCredentials = requireNonNull(jenkinsToBitbucketCredentials);
        this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
    }

    public BitbucketWebhook register(BitbucketSCMRepository repository) {
        BitbucketServerConfiguration serverConfiguration = getServer(repository.getServerId());
        String jenkinsUrl = jenkinsProvider.get().getRootUrl();
        requireNonNull(serverConfiguration);
        requireNonNull(repository);
        requireNonNull(serverConfiguration.getBaseUrl(), "Bitbucket base URL not available");
        requireNonNull(jenkinsUrl, "Jenkins root URL not available");

        WebhookRegisterRequest request = WebhookRegisterRequest.Builder
                .aRequest(repository.getProjectKey(), repository.getRepositorySlug())
                .withJenkinsBaseUrl(jenkinsUrl)
                .isMirror(repository.isMirror())
                .withName(instanceBasedNameGenerator.getUniqueName())
                .build();
        String jobCredentials = repository.getCredentialsId();
        try {
            return registerWithRetry(serverConfiguration, jobCredentials, request);
        } catch (Exception ex) {
            String message =
                    "Failed to register webhook in bitbucket server with url " + serverConfiguration.getBaseUrl();
            throw new WebhookRegistrationFailed(message, ex);
        }
    }

    private BitbucketServerConfiguration getServer(String serverId) {
        return bitbucketPluginConfiguration
                .getServerById(serverId)
                .orElseThrow(() -> new WebhookRegistrationFailed(
                        "Server config not found for input server id" + serverId));
    }

    private BitbucketWebhook registerUsingCredentials(BitbucketServerConfiguration serverConfiguration,
                                                      BitbucketCredentials credentials,
                                                      WebhookRegisterRequest request) {
        BitbucketClientFactory clientFactory = provider.getClient(serverConfiguration.getBaseUrl(), credentials);
        BitbucketCapabilitiesClient capabilityClient = clientFactory.getCapabilityClient();
        BitbucketWebhookClient webhookClient =
                clientFactory.getProjectClient(request.getProjectKey()).getRepositoryClient(request.getRepoSlug())
                        .getWebhookClient();
        WebhookHandler handler = new BitbucketWebhookHandler(capabilityClient, webhookClient);
        return handler.register(request);
    }

    private BitbucketWebhook registerWithRetry(
            BitbucketServerConfiguration serverConfiguration,
            String jobCredentials,
            WebhookRegisterRequest request) {
        try {
            BitbucketCredentials globalAdminCredentials =
                    jenkinsToBitbucketCredentials.toBitbucketCredentials(serverConfiguration.getAdminCredentials());
            return registerUsingCredentials(serverConfiguration, globalAdminCredentials, request);
        } catch (AuthorizationException exception) {
            try {
                BitbucketCredentials credentials = jenkinsToBitbucketCredentials.toBitbucketCredentials(jobCredentials);
                return registerUsingCredentials(serverConfiguration, credentials, request);
            } catch (AuthorizationException ex) {
                BitbucketCredentials globalCredentials =
                        jenkinsToBitbucketCredentials.toBitbucketCredentials(serverConfiguration.getCredentials());
                return registerUsingCredentials(serverConfiguration, globalCredentials, request);
            }
        }
    }
}