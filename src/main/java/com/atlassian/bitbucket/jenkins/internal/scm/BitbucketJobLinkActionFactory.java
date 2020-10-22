package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.scm.SCM;
import hudson.util.FormValidation;
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
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;
    @Inject
    private BitbucketScmFormValidationDelegate formValidation;

    public BitbucketJobLinkActionFactory() { }

    @Inject
    public BitbucketJobLinkActionFactory(BitbucketPluginConfiguration bitbucketPluginConfiguration,
                                         BitbucketScmFormValidationDelegate formValidation) {
        this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
        this.formValidation = formValidation;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(@Nonnull Job target) {
        Optional<BitbucketSCMRepository> maybeRepository = getBitbucketSCMRepository(target);
        if (!maybeRepository.isPresent()) {
            return Collections.emptySet();
        }
        BitbucketSCMRepository bitbucketRepository = maybeRepository.get();
        String serverId = Objects.toString(bitbucketRepository.getServerId(), "");
        String credentialsId = Objects.toString(bitbucketRepository.getCredentialsId(), "");

        Optional<BitbucketServerConfiguration> maybeConfig = bitbucketPluginConfiguration.getServerById(serverId);
        FormValidation configValid = FormValidation.aggregate(Arrays.asList(
                maybeConfig.map(BitbucketServerConfiguration::validate).orElse(FormValidation.error("Config not present")),
                formValidation.doCheckProjectName(target, serverId, credentialsId, bitbucketRepository.getProjectName()),
                formValidation.doCheckRepositoryName(target, serverId, credentialsId, bitbucketRepository.getProjectName(), bitbucketRepository.getRepositoryName())
        ));

        if (configValid.kind == FormValidation.Kind.ERROR) {
            return Collections.emptySet();
        }

        String url = maybeConfig.get().getBaseUrl() +
                     "/projects/" +
                     bitbucketRepository.getProjectKey() +
                     "/repos/" +
                     bitbucketRepository.getRepositorySlug();
        return Collections.singleton(BitbucketExternalLink.createDashboardLink(url, target));
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

    private Optional<BitbucketSCMRepository> getBitbucketSCMRepository(Job job) {
        // Freestyle Job
        if (job instanceof FreeStyleProject) {
            FreeStyleProject freeStyleProject = (FreeStyleProject) job;
            if (freeStyleProject.getScm() instanceof BitbucketSCM) {
                return Optional.of(((BitbucketSCM) freeStyleProject.getScm()).getBitbucketSCMRepository());
            }
        } else if (job instanceof WorkflowJob) {
            // Pipeline Job
            WorkflowJob workflowJob = (WorkflowJob) job;
            if (workflowJob.getDefinition() instanceof CpsScmFlowDefinition) {
                CpsScmFlowDefinition definition = (CpsScmFlowDefinition) workflowJob.getDefinition();
                if (definition.getScm() instanceof BitbucketSCM) {
                    return Optional.of(((BitbucketSCM) definition.getScm()).getBitbucketSCMRepository());
                }
            }
            // Multibranch Pipeline Job built with an SCMStep
            if (getWorkflowSCMs(workflowJob).stream().anyMatch(scm -> scm instanceof BitbucketSCM)) {
                return getWorkflowSCMs(workflowJob)
                        .stream()
                        .filter(scm -> scm instanceof BitbucketSCM)
                        .map(scm -> ((BitbucketSCM) scm).getBitbucketSCMRepository())
                        .findFirst();
            }
            // Multibranch Pipeline Job built with the SCM Source
            if (getWorkflowParent(workflowJob) instanceof WorkflowMultiBranchProject) {
                return ((WorkflowMultiBranchProject) getWorkflowParent(workflowJob)).getSCMSources().stream()
                        .filter(scmSource -> scmSource instanceof BitbucketSCMSource)
                        .map(scmSource -> ((BitbucketSCMSource) scmSource).getBitbucketSCMRepository())
                        .findFirst();
            }
        }
        return Optional.empty();
    }
}
