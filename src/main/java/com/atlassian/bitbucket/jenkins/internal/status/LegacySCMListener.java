package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.*;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject;
import jenkins.triggers.SCMTriggerItem;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Jobs that have not been saved since updating to 3.1 will not have the new {@link BitbucketSCMRevisionAction} on them,
 * so this class:
 * <ul>
 *     <li>
 *         Extracts the information needed to create a {@link BitbucketSCMRevisionAction} and adds it to the job
 *     </li>
 *     <li>
 *         Sends the in-progress build notification, since {@link BitbucketSCMRevisionAction} will not be on the build
 *         at the time {@link BuildStatusSCMListener#onCheckout(Run, SCM, FilePath, TaskListener, File, SCMRevisionState)}
 *         is called (for jobs that have not been updated).
 *     </li>
 * </ul>
 *
 * @deprecated in 3.1.0 for removal in 4.0.0
 */
@Deprecated
@Extension
public class LegacySCMListener extends SCMListener {

    @Inject
    private BuildStatusPoster buildStatusPoster;

    public LegacySCMListener() {
    }

    LegacySCMListener(BuildStatusPoster buildStatusPoster) {
        this.buildStatusPoster = buildStatusPoster;
    }

    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener,
                           @CheckForNull File changelogFile,
                           @CheckForNull SCMRevisionState pollingBaseline) {
        BitbucketSCMRevisionAction repoAction = build.getAction(BitbucketSCMRevisionAction.class);
        if (repoAction != null) {
            // Not a legacy build
            return;
        }

        if (!(scm instanceof GitSCM || scm instanceof BitbucketSCM)) {
            return;
        }

        //case 1 - bb_checkout step in the script (pipeline or groovy)
        if (scm instanceof BitbucketSCM) {
            handleBitbucketSCMCheckout(build, (BitbucketSCM) scm, listener);
            return;
        }

        if (isWorkflowRun(build)) {
            // Case 2 - Script does not have explicit checkout statement. Proceed to inspect SCM on item
            Job<?, ?> job = build.getParent();
            GitSCM gitScm = (GitSCM) scm;
            ItemGroup<?> parent = getJobParent(job);
            if (parent instanceof MultiBranchProject) { // Case 2.1 - Multi branch workflow job
                MultiBranchProject<?, ?> multiBranchProject = (MultiBranchProject<?, ?>) parent;
                multiBranchProject
                        .getSources()
                        .stream()
                        .map(BranchSource::getSource)
                        .filter(BitbucketSCMSource.class::isInstance)
                        .map(BitbucketSCMSource.class::cast)
                        .filter(bbsSource ->
                                filterSource(gitScm, bbsSource))
                        .findFirst()
                        .ifPresent(scmSource ->
                                handleCheckout(scmSource.getBitbucketSCMRepository(), gitScm, build, listener));
            } else { // Case 2.2 - Part of pipeline run
                // Handle only SCM jobs.
                if (job instanceof SCMTriggerItem) {
                    SCMTriggerItem scmItem = (SCMTriggerItem) job;
                    scmItem.getSCMs()
                            .stream()
                            .filter(BitbucketSCM.class::isInstance)
                            .map(BitbucketSCM.class::cast)
                            .filter(bScm -> {
                                GitSCM bGitScm = bScm.getGitSCM();
                                return bGitScm != null &&
                                       Objects.equals(bGitScm.getKey(), scm.getKey());
                            })
                            .findFirst()
                            .ifPresent(bScm -> handleCheckout(bScm, gitScm, build, listener));
                }
            }
        }
    }

    @VisibleForTesting
    ItemGroup<?> getJobParent(Job<?, ?> job) {
        return job.getParent();
    }

    @VisibleForTesting
    boolean isWorkflowRun(Run<?, ?> build) {
        return build instanceof WorkflowRun;
    }

    /**
     * The assumption is the remote URL specified in GitSCM should be same as remote URL specified in
     * Bitbucket Source.
     */
    @VisibleForTesting
    boolean filterSource(GitSCM gitScm, BitbucketSCMSource bbsSource) {
        return gitScm.getUserRemoteConfigs()
                .stream()
                .anyMatch(userRemoteConfig ->
                        Objects.equals(userRemoteConfig.getUrl(), bbsSource.getRemote()));
    }

    private void handleBitbucketSCMCheckout(Run<?, ?> build, BitbucketSCM scm, TaskListener listener) {
        if (scm.getServerId() != null) {
            GitSCM gitSCM = scm.getGitSCM();
            if (gitSCM != null) {
                handleCheckout(scm, gitSCM, build, listener);
            }
        }
    }

    private void handleCheckout(BitbucketSCM bitbucketScm,
                                GitSCM underlyingScm,
                                Run<?, ?> build,
                                TaskListener listener) {
        handleCheckout(bitbucketScm.getBitbucketSCMRepository(), underlyingScm, build, listener);
    }

    private void handleCheckout(BitbucketSCMRepository bitbucketSCMRepository,
                                GitSCM underlyingScm,
                                Run<?, ?> build,
                                TaskListener listener) {
        Map<String, String> env = new HashMap<>();
        underlyingScm.buildEnvironment(build, env);

        String branch = env.get(GitSCM.GIT_BRANCH);
        String refName = branch != null ? underlyingScm.deriveLocalBranchName(branch) : null;
        BitbucketSCMRevisionAction revisionAction = new BitbucketSCMRevisionAction(bitbucketSCMRepository, refName, env.get(GitSCM.GIT_COMMIT));
        build.addAction(revisionAction);
        buildStatusPoster.postBuildStatus(revisionAction, build, listener);
    }
}
