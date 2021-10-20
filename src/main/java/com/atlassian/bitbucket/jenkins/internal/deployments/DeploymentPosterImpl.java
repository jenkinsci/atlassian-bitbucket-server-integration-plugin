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
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepositoryHelper;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Extension
@Singleton
public class DeploymentPosterImpl extends SCMListener implements DeploymentPoster {

    protected static final Logger LOGGER = Logger.getLogger(DeploymentPosterImpl.class.getName());

    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private BitbucketDeploymentFactory bitbucketDeploymentFactory;
    private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    private BitbucketPluginConfiguration pluginConfiguration;
    private BitbucketSCMRepositoryHelper scmRunHelper;

    public DeploymentPosterImpl() {

    }

    @Inject
    public DeploymentPosterImpl(BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                                BitbucketDeploymentFactory bitbucketDeploymentFactory,
                                JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials,
                                BitbucketPluginConfiguration pluginConfiguration,
                                BitbucketSCMRepositoryHelper scmRunHelper) {
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
        this.bitbucketDeploymentFactory = bitbucketDeploymentFactory;
        this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
        this.pluginConfiguration = pluginConfiguration;
        this.scmRunHelper = scmRunHelper;
    }

    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener,
                           @CheckForNull File changelogFile,
                           @CheckForNull SCMRevisionState pollingBaseline) {
        DeploymentNotifier deploymentPublisher = getDeploymentPublisher(build);
        if (deploymentPublisher == null) {
            return;
        }
        BitbucketDeploymentEnvironment environment = deploymentPublisher.getEnvironment(build, listener);
        BitbucketDeployment deployment = bitbucketDeploymentFactory.createDeployment(build, environment);

        // Get the repository off the SCM
        BitbucketSCMRepository repo = scmRunHelper.getRepository(build, scm);
        // If there's no repo in the environment, then we don't know where to send the build status to
        if (repo == null) {
            listener.getLogger().println("Could not post deployment information: Bitbucket repository information not present on the SCM");
            return;
        }

        // Get the commit off the environment
        String revisionSha1;
        try {
            revisionSha1 = build.getEnvironment(listener).get(GitSCM.GIT_COMMIT);
        } catch (IOException | InterruptedException e) {
            listener.getLogger().println("Could not post deployment information: Error reading the environment variables");
            return;
        }
        if (isBlank(revisionSha1)) {
            listener.getLogger().println("Could not post deployment information: Git commit information not present in the environment variables");
            return;
        }

        postDeployment(repo, revisionSha1, deployment, build, listener);
    }

    @Override
    public void postDeployment(BitbucketSCMRepository repository, String revisionSha, BitbucketDeployment deployment, Run<?, ?> run, TaskListener taskListener) {
        Optional<BitbucketServerConfiguration> maybeServer = pluginConfiguration.getServerById(repository.getServerId());
        if (!maybeServer.isPresent()) {
            taskListener.error(format("Could not send deployment notification to Bitbucket Server: Unknown serverId %s", repository.getServerId()));
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

        taskListener.getLogger().println(format("Sending notification of %s deployment to %s on commit %s",
                deployment.getState().name(), server.getServerName(), revisionSha));
        try {
            clientFactory.getProjectClient(repository.getProjectKey())
                    .getRepositoryClient(repository.getRepositorySlug())
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

    @CheckForNull
    private DeploymentNotifier getDeploymentPublisher(Run<?, ?> build) {
        if (!(build instanceof FreeStyleBuild)) {
            // Notifiers only support freestyle builds
            return null;
        }
        FreeStyleBuild freeStyleBuild = (FreeStyleBuild) build;
        return freeStyleBuild.getParent().getPublishersList().get(DeploymentNotifier.class);
    }
}
