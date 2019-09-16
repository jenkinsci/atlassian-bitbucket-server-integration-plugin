package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
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

    private static final String BUILD_STATUS_ERROR_MSG = "Failed to post build status, additional information:";
    private static final String BUILD_STATUS_FORMAT = "Posting build status of %s to %s";
    private static final Logger LOGGER = Logger.getLogger(BuildStatusPoster.class.getName());
    private static final String NO_SERVER_MSG =
            "Failed to post build status as the provided Bitbucket Server config does not exist";
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
                BitbucketBuildStatus buildStatus = BitbucketBuildStatusFactory.fromBuild(build);
                listener.getLogger().format(BUILD_STATUS_FORMAT, buildStatus.getState(), server.getServerName());

                BitbucketCredentials credentials =
                        BitbucketCredentialsAdaptor.createWithFallback(server.getCredentials(), server);
                bitbucketClientFactoryProvider.getClient(server.getBaseUrl(), credentials)
                        .getBuildStatusClient(revisionAction.getRevisionSha1())
                        .post(buildStatus);
                return;
            } catch (RuntimeException e) {
                String errorMsg = BUILD_STATUS_ERROR_MSG + ' ' + e.getMessage();
                LOGGER.info(errorMsg);
                listener.getLogger().println(errorMsg);
                LOGGER.log(Level.FINE, "Stacktrace from build status failure", e);
            }
        } else {
            listener.error(NO_SERVER_MSG);
        }
    }
}

