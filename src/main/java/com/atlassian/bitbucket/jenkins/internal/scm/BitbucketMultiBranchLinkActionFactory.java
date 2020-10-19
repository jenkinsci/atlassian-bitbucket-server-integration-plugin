package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import hudson.Extension;
import hudson.model.Action;
import hudson.util.FormValidation;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import java.util.*;

@Extension
public class BitbucketMultiBranchLinkActionFactory extends TransientActionFactory<WorkflowMultiBranchProject> {

    @Override
    public Collection<? extends Action> createFor(WorkflowMultiBranchProject target) {
        Optional<BitbucketSCMSource> maybeSource = getBitbucketSCMSource(target);
        if (!maybeSource.isPresent()) {
            return Collections.emptySet();
        }

        BitbucketSCMSource bitbucketSCM = maybeSource.get();
        BitbucketSCMSource.DescriptorImpl descriptor = (BitbucketSCMSource.DescriptorImpl) bitbucketSCM.getDescriptor();
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
    public Class<WorkflowMultiBranchProject> type() {
        return WorkflowMultiBranchProject.class;
    }

    private Optional<BitbucketSCMSource> getBitbucketSCMSource(WorkflowMultiBranchProject project) {
        return project.getSCMSources().stream()
                .filter(source -> source instanceof BitbucketSCMSource)
                .map(source -> (BitbucketSCMSource) source)
                .findAny();
    }
}
