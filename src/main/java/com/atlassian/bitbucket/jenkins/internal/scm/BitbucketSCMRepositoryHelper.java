package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject;
import jenkins.triggers.SCMTriggerItem;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.CheckForNull;
import javax.inject.Singleton;
import java.util.Objects;

/**
 * Class to help get the {@link BitbucketSCMRepository} from a run.
 */
@Singleton
public class BitbucketSCMRepositoryHelper {

    @CheckForNull
    public BitbucketSCMRepository getRepository(Run<?, ?> build, SCM scm) {
        if (!(scm instanceof GitSCM || scm instanceof BitbucketSCM)) {
            return null;
        }

        //case 1 - bb_checkout step in the script (pipeline or groovy)
        if (scm instanceof BitbucketSCM) {
            return ((BitbucketSCM) scm).getBitbucketSCMRepository();
        }

        if (isWorkflowRun(build)) {
            // Case 2 - Script does not have explicit checkout statement. Proceed to inspect SCM on item
            Job<?, ?> job = build.getParent();
            GitSCM gitScm = (GitSCM) scm;
            ItemGroup<?> parent = getJobParent(job);
            if (parent instanceof MultiBranchProject) { // Case 2.1 - Multi branch workflow job
                return ((MultiBranchProject<?, ?>) parent)
                        .getSources()
                        .stream()
                        .map(BranchSource::getSource)
                        .filter(BitbucketSCMSource.class::isInstance)
                        .map(BitbucketSCMSource.class::cast)
                        .filter(bbsSource ->
                                filterSource(gitScm, bbsSource))
                        .findFirst()
                        .map(BitbucketSCMSource::getBitbucketSCMRepository)
                        .orElse(null);
            } else { // Case 2.2 - Part of pipeline run
                // Handle only SCM jobs.
                if (job instanceof SCMTriggerItem) {
                    SCMTriggerItem scmItem = (SCMTriggerItem) job;
                    return scmItem.getSCMs()
                            .stream()
                            .filter(BitbucketSCM.class::isInstance)
                            .map(BitbucketSCM.class::cast)
                            .filter(bScm -> {
                                GitSCM bGitScm = bScm.getGitSCM();
                                return bGitScm != null &&
                                        Objects.equals(bGitScm.getKey(), scm.getKey());
                            })
                            .findFirst()
                            .map(BitbucketSCM::getBitbucketSCMRepository)
                            .orElse(null);
                }
            }
        }
        return null;
    }

    ItemGroup<?> getJobParent(Job<?, ?> job) {
        return job.getParent();
    }

    boolean isWorkflowRun(Run<?, ?> build) {
        return build instanceof WorkflowRun;
    }

    /**
     * The assumption is the remote URL specified in GitSCM should be same as remote URL specified in
     * Bitbucket Source.
     */
    boolean filterSource(GitSCM gitScm, BitbucketSCMSource bbsSource) {
        return gitScm.getUserRemoteConfigs()
                .stream()
                .anyMatch(userRemoteConfig ->
                        Objects.equals(userRemoteConfig.getUrl(), bbsSource.getRemote()));
    }
}
