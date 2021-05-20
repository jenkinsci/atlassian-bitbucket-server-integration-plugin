package com.atlassian.bitbucket.jenkins.internal.link;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import hudson.Extension;
import hudson.model.Action;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

@Extension
public class BitbucketMultibranchLinkActionFactory extends TransientActionFactory<WorkflowMultiBranchProject> {

    @Inject
    private BitbucketExternalLinkUtils externalLinkUtils;

    public BitbucketMultibranchLinkActionFactory() { }

    public BitbucketMultibranchLinkActionFactory(BitbucketExternalLinkUtils externalLinkUtils) {
        this.externalLinkUtils = externalLinkUtils;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(@Nonnull WorkflowMultiBranchProject workflowMultiBranchProject) {
        Optional<BitbucketSCMRepository> maybeSource = workflowMultiBranchProject.getSCMSources()
                .stream().filter(source -> source instanceof BitbucketSCMSource)
                .map(source -> ((BitbucketSCMSource) source).getBitbucketSCMRepository())
                // We do not support more than one SCM Source per job, so this check is sufficient
                .findFirst();
        return maybeSource.flatMap(externalLinkUtils::createRepoLink)
                .map(Arrays::asList)
                .orElse(Collections.emptyList());
    }

    @Override
    public Class<WorkflowMultiBranchProject> type() {
        return WorkflowMultiBranchProject.class;
    }
}
