package com.atlassian.bitbucket.jenkins.internal.scm.extensions;

import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import com.atlassian.bitbucket.jenkins.internal.status.BuildStatusPoster;
import com.google.inject.Injector;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.util.BuildData;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;

public class BitbucketPostBuildStatus extends GitSCMExtension {

    private final String serverId;

    public BitbucketPostBuildStatus(String serverId) {
        this.serverId = serverId;
    }

    @Override
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener,
                                        CheckoutCommand cmd) throws GitException {
        Injector injector = Jenkins.get().getInjector();
        if (injector == null) {
            return;
        }

        if (build instanceof AbstractBuild) {
            BuildData buildData = scm.getBuildData(build);
            if (buildData != null) {
                String revisionSha1 = buildData.lastBuild.getRevision().getSha1String();
                build.addAction(new BitbucketRevisionAction(revisionSha1, serverId));
            }
            BuildStatusPoster poster = injector.getInstance(BuildStatusPoster.class);
            if (poster != null) {
                poster.postBuildStatus((AbstractBuild) build, listener);
            }
        }
    }
}
