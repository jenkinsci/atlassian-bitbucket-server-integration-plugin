package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRevisionAction;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.File;

@Extension
public class BuildStatusSCMListener extends SCMListener {

    private BuildStatusPoster buildStatusPoster;

    public BuildStatusSCMListener() {
    }

    @Inject
    BuildStatusSCMListener(BuildStatusPoster buildStatusPoster) {
        this.buildStatusPoster = buildStatusPoster;
    }

    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener,
                           @CheckForNull File changelogFile,
                           @CheckForNull SCMRevisionState pollingBaseline) {
        BitbucketSCMRevisionAction bitbucketRevisionAction = build.getAction(BitbucketSCMRevisionAction.class);
        if (bitbucketRevisionAction == null) {
            // Not a Bitbucket SCM (or a legacy build, which will be handled by LegacySCMListener)
            return;
        }
        buildStatusPoster.postBuildStatus(bitbucketRevisionAction, build, listener);
    }
}
