package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.extensions.GitSCMExtension;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BitbucketPRBranchNameDecorator extends GitSCMExtension {

    private final BitbucketPullRequestSCMHead pullRequestSCMHead;

    public BitbucketPRBranchNameDecorator(BitbucketPullRequestSCMHead pullRequestSCMHead) {
        this.pullRequestSCMHead = pullRequestSCMHead;
    }

    @Override
    public void populateEnvironmentVariables(GitSCM scm, Map<String, String> env) {
        env.put(GitSCM.GIT_BRANCH, pullRequestSCMHead.getOriginName());
    }

    @Override
    public Revision decorateRevisionToBuild(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, Revision marked, Revision rev) throws GitException {
        List<Branch> branches = rev.getBranches().stream()
                .map(branch -> new Branch(pullRequestSCMHead.getOriginName(), branch.getSHA1()))
                .collect(Collectors.toList());
        rev.setBranches(branches);
        return rev;
    }
}
