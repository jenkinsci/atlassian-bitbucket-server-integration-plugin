package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
public class BuildStatusPoster {

    private static final Logger LOGGER = Logger.getLogger(BuildStatusPoster.class.getName());
    @Inject
    BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    @Inject
    private BitbucketPluginConfiguration pluginConfiguration;

    public void postBuildStatus(AbstractBuild build, TaskListener listener) {
        BitbucketRevisionAction revisionAction = build.getAction(BitbucketRevisionAction.class);
        if (revisionAction == null) {
            return;
        }
        Optional<BitbucketServerConfiguration> serverOptional =
                pluginConfiguration.getServerById(revisionAction.getServerId());
        if (serverOptional.isPresent()) {
            BitbucketServerConfiguration server = serverOptional.get();
            try {
                BitbucketBuildStatus buildStatus = new BitbucketBuildStatus(build);
                listener.getLogger().println(
                        "Posting build status of " + buildStatus.getState() + " to: " + server.getServerName());

                BitbucketCredentials credentials =
                        BitbucketCredentialsAdaptor.createWithFallback(server.getCredentials(), server);
                bitbucketClientFactoryProvider.getClient(server.getBaseUrl(), credentials)
                        .getBuildStatusClient(revisionAction.getRevisionSha1())
                        .post(buildStatus);
                return;
            } catch (RuntimeException e) {
                LOGGER.info("Failed to post build status, additional information: " + e.getMessage());
                LOGGER.log(Level.FINE, "Stacktrace from build status failure", e);
            }
        }
        listener.error("Failed to post build status as the provided Bitbucket Server config does not exist");
    }
}

