package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.util.FormValidation;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import javax.annotation.Nonnull;
import java.util.*;

@Extension
public class BitbucketJobLinkActionFactory extends TransientActionFactory<Job> {

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(@Nonnull Job target) {
        Optional<BitbucketSCM> maybeSCM = getBitbucketSCM(target);
        if (!maybeSCM.isPresent()) {
            return Collections.emptySet();
        }
        BitbucketSCM bitbucketSCM = maybeSCM.get();
        BitbucketSCM.DescriptorImpl descriptor = (BitbucketSCM.DescriptorImpl) bitbucketSCM.getDescriptor();
        Optional<BitbucketServerConfiguration> maybeConfig = descriptor.getConfiguration(bitbucketSCM.getServerId());
        String serverId = Objects.toString(bitbucketSCM.getServerId(), "");
        String credentialsId = Objects.toString(bitbucketSCM.getCredentialsId(), "");

        FormValidation configValid = FormValidation.aggregate(Arrays.asList(
                maybeConfig.map(BitbucketServerConfiguration::validate).orElse(FormValidation.error("Config not present")),
                descriptor.doCheckProjectName(target, serverId, credentialsId, bitbucketSCM.getProjectName()),
                descriptor.doCheckRepositoryName(target, serverId, credentialsId, bitbucketSCM.getProjectName(), bitbucketSCM.getRepositoryName())
        ));

        if (configValid.kind == FormValidation.Kind.ERROR) {
            return Collections.emptySet();
        }

        String url = maybeConfig.get().getBaseUrl() +
                     "/projects/" +
                     bitbucketSCM.getProjectKey() +
                     "/repos/" +
                     bitbucketSCM.getRepositorySlug();
        return Collections.singleton(BitbucketExternalLink.createDashboardLink(url, target));
    }

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    private Optional<BitbucketSCM> getBitbucketSCM(Job job) {
        if (job instanceof FreeStyleProject) {
            FreeStyleProject freeStyleProject = (FreeStyleProject) job;
            if (freeStyleProject.getScm() instanceof BitbucketSCM) {
                return Optional.of((BitbucketSCM) freeStyleProject.getScm());
            }
        } else if (job instanceof WorkflowJob) {
            WorkflowJob workflowJob = (WorkflowJob) job;
            if (workflowJob.getDefinition() instanceof CpsScmFlowDefinition) {
                CpsScmFlowDefinition definition = (CpsScmFlowDefinition) workflowJob.getDefinition();
                if (definition.getScm() instanceof BitbucketSCM) {
                    return Optional.of((BitbucketSCM) definition.getScm());
                }
            }
        }
        return Optional.empty();
    }
}
