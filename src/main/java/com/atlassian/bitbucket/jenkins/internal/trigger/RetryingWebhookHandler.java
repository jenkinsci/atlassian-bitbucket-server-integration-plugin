package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCapabilitiesClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.AuthorizationException;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.BitbucketWebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegisterRequest;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegistrationFailed;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Admin permission is needed to add webhooks. It is possible that credentials in job configuration is not admin. This retries
 * adding webhook in with alternate credentials. It retries in following fashion,
 * 1. Global admin is used. If failed then,
 * 2. Job credential is used. If failed then,
 * 3. Global credentials is used.
 */
@Singleton
public class RetryingWebhookHandler {

    private final InstanceBasedNameGenerator instanceBasedNameGenerator;
    private final JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    private final JenkinsProvider jenkinsProvider;
    private final BitbucketClientFactoryProvider provider;

    @Inject
    public RetryingWebhookHandler(
            JenkinsProvider jenkinsProvider,
            BitbucketClientFactoryProvider provider,
            InstanceBasedNameGenerator instanceBasedNameGenerator,
            JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials) {
        this.jenkinsProvider = requireNonNull(jenkinsProvider);
        this.provider = requireNonNull(provider);
        this.instanceBasedNameGenerator = requireNonNull(instanceBasedNameGenerator);
        this.jenkinsToBitbucketCredentials = requireNonNull(jenkinsToBitbucketCredentials);
    }

    public BitbucketWebhook register(String bitbucketBaseUrl,
                                     GlobalCredentialsProvider globalCredentialsProvider,
                                     BitbucketSCMRepository repository) {
        if (isBlank(bitbucketBaseUrl)) {
            throw new IllegalArgumentException("Invalid Bitbucket base URL. Input - " + bitbucketBaseUrl);
        }
        String jenkinsUrl = jenkinsProvider.get().getRootUrl();
        if (isBlank(jenkinsUrl)) {
            throw new IllegalArgumentException("Invalid Jenkins base url. Actual - " + jenkinsUrl);
        }

        WebhookRegisterRequest request = WebhookRegisterRequest.Builder
                .aRequest(repository.getProjectKey(), repository.getRepositorySlug())
                .withJenkinsBaseUrl(jenkinsUrl)
                .isMirror(repository.isMirrorConfigured())
                .withName(instanceBasedNameGenerator.getUniqueName())
                .build();
        String jobCredentials = repository.getCredentialsId();
        try {
            return registerWithRetry(bitbucketBaseUrl, globalCredentialsProvider, jobCredentials, request);
        } catch (Exception ex) {
            String message =
                    "Failed to register webhook in bitbucket server with url " + bitbucketBaseUrl;
            throw new WebhookRegistrationFailed(message, ex);
        }
    }

    private BitbucketWebhook registerUsingCredentials(String bitbucketUrl,
                                                      BitbucketCredentials credentials,
                                                      WebhookRegisterRequest request) {
        BitbucketClientFactory clientFactory = provider.getClient(bitbucketUrl, credentials);
        BitbucketCapabilitiesClient capabilityClient = clientFactory.getCapabilityClient();
        BitbucketWebhookClient webhookClient = clientFactory
                .getProjectClient(request.getProjectKey())
                .getRepositoryClient(request.getRepoSlug())
                .getWebhookClient();
        WebhookHandler handler = new BitbucketWebhookHandler(capabilityClient, webhookClient);
        return handler.register(request);
    }

    @Nullable
    private BitbucketWebhook registerUsingCredentialsQuietly(String bitbucketUrl,
                                                             BitbucketCredentials credentials,
                                                             WebhookRegisterRequest request) {
        try {
            return this.registerUsingCredentials(bitbucketUrl, credentials, request);
        } catch (AuthorizationException exception) {
            return null;
        }
    }

    private BitbucketWebhook registerWithRetry(
            String bitbucketUrl,
            GlobalCredentialsProvider globalCredentialsProvider,
            String jobCredentials,
            WebhookRegisterRequest request) {
        BitbucketWebhook result;
        result = globalCredentialsProvider
                .getGlobalAdminCredentials()
                .map(c ->
                        registerUsingCredentialsQuietly(
                                bitbucketUrl,
                                jenkinsToBitbucketCredentials.toBitbucketCredentials(c),
                                request))
                .orElse(null);
        if (result == null) {
            BitbucketCredentials credentials = jenkinsToBitbucketCredentials.toBitbucketCredentials(jobCredentials);
            result = registerUsingCredentialsQuietly(bitbucketUrl, credentials, request);
        }

        if (result == null) {
            result = globalCredentialsProvider
                    .getGlobalCredentials()
                    .map(c ->
                            registerUsingCredentials(
                                    bitbucketUrl,
                                    jenkinsToBitbucketCredentials.toBitbucketCredentials(c),
                                    request))
                    .orElse(null);
        }
        return result;
    }
}