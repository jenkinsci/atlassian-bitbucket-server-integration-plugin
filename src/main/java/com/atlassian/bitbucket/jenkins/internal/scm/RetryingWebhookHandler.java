package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.client.exception.AuthorizationException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.BitbucketWebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegisterRequest;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegisterResult;

import static java.util.Objects.requireNonNull;

/**
 * Admin permission is needed to add webhooks. It is possible that credentials in job configuration is not admin. This retries
 * adding webhook in with alternate credentials. It retries in following fashion,
 * 1. First Job credential is used. If failed then,
 * 2. Global admin is used. If failed then,
 * 3. Global credentials is used.
 */
public class RetryingWebhookHandler {

    private final String jenkinsUrl;
    private final BitbucketClientFactoryProvider provider;
    private final BitbucketServerConfiguration serverConfiguration;
    private final InstanceBasedNameGenerator instanceBasedNameGenerator;
    private final JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

    public RetryingWebhookHandler(
            String jenkinsUrl,
            BitbucketClientFactoryProvider provider,
            BitbucketServerConfiguration serverConfiguration,
            InstanceBasedNameGenerator instanceBasedNameGenerator,
            JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials) {
        this.jenkinsUrl = requireNonNull(jenkinsUrl);
        this.provider = requireNonNull(provider);
        this.serverConfiguration = requireNonNull(serverConfiguration);
        this.instanceBasedNameGenerator = requireNonNull(instanceBasedNameGenerator);
        this.jenkinsToBitbucketCredentials = requireNonNull(jenkinsToBitbucketCredentials);
        requireNonNull(serverConfiguration.getBaseUrl(), "Bitbucket base URL not available");
    }

    public WebhookRegisterResult register(BitbucketSCMRepository repository, boolean isMirrorSelected) {
        WebhookRegisterRequest request = WebhookRegisterRequest.Builder
                .aRequest(repository.getProjectKey(), repository.getRepositorySlug())
                .withJenkinsBaseUrl(jenkinsUrl)
                .isMirror(isMirrorSelected)
                .withName(instanceBasedNameGenerator.getUniqueName())
                .build();
        String jobCredentials = repository.getCredentialsId();
        BitbucketCredentials credentials = jenkinsToBitbucketCredentials.toBitbucketCredentials(jobCredentials);
        return registerWithRetry(credentials, request);
    }

    private WebhookRegisterResult registerUsingCredentials(BitbucketCredentials credentials,
                                                           WebhookRegisterRequest request) {
        BitbucketClientFactory clientFactory = provider.getClient(serverConfiguration.getBaseUrl(), credentials);
        BitbucketCapabilitiesClient capabilityClient = clientFactory.getCapabilityClient();
        BitbucketWebhookClient webhookClient =
                clientFactory.getProjectClient(request.getProjectKey()).getRepositoryClient(request.getRepoSlug())
                        .getWebhookClient();
        WebhookHandler handler = new BitbucketWebhookHandler(capabilityClient, webhookClient);
        return handler.register(request);
    }

    private WebhookRegisterResult registerWithRetry(BitbucketCredentials jobCredentials,
                                                    WebhookRegisterRequest request) {
        try {
            return registerUsingCredentials(jobCredentials, request);
        } catch (AuthorizationException exception) {
            BitbucketCredentials globalAdminCredentials =
                    jenkinsToBitbucketCredentials.toBitbucketCredentials(serverConfiguration.getAdminCredentials());
            try {
                return registerUsingCredentials(globalAdminCredentials, request);
            } catch (AuthorizationException ex) {
                BitbucketCredentials globalCredentials =
                        jenkinsToBitbucketCredentials.toBitbucketCredentials(serverConfiguration.getCredentials());
                return registerUsingCredentials(globalCredentials, request);
            }
        }
    }
}