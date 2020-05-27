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
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketRefNameExtractorFactory;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMException;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import jenkins.triggers.SCMTriggerItem;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class BuildStatusPoster<R extends Run<?, ?>> extends RunListener<R> {

    private static final String BUILD_STATUS_ERROR_MSG = "Failed to post build status, additional information:";
    private static final String BUILD_STATUS_FORMAT = "Posting build status of %s to %s for commit id [%s]";
    private static final Logger LOGGER = Logger.getLogger(BuildStatusPoster.class.getName());
    private static final String NO_SERVER_MSG =
            "Failed to post build status as the provided Bitbucket Server config does not exist";

    @Inject
    BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    @Inject
    private BitbucketPluginConfiguration pluginConfiguration;
    @Inject
    private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

    @Override
    public void onCompleted(R r, @Nonnull TaskListener listener) {
        BitbucketRevisionAction bitbucketRevisionAction = r.getAction(BitbucketRevisionAction.class);
        if (bitbucketRevisionAction != null) {
            postBuildStatus(bitbucketRevisionAction, r, listener);
        }
    }

    private void postBuildStatus(BitbucketRevisionAction revisionAction, Run<?, ?> run, TaskListener listener) {
        Optional<BitbucketServerConfiguration> serverOptional =
                pluginConfiguration.getServerById(revisionAction.getServerId());
        if (!serverOptional.isPresent()) {
            listener.error(NO_SERVER_MSG);
            return;
        }
        postBuildStatus(serverOptional.get(), revisionAction, run, listener);
    }

    private void postBuildStatus(BitbucketServerConfiguration server, BitbucketRevisionAction revisionAction,
                                 Run<?, ?> run, TaskListener listener) {
        GlobalCredentialsProvider globalCredentialsProvider = server.getGlobalCredentialsProvider(run.getParent());
        try {
            BitbucketClientFactory bbsClient = getBbsClient(server, globalCredentialsProvider);
            BitbucketCICapabilities ciCapabilities = bbsClient.getCapabilityClient().getCICapabilities();

            BitbucketBuildStatus buildStatus = BitbucketBuildStatusFactory.fromBuild(run, ciCapabilities);
            listener.getLogger().format(BUILD_STATUS_FORMAT, buildStatus.getState(), server.getServerName(), revisionAction.getRevisionSha1());

            bbsClient.getBuildStatusClient(revisionAction.getRevisionSha1(), revisionAction.getBitbucketSCM(), ciCapabilities)
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

    @Inject
    public void setJenkinsToBitbucketCredentials(
            JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials) {
        this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
    }

    @Extension
    public static class LocalSCMListener extends SCMListener {

        @Inject
        private BuildStatusPoster buildStatusPoster;

        @Override
        public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener,
                               @CheckForNull File changelogFile,
                               @CheckForNull SCMRevisionState pollingBaseline) {
            if (!(scm instanceof GitSCM)) {
                return;
            }

            if (build instanceof WorkflowRun) {
                //case 1 - bb_checkout step in the script
                if (scm instanceof BitbucketSCM) {
                    handleBitbucketSCMCheckout(build, scm, listener);
                    return;
                }

                //case 2 - Script does not have explicit checkout
                Job<?, ?> job = build.getParent();
                //Handle only SCM jobs.
                GitSCM gitScm = (GitSCM) scm;
                if (job instanceof SCMTriggerItem) {
                    SCMTriggerItem scmItem = (SCMTriggerItem) job;
                    scmItem.getSCMs()
                            .stream()
                            .filter(s -> s instanceof BitbucketSCM)
                            .map(s -> (BitbucketSCM) s)
                            .filter(bScm -> bScm.getGitSCM().getKey().equals(scm.getKey()))
                            .findFirst()
                            .ifPresent(bScm -> handleCheckout(bScm, gitScm, build, listener));
                }
            } else {
                handleBitbucketSCMCheckout(build, scm, listener);
            }
        }

        private void handleBitbucketSCMCheckout(Run<?, ?> build, SCM scm, TaskListener listener) {
            BitbucketSCM bitbucketSCM = scm instanceof BitbucketSCM ? (BitbucketSCM) scm : null;
            if (bitbucketSCM != null && bitbucketSCM.getServerId() != null) {
                GitSCM gitSCM = ((BitbucketSCM) scm).getGitSCM();
                handleCheckout(bitbucketSCM, gitSCM, build, listener);
            }
        }

        private void handleCheckout(BitbucketSCM bitbucketScm,
                                    GitSCM underlyingScm,
                                    Run<?, ?> build,
                                    TaskListener listener) {
            Map<String, String> env = new HashMap<>();
            underlyingScm.buildEnvironment(build, env);

            String repositoryName = (underlyingScm.getRepositories().stream().findFirst()
                    .orElseThrow(() -> new BitbucketSCMException("No repository found in the GitSCM")))
                    .getName();
            String branch = env.get(GitSCM.GIT_BRANCH);
            BitbucketRefNameExtractorFactory refNameExtractorFactory = new BitbucketRefNameExtractorFactory();
            String branchName = branch != null ?
                    refNameExtractorFactory.forBuildType(build.getClass()).extractRefName(branch, repositoryName)
                    : null;
            BitbucketRevisionAction revisionAction =
                    new BitbucketRevisionAction(bitbucketScm, branchName, env.get(GitSCM.GIT_COMMIT), bitbucketScm.getServerId());
            build.addAction(revisionAction);
            buildStatusPoster.postBuildStatus(revisionAction, build, listener);
        }
    }
}

