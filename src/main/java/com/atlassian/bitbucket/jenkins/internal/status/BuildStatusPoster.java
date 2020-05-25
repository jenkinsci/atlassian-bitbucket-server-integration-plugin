package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentialsModule;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketRefNameExtractorFactory;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMException;
import com.cloudbees.plugins.credentials.Credentials;
import com.google.inject.Guice;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.scm.BitbucketScmRunHelper.getBitbucketSCM;
import static com.atlassian.bitbucket.jenkins.internal.scm.BitbucketScmRunHelper.hasBitbucketScmOrBitbucketScmSource;

@Singleton
public class BuildStatusPoster {

    private static final String BUILD_STATUS_ERROR_MSG = "Failed to post build status, additional information:";
    private static final String BUILD_STATUS_FORMAT = "Posting build status of %s to %s for commit id [%s]";
    private static final Logger LOGGER = Logger.getLogger(BuildStatusPoster.class.getName());
    private static final String NO_SERVER_MSG =
            "Failed to post build status as the provided Bitbucket Server config does not exist";
    @Inject
    BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    @Inject
    private BitbucketPluginConfiguration pluginConfiguration;
    private transient JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

    void postBuildStatus(Run<?, ?> run, TaskListener listener) {
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

                Credentials globalAdminCredentials = globalCredentialsProvider.getGlobalAdminCredentials().orElse(null);
                BitbucketCredentials credentials =
                        jenkinsToBitbucketCredentials.toBitbucketCredentials(globalAdminCredentials, globalCredentialsProvider);
                BitbucketClientFactory bbsClient =
                        bitbucketClientFactoryProvider.getClient(server.getBaseUrl(), credentials);
                BitbucketCICapabilities ciCapabilities = bbsClient.getCapabilityClient().getCICapabilities();
                BitbucketBuildStatus buildStatus = BitbucketBuildStatusFactory.fromBuild(run, ciCapabilities);
                listener.getLogger().format(BUILD_STATUS_FORMAT, buildStatus.getState(), server.getServerName(), revisionAction.getRevisionSha1());

                bbsClient.getBuildStatusClient(revisionAction.getRevisionSha1(), getBitbucketSCM(run).orElse(null), ciCapabilities)
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

    @Extension
    public static class LocalRunListener<R extends Run<?, ?>> extends RunListener<R> {

        @Inject
        private BuildStatusPoster buildStatusPoster;

        @Override
        public void onCompleted(R r, @Nonnull TaskListener listener) {
            if (hasBitbucketScmOrBitbucketScmSource(r)) {
                buildStatusPoster.postBuildStatus(r, listener);
            }
        }
    }

    @Extension
    public static class LocalSCMListener extends SCMListener {

        @Inject
        private BuildStatusPoster buildStatusPoster;

        @Override
        public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener,
                               @CheckForNull File changelogFile,
                               @CheckForNull SCMRevisionState pollingBaseline) {
            BitbucketSCM bitbucketSCM = scm instanceof BitbucketSCM ? (BitbucketSCM) scm : null;
            if (bitbucketSCM != null && bitbucketSCM.getServerId() != null) {
                GitSCM gitSCM = ((BitbucketSCM) scm).getGitSCM();
                Map<String, String> env = new HashMap<>();
                scm.buildEnvironment(build, env);

                String repositoryName = (gitSCM.getRepositories().stream().findFirst()
                        .orElseThrow(() -> new BitbucketSCMException("No repository found in the GitSCM")))
                        .getName();
                String branch = env.get(GitSCM.GIT_BRANCH);
                BitbucketRefNameExtractorFactory refNameExtractorFactory = new BitbucketRefNameExtractorFactory();
                String ref = branch != null ?
                        "refs/heads/" +
                        refNameExtractorFactory.forBuildType(build.getClass()).extractRefName(branch, repositoryName)
                        : null;
                build.addAction(new BitbucketRevisionAction(ref, env.get(GitSCM.GIT_COMMIT), bitbucketSCM.getServerId()));
                buildStatusPoster.postBuildStatus(build, listener);
            }
        }
    }
}

