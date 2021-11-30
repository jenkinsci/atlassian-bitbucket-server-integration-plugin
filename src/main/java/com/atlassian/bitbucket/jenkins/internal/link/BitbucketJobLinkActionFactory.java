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
import java.util.stream.Stream;

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
            // Pipeline Job from SCM
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
            // Multibranch Pipeline Job
            if (getWorkflowParent(workflowJob) instanceof WorkflowMultiBranchProject) {
                // Multibranch Pipeline Job from SCM Source, or if there is none, try and get it from an SCMStep
                return Stream.of(getScmSource(workflowJob), getScmStep(workflowJob))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst()
                        .flatMap(scmRepository -> externalLinkUtils.createBranchDiffLink(scmRepository, target.getName()))
                        .map(Arrays::asList)
                        .orElse(Collections.emptyList());
            }
            // Pipeline Job built with an SCMStep
            if (getWorkflowSCMs(workflowJob).stream().anyMatch(scm -> scm instanceof BitbucketSCM)) {
                return getScmStep(workflowJob)
                        .flatMap(scmRepository -> externalLinkUtils.createRepoLink(scmRepository))
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

    private Optional<BitbucketSCMRepository> getScmStep(WorkflowJob workflowJob) {
        return getWorkflowSCMs(workflowJob)
                .stream()
                .filter(scm -> scm instanceof BitbucketSCM)
                .map(scm -> ((BitbucketSCM) scm).getBitbucketSCMRepository())
                .findFirst();
    }

    private Optional<BitbucketSCMRepository> getScmSource(WorkflowJob workflowJob) {
        return ((WorkflowMultiBranchProject) getWorkflowParent(workflowJob))
                .getSCMSources().stream()
                .filter(scmSource -> scmSource instanceof BitbucketSCMSource)
                .map(scmSource -> ((BitbucketSCMSource) scmSource).getBitbucketSCMRepository())
                .findFirst();
    }
}
