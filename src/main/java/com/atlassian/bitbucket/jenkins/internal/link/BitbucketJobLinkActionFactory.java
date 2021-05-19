package com.atlassian.bitbucket.jenkins.internal.link;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.scm.SCM;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

@Extension
public class BitbucketJobLinkActionFactory extends TransientActionFactory<Job> {

    @Inject
    private BitbucketExternalLinkUtils externalLinkUtils;

    public BitbucketJobLinkActionFactory() { }

    public BitbucketJobLinkActionFactory(BitbucketExternalLinkUtils externalLinkUtils) {
        this.externalLinkUtils = externalLinkUtils;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(@Nonnull Job target) {
        // Freestyle Job
        if (target instanceof FreeStyleProject) {
            FreeStyleProject freeStyleProject = (FreeStyleProject) target;
            if (freeStyleProject.getScm() instanceof BitbucketSCM) {
                BitbucketSCMRepository scmRepository = ((BitbucketSCM) freeStyleProject.getScm()).getBitbucketSCMRepository();
                return externalLinkUtils.createRepoLink(scmRepository)
                        .map(Arrays::asList)
                        .orElse(Collections.emptyList());
            }
        } else if (target instanceof WorkflowJob) {
            // Pipeline Job
            WorkflowJob workflowJob = (WorkflowJob) target;
            if (workflowJob.getDefinition() instanceof CpsScmFlowDefinition) {
                CpsScmFlowDefinition definition = (CpsScmFlowDefinition) workflowJob.getDefinition();
                if (definition.getScm() instanceof BitbucketSCM) {
                    BitbucketSCMRepository scmRepository = ((BitbucketSCM) definition.getScm()).getBitbucketSCMRepository();
                    return externalLinkUtils.createRepoLink(scmRepository)
                            .map(Arrays::asList)
                            .orElse(Collections.emptyList());
                }
            }
            // Multibranch Pipeline Job built with an SCMStep
            if (getWorkflowSCMs(workflowJob).stream().anyMatch(scm -> scm instanceof BitbucketSCM)) {
                Optional<BitbucketSCMRepository> maybeRepository = getWorkflowSCMs(workflowJob)
                        .stream()
                        .filter(scm -> scm instanceof BitbucketSCM)
                        .map(scm -> ((BitbucketSCM) scm).getBitbucketSCMRepository())
                        .findFirst();
                return maybeRepository.flatMap(scmRepository -> externalLinkUtils.createBranchDiffLink(scmRepository, target.getName()))
                        .map(Arrays::asList)
                        .orElse(Collections.emptyList());
            }
            // Multibranch Pipeline Job built with the SCM Source
            if (getWorkflowParent(workflowJob) instanceof WorkflowMultiBranchProject) {
                Optional<BitbucketSCMRepository> maybeRepository = ((WorkflowMultiBranchProject) getWorkflowParent(workflowJob))
                        .getSCMSources().stream()
                        .filter(scmSource -> scmSource instanceof BitbucketSCMSource)
                        .map(scmSource -> ((BitbucketSCMSource) scmSource).getBitbucketSCMRepository())
                        .findFirst();
                return maybeRepository.flatMap(scmRepository -> externalLinkUtils.createBranchDiffLink(scmRepository, target.getName()))
                        .map(Arrays::asList)
                        .orElse(Collections.emptyList());
            }
        }
        // If the job doesn't have a valid scm repository, it shouldn't have a bitbucket link
        return Collections.emptySet();
    }

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @VisibleForTesting
    ItemGroup getWorkflowParent(WorkflowJob job) {
        return job.getParent();
    }

    @VisibleForTesting
    Collection<? extends SCM> getWorkflowSCMs(WorkflowJob job) {
        return job.getSCMs();
    }
}
