package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.provider.DefaultJenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import com.atlassian.bitbucket.jenkins.internal.status.BuildStatusPoster;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Injector;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.GitClient;

class BitbucketPostBuildStatus extends GitSCMExtension {

    private final JenkinsProvider jenkinsProvider;
    private final String serverId;

    @VisibleForTesting
    BitbucketPostBuildStatus(String serverId, JenkinsProvider jenkinsProvider) {
        this.serverId = serverId;
        this.jenkinsProvider = jenkinsProvider;
    }

    public BitbucketPostBuildStatus(String serverId) {
        this(serverId, new DefaultJenkinsProvider());
    }

    @Override
    public Revision decorateRevisionToBuild(GitSCM scm, Run<?, ?> run, GitClient git, TaskListener listener,
                                            Revision marked, Revision rev) throws GitException {
        Injector injector = jenkinsProvider.get().getInjector();
        if (injector == null) {
            listener.getLogger().println("Injector could not be found while creating build status");
            return rev;
        }
        BitbucketRefNameExtractorFactory refNameExtractorFactory = new BitbucketRefNameExtractorFactory();
        String repositoryName = (scm.getRepositories().stream().findFirst()
                .orElseThrow(() -> new BitbucketSCMException("No repository found in the GitSCM")))
                .getName();

        BuildData buildData = scm.getBuildData(run);

        String ref = rev.getBranches().stream()
                .map(branch -> {
                    String branchName =
                            refNameExtractorFactory.forBuildType(run.getClass())
                                    .extractRefName(branch.getName(), repositoryName);
                    return "refs/heads/" + branchName;
                })
                .findFirst()
                .orElse(null);
        if (buildData == null || buildData.lastBuild == null) {
            run.addAction(new BitbucketRevisionAction(ref, rev.getSha1String(), serverId));
        } else {
            run.addAction(new BitbucketRevisionAction(ref, buildData.lastBuild.getRevision().getSha1String(), serverId));
        }
        BuildStatusPoster poster = injector.getInstance(BuildStatusPoster.class);
        if (poster != null) {
            poster.postBuildStatus(run, listener);
        } else {
            listener.getLogger().println("Build Status Poster instance could not be found while creating a build status");
        }

        return rev;
    }
}
