package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.AuthorizationException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
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

@Singleton
public class DeploymentPosterImpl implements DeploymentPoster {

    protected static final Logger LOGGER = Logger.getLogger(DeploymentPosterImpl.class.getName());

    @Inject
    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    @Inject
    private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    @Inject
    private BitbucketPluginConfiguration pluginConfiguration;

    @Override
    public void postDeployment(String bbsServerId, String projectKey, String repositorySlug, String revisionSha,
                               BitbucketDeployment deployment, Run<?, ?> run,
                               TaskListener taskListener) {
        Optional<BitbucketServerConfiguration> maybeServer = pluginConfiguration.getServerById(bbsServerId);
        if (!maybeServer.isPresent()) {
            taskListener.error(String.format("Could not send deployment notification to Bitbucket Server: Unknown serverId %s", bbsServerId));
            return;
        }

        BitbucketServerConfiguration server = maybeServer.get();
        BitbucketClientFactory clientFactory = getClientFactory(run, server);
        BitbucketCDCapabilities cdCapabilities = clientFactory.getCapabilityClient().getCDCapabilities();
        if (!cdCapabilities.supportsDeployments()) {
            // Bitbucket doesn't have deployments
            taskListener.error("Could not send deployment notification to Bitbucket Server: The Bitbucket version does not support deployments");
            return;
        }

        taskListener.getLogger().printf("Sending notification of %s to %s on commit %s%n",
                deployment.getState().name(), server.getServerName(), revisionSha);
        try {
            clientFactory.getProjectClient(projectKey)
                    .getRepositoryClient(repositorySlug)
                    .getDeploymentClient(revisionSha)
                    .post(deployment);
            taskListener.getLogger().printf("Successfully sent notification of %s deployment to %s on commit %s%n",
                    deployment.getState().name(), server.getServerName(), revisionSha);
        } catch (AuthorizationException e) {
            taskListener.error(String.format("Failed to send notification of deployment to %s due to an authorization error: %s",
                    server.getServerName(), e.getMessage()));
        } catch (BitbucketClientException e) {
            // There was a problem sending the deployment to Bitbucket
            String errorMsg = String.format("Failed to send notification of deployment to %s due to an error: %s",
                    server.getServerName(), e.getMessage());
            taskListener.getLogger().println(errorMsg);
            // This is typically not an error that the user running the job is able to fix, so
            LOGGER.log(Level.FINE, "Stacktrace from deployment post failure", e);
        }
    }

    private BitbucketClientFactory getClientFactory(Run<?, ?> run, BitbucketServerConfiguration server) {
        GlobalCredentialsProvider globalCredentialsProvider = server.getGlobalCredentialsProvider(run.getParent());
        Credentials globalAdminCredentials = globalCredentialsProvider.getGlobalAdminCredentials().orElse(null);
        return bitbucketClientFactoryProvider.getClient(server.getBaseUrl(),
                jenkinsToBitbucketCredentials.toBitbucketCredentials(globalAdminCredentials));
    }
}
