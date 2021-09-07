package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.AuthorizationException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketCDCapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

@Singleton
public class DeploymentPosterImpl implements DeploymentPoster {

    protected static final Logger LOGGER = Logger.getLogger(DeploymentPosterImpl.class.getName());

    private final BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private final JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    private final BitbucketPluginConfiguration pluginConfiguration;

    @Inject
    public DeploymentPosterImpl(BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                                JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials,
                                BitbucketPluginConfiguration pluginConfiguration) {
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
        this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
        this.pluginConfiguration = pluginConfiguration;
    }

    @Override
    public void postDeployment(String bbsServerId, String projectKey, String repositorySlug, String revisionSha,
                               BitbucketDeployment deployment, Run<?, ?> run,
                               TaskListener taskListener) {
        Optional<BitbucketServerConfiguration> maybeServer = pluginConfiguration.getServerById(bbsServerId);
        if (!maybeServer.isPresent()) {
            taskListener.error(format("Could not send deployment notification to Bitbucket Server: Unknown serverId %s", bbsServerId));
            return;
        }

        BitbucketServerConfiguration server = maybeServer.get();
        Credentials globalAdminCredentials = server.getGlobalCredentialsProvider(run.getParent())
                .getGlobalAdminCredentials()
                .orElse(null);
        BitbucketCredentials credentials = jenkinsToBitbucketCredentials.toBitbucketCredentials(globalAdminCredentials);
        BitbucketClientFactory clientFactory =
                bitbucketClientFactoryProvider.getClient(server.getBaseUrl(), credentials);
        BitbucketCDCapabilities cdCapabilities = clientFactory.getCapabilityClient().getCDCapabilities();
        if (cdCapabilities == null) {
            // Bitbucket doesn't have deployments
            taskListener.error(format("Could not send deployment notification to %s: The Bitbucket version does not support deployments", server.getServerName()));
            return;
        }

        taskListener.getLogger().println(format("Sending notification of %s to %s on commit %s",
                deployment.getState().name(), server.getServerName(), revisionSha));
        try {
            clientFactory.getProjectClient(projectKey)
                    .getRepositoryClient(repositorySlug)
                    .getDeploymentClient(revisionSha)
                    .post(deployment);
            taskListener.getLogger().println(format("Sent notification of %s deployment to %s on commit %s",
                    deployment.getState().name(), server.getServerName(), revisionSha));
        } catch (AuthorizationException e) {
            taskListener.error(format("The personal access token for the Bitbucket Server instance %s is invalid or insufficient to post deployment information: %s",
                    server.getServerName(), e.getMessage()));
        } catch (BitbucketClientException e) {
            // There was a problem sending the deployment to Bitbucket
            String errorMsg = format("Failed to send notification of deployment to %s due to an error: %s",
                    server.getServerName(), e.getMessage());
            taskListener.error(errorMsg);
            // This is typically not an error that the user running the job is able to fix, so
            LOGGER.log(Level.FINE, "Stacktrace from deployment post failure", e);
        }
    }
}
