package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentialsModule;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.cloudbees.plugins.credentials.Credentials;
import com.google.inject.Guice;
import hudson.model.Run;
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
    private transient JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

    public void postBuildStatus(Run<?, ?> run, TaskListener listener) {
        BitbucketRevisionAction revisionAction = run.getAction(BitbucketRevisionAction.class);
        if (revisionAction == null) {
            return;
        }
        Optional<BitbucketServerConfiguration> serverOptional =
                pluginConfiguration.getServerById(revisionAction.getServerId());
        if (serverOptional.isPresent()) {
            BitbucketServerConfiguration server = serverOptional.get();
            GlobalCredentialsProvider globalCredentialsProvider =
                    server.getGlobalCredentialsProvider(run.getParent());
            try {
                if (jenkinsToBitbucketCredentials == null) {
                    Guice.createInjector(new JenkinsToBitbucketCredentialsModule()).injectMembers(this);
                }
                BitbucketBuildStatus buildStatus = BitbucketBuildStatusFactory.fromBuild(run);
                listener.getLogger().format(BUILD_STATUS_FORMAT, buildStatus.getState(), server.getServerName());

                Credentials globalAdminCredentials = globalCredentialsProvider.getGlobalAdminCredentials().orElse(null);
                BitbucketCredentials credentials =
                        jenkinsToBitbucketCredentials.toBitbucketCredentials(globalAdminCredentials, globalCredentialsProvider);
                bitbucketClientFactoryProvider.getClient(server.getBaseUrl(), credentials)
                        .getBuildStatusClient(revisionAction.getRevisionSha1())
                        .post(buildStatus);
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

    @Inject
    public void setJenkinsToBitbucketCredentials(
            JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials) {
        this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
    }
}

