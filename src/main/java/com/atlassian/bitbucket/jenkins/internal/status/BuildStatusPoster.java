package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
import com.cloudbees.plugins.credentials.Credentials;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class BuildStatusPoster extends RunListener<Run<?, ?>> {

    private static final String BUILD_STATUS_ERROR_MSG = "Failed to post build status, additional information:";
    private static final String BUILD_STATUS_FORMAT =
            "Posting build status of %s to %s for commit id [%s] and ref '%s'";
    private static final Logger LOGGER = Logger.getLogger(BuildStatusPoster.class.getName());
    private static final String NO_SERVER_MSG =
            "Failed to post build status as the provided Bitbucket Server config does not exist";
    private static final String LEGACY_BUILD_STATUS_PROPERTY = "legacyBuildStatus";

    @Inject
    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    @Inject
    private BitbucketPluginConfiguration pluginConfiguration;
    @Inject
    private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    @Inject
    private BitbucketBuildStatusFactory bitbucketBuildStatusFactory;

    public BuildStatusPoster() {
    }

    public BuildStatusPoster(BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                             BitbucketPluginConfiguration pluginConfiguration,
                             JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials,
                             BitbucketBuildStatusFactory bitbucketBuildStatusFactory) {
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
        this.pluginConfiguration = pluginConfiguration;
        this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
        this.bitbucketBuildStatusFactory = bitbucketBuildStatusFactory;
    }

    @Override
    public void onCompleted(Run<?, ?> r, @Nonnull TaskListener listener) {
        BitbucketRevisionAction bitbucketRevisionAction = r.getAction(BitbucketRevisionAction.class);
        if (bitbucketRevisionAction != null) {
            postBuildStatus(bitbucketRevisionAction, r, listener);
        }
    }

    public void postBuildStatus(BitbucketRevisionAction revisionAction, Run<?, ?> run, TaskListener listener) {
        Optional<BitbucketServerConfiguration> serverOptional =
                pluginConfiguration.getServerById(revisionAction.getBitbucketSCMRepo().getServerId());
        if (serverOptional.isPresent()) {
            postBuildStatus(serverOptional.get(), revisionAction, run, listener);
        } else {
            listener.error(NO_SERVER_MSG);
        }
    }

    private void postBuildStatus(BitbucketServerConfiguration server, BitbucketRevisionAction revisionAction,
                                 Run<?, ?> run, TaskListener listener) {
        GlobalCredentialsProvider globalCredentialsProvider = server.getGlobalCredentialsProvider(run.getParent());
        try {
            BitbucketClientFactory bbsClient = getBbsClient(server, globalCredentialsProvider);
            BitbucketCICapabilities ciCapabilities = bbsClient.getCapabilityClient().getCICapabilities();

            BitbucketBuildStatus buildStatus;
            if (!useLegacyBuildStatus() && ciCapabilities.supportsRichBuildStatus()) {
                buildStatus = bitbucketBuildStatusFactory.createRichBuildStatus(run);
            } else {
                buildStatus = bitbucketBuildStatusFactory.createLegacyBuildStatus(run);
            }

            listener.getLogger().println(String.format(BUILD_STATUS_FORMAT,
                    buildStatus.getState(), server.getServerName(), revisionAction.getRevisionSha1(),
                    buildStatus.getRef()));

            bbsClient.getBuildStatusClient(revisionAction.getRevisionSha1(), revisionAction.getBitbucketSCMRepo(), ciCapabilities)
                    .post(buildStatus);
        } catch (RuntimeException e) {
            String errorMsg = BUILD_STATUS_ERROR_MSG + ' ' + e.getMessage();
            LOGGER.info(errorMsg);
            listener.getLogger().println(errorMsg);
            LOGGER.log(Level.FINE, "Stacktrace from build status failure", e);
        }
    }

    private BitbucketClientFactory getBbsClient(BitbucketServerConfiguration server,
                                                GlobalCredentialsProvider globalCredentialsProvider) {
        Credentials globalAdminCredentials = globalCredentialsProvider.getGlobalAdminCredentials().orElse(null);
        BitbucketCredentials credentials =
                jenkinsToBitbucketCredentials.toBitbucketCredentials(globalAdminCredentials, globalCredentialsProvider);
        return bitbucketClientFactoryProvider.getClient(server.getBaseUrl(), credentials);
    }

    @VisibleForTesting
    boolean useLegacyBuildStatus() {
        return Boolean.getBoolean(LEGACY_BUILD_STATUS_PROPERTY);
    }
}
