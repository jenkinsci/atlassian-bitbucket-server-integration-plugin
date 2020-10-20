package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
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
            if (workflowJob.getSCMs().stream().anyMatch(scm -> scm instanceof BitbucketSCM)) {
                return workflowJob.getSCMs()
                        .stream()
                        .filter(scm -> scm instanceof BitbucketSCM)
                        .map(scm -> ((BitbucketSCM) scm).getBitbucketSCMRepository())
                        .findFirst();
            }
            // Mutlibranch Pipeline Job built with the SCM Source
            if (workflowJob.getParent() instanceof WorkflowMultiBranchProject) {
                return ((WorkflowMultiBranchProject) workflowJob.getParent()).getSCMSources().stream()
                        .filter(scmSource -> scmSource instanceof BitbucketSCMSource)
                        .map(scmSource -> ((BitbucketSCMSource) scmSource).getBitbucketSCMRepository())
                        .findFirst();
            }
        }
        return Optional.empty();
    }
}
