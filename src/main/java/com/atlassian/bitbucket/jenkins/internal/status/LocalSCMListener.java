package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketRefNameExtractorFactory;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
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
import jenkins.branch.MultiBranchProject;
import jenkins.triggers.SCMTriggerItem;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Extension
public class LocalSCMListener extends SCMListener {

    @Inject
    private BuildStatusPoster buildStatusPoster;

    public LocalSCMListener() {
    }

    LocalSCMListener(BuildStatusPoster buildStatusPoster) {
        this.buildStatusPoster = buildStatusPoster;
    }

    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener,
                           @CheckForNull File changelogFile,
                           @CheckForNull SCMRevisionState pollingBaseline) {
        if (!(scm instanceof GitSCM || scm instanceof BitbucketSCM)) {
            return;
        }

        //case 1 - bb_checkout step in the script (pipeline or groovy)
        if (scm instanceof BitbucketSCM) {
            handleBitbucketSCMCheckout(build, scm, listener);
            return;
        }

        if (isWorkflowRun(build)) {
            //case 2 - Script does not have explicit checkout statement. Proceed to inspect SCM on item
            Job<?, ?> job = build.getParent();
            ItemGroup parent = job.getParent();
            //Case 2.1 - Multi branch workflow job
            if (parent != null && parent instanceof MultiBranchProject) {
                MultiBranchProject<?, ?> multiBranchProject = (MultiBranchProject<?, ?>) parent;
                GitSCM gitScm = (GitSCM) scm;
                multiBranchProject
                        .getSources()
                        .stream()
                        .map(branchSource -> branchSource.getSource())
                        .filter(s -> s instanceof BitbucketSCMSource)
                        .map(BitbucketSCMSource.class::cast)
                        .filter(bbsSource ->
                                filterSource(gitScm, bbsSource))
                        .findFirst()
                        .ifPresent(s -> handleCheckout(s.getBitbucketSCMRepository(), gitScm, build, listener));
            } else { // Case 2.2 - Part of pipeline run
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
            }
        }
    }

    boolean isWorkflowRun(Run<?, ?> build) {
        return build instanceof WorkflowRun;
    }

    /**
     * The assumption is the remote URL specified in GitSCM should be same as remote URL specified in
     * Bitbucket Source.
     */
    private boolean filterSource(GitSCM gitScm, BitbucketSCMSource bbsSource) {
        return gitScm.getUserRemoteConfigs().stream().anyMatch(userRemoteConfig -> userRemoteConfig.getUrl().equals(bbsSource.getRemote()));
    }

    private void handleBitbucketSCMCheckout(Run<?, ?> build, SCM scm, TaskListener listener) {
        BitbucketSCM bitbucketSCM = scm instanceof BitbucketSCM ? (BitbucketSCM) scm : null;
        if (bitbucketSCM != null && bitbucketSCM.getServerId() != null) {
            GitSCM gitSCM = ((BitbucketSCM) scm).getGitSCM();
            if (gitSCM != null) {
                handleCheckout(bitbucketSCM, gitSCM, build, listener);
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

        String repositoryName = bitbucketSCMRepository.getRepositorySlug();
        String branch = env.get(GitSCM.GIT_BRANCH);
        BitbucketRefNameExtractorFactory refNameExtractorFactory = new BitbucketRefNameExtractorFactory();
        String branchName = branch != null ?
                refNameExtractorFactory.forBuildType(build.getClass()).extractRefName(branch, repositoryName)
                : null;
        BitbucketRevisionAction revisionAction =
                new BitbucketRevisionAction(bitbucketSCMRepository, branchName, env.get(GitSCM.GIT_COMMIT));
        build.addAction(revisionAction);
        buildStatusPoster.postBuildStatus(revisionAction, build, listener);
    }
}
