package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Project;
import hudson.util.FormValidation;
import jenkins.model.TransientActionFactory;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

@Extension
public class BitbucketLinkActionFactory extends TransientActionFactory<Project> {

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(@Nonnull Project target) {
        if (!(target.getScm() instanceof BitbucketSCM)) {
            return Collections.emptySet();
        }
        BitbucketSCM bitbucketSCM = (BitbucketSCM) target.getScm();
        BitbucketSCM.DescriptorImpl descriptor = (BitbucketSCM.DescriptorImpl) bitbucketSCM.getDescriptor();
        Optional<BitbucketServerConfiguration> maybeConfig = descriptor.getConfiguration(bitbucketSCM.getServerId());

        FormValidation configValid = FormValidation.aggregate(Arrays.asList(
                maybeConfig.map(config -> config.validate()).orElse(FormValidation.error("Config not present")),
                descriptor.doCheckProjectName(bitbucketSCM.getServerId(), bitbucketSCM.getCredentialsId(),
                        bitbucketSCM.getProjectName()),
                descriptor.doCheckRepositoryName(bitbucketSCM.getServerId(), bitbucketSCM.getCredentialsId(),
                        bitbucketSCM.getProjectName(), bitbucketSCM.getRepositoryName())
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
    public Class<Project> type() {
        return Project.class;
    }
}
