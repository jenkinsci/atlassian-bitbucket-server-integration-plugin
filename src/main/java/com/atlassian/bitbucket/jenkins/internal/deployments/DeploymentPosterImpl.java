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
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketRevisionAction;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Publisher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

@Singleton
public class DeploymentPosterImpl implements DeploymentPoster {

    private static final Logger LOGGER = Logger.getLogger(DeploymentPosterImpl.class.getName());

    private final BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private final JenkinsProvider jenkinsProvider;
    private final JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    private final BitbucketPluginConfiguration pluginConfiguration;

    @Inject
    public DeploymentPosterImpl(BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                                JenkinsProvider jenkinsProvider,
                                JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials,
                                BitbucketPluginConfiguration pluginConfiguration) {
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
        this.jenkinsProvider = jenkinsProvider;
        this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
        this.pluginConfiguration = pluginConfiguration;
    }

    @Override
    public void onCheckout(Run<?, ?> build, TaskListener listener) {
        try {
            BitbucketRevisionAction revisionAction = build.getAction(BitbucketRevisionAction.class);
            if (revisionAction == null) {
                // Not a Bitbucket checkout
                return;
            }
            if (!(build instanceof FreeStyleBuild)) {
                // Not a freestyle build so we can't get the publisher off it
                return;
            }
            FreeStyleBuild freeStyleBuild = (FreeStyleBuild) build;
            DeployedToEnvironmentNotifierStep.DescriptorImpl deploymentPublisherDescriptor = jenkinsProvider.get()
                    .getDescriptorByType(DeployedToEnvironmentNotifierStep.DescriptorImpl.class);
            Publisher publisher = freeStyleBuild.getParent().getPublisher(deploymentPublisherDescriptor);
            if (!(publisher instanceof DeployedToEnvironmentNotifierStep)) {
                // Not a deployment
                return;
            }
            DeployedToEnvironmentNotifierStep deploymentPublisher = (DeployedToEnvironmentNotifierStep) publisher;
            BitbucketDeployment deployment = deploymentPublisher.getBitbucketDeployment(build, listener);
            postDeployment(revisionAction, deployment, build, listener);
        } catch (RuntimeException e) {
            // This shouldn't happen because deploymentPoster.postDeployment doesn't throw anything. But just in case,
            // we don't want to throw anything and potentially stop other steps from being executed
            String errorMsg = format("An error occurred when trying to post the in-progress deployment to Bitbucket Server: %s", e.getMessage());
            listener.error(errorMsg);
            LOGGER.info(errorMsg);
            LOGGER.log(Level.FINE, "Stacktrace from deployment post failure", e);
        }
    }

    @Override
    public void postDeployment(BitbucketRevisionAction revisionAction, BitbucketDeployment deployment, Run<?, ?> run,
                               TaskListener taskListener) {
        BitbucketSCMRepository bitbucketSCMRepo = revisionAction.getBitbucketSCMRepo();
        String bbsServerId = bitbucketSCMRepo.getServerId();
        String revisionSha = revisionAction.getRevisionSha1();
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
            clientFactory.getProjectClient(bitbucketSCMRepo.getProjectKey())
                    .getRepositoryClient(bitbucketSCMRepo.getRepositorySlug())
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
