package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;

public final class BitbucketScmRunHelper {

    private BitbucketScmRunHelper() {
    }

    public static boolean hasBitbucketScmOrBitbucketScmSource(Run<?, ?> run) {
        return getBitbucketScm(run).isPresent() || getBitbucketSCMSource(run).isPresent();
    }

    private static Optional<BitbucketSCMSource> getBitbucketSCMSource(Run<?, ?> run) {
        if (run.getParent().getParent() instanceof WorkflowMultiBranchProject) {
            WorkflowMultiBranchProject project = (WorkflowMultiBranchProject) run.getParent().getParent();
            return project.getSCMSources().stream().filter(src -> src instanceof BitbucketSCMSource).map(scmSource -> (BitbucketSCMSource) scmSource).findFirst();
        }
        return empty();
    }

    private static Optional<BitbucketSCM> getBitbucketScm(Run<?, ?> run) {
        if (run instanceof AbstractBuild) {
            AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
            if (build.getProject().getScm() instanceof BitbucketSCM) {
                return of((BitbucketSCM) build.getProject().getScm());
            }
        } else if (run instanceof WorkflowRun) {
            FlowDefinition flowDefinition = ((WorkflowRun) run).getParent().getDefinition();
            if (flowDefinition instanceof CpsScmFlowDefinition) {
                CpsScmFlowDefinition scmFlowDefinition = (CpsScmFlowDefinition) flowDefinition;
                if (scmFlowDefinition.getScm() instanceof BitbucketSCM) {
                    return of((BitbucketSCM) scmFlowDefinition.getScm());
                }
            }
        }
        return empty();
    }
}
