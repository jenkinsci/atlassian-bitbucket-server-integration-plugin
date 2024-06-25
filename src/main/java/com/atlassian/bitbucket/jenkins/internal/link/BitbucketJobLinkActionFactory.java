package com.atlassian.bitbucket.jenkins.internal.link;

import com.atlassian.bitbucket.jenkins.internal.provider.DefaultSCMHeadByItemProvider;
import com.atlassian.bitbucket.jenkins.internal.provider.DefaultSCMSourceByItemProvider;
import com.atlassian.bitbucket.jenkins.internal.provider.SCMHeadByItemProvider;
import com.atlassian.bitbucket.jenkins.internal.provider.SCMSourceByItemProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.*;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.scm.SCM;
import jenkins.model.TransientActionFactory;
import jenkins.scm.api.SCMHead;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Stream;

@Extension
public class BitbucketJobLinkActionFactory extends TransientActionFactory<Job> {

    @Inject
    private BitbucketExternalLinkUtils externalLinkUtils;
    @Inject
    private SCMHeadByItemProvider headProvider;
    @Inject
    private SCMSourceByItemProvider sourceProvider;

    public BitbucketJobLinkActionFactory() { }

    public BitbucketJobLinkActionFactory(BitbucketExternalLinkUtils externalLinkUtils,
                                         DefaultSCMHeadByItemProvider headProvider,
                                         DefaultSCMSourceByItemProvider sourceProvider) {
        this.externalLinkUtils = externalLinkUtils;
        this.headProvider = headProvider;
        this.sourceProvider = sourceProvider;
    }

    @Override
    public Collection<? extends Action> createFor(Job target) {
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
                SCMHead head = headProvider.findHead(workflowJob);
                if (head == null) {
                    return Collections.emptyList();
                }

                Optional<BitbucketSCMRepository> repository = Stream.of(getScmSource(workflowJob), getScmStep(workflowJob))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst();

                Optional<BitbucketExternalLink> externalLink;
                if (head instanceof BitbucketPullRequestSCMHead) {
                    externalLink = repository.flatMap(scmRepository ->
                            externalLinkUtils.createPullRequestLink(scmRepository,
                                    ((BitbucketPullRequestSCMHead) head).getId()));
                } else if (head instanceof BitbucketTagSCMHead) {
                    externalLink = repository.flatMap(scmRepository ->
                            externalLinkUtils.createTagDiffLink(scmRepository, head.getName()));
                } else {
                    externalLink = repository.flatMap(scmRepository ->
                            externalLinkUtils.createBranchDiffLink(scmRepository, head.getName()));
                }

                return externalLink.map(Collections::singletonList).orElse(Collections.emptyList());
            }
            // Pipeline Job built with an SCMStep
            if (getWorkflowSCMs(workflowJob).stream().anyMatch(BitbucketSCM.class::isInstance)) {
                return getScmStep(workflowJob)
                        .flatMap(scmRepository -> externalLinkUtils.createRepoLink(scmRepository))
                        .map(Collections::singletonList)
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
        return Optional.ofNullable(sourceProvider.findSource(workflowJob))
                .filter(BitbucketSCMSource.class::isInstance)
                .map(scmSource -> ((BitbucketSCMSource) scmSource).getBitbucketSCMRepository());
    }
}
