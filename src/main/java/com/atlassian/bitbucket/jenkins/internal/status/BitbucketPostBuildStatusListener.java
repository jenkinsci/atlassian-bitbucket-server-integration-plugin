package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import javax.inject.Inject;

@Extension
public class BitbucketPostBuildStatusListener<R extends Run> extends RunListener<R> {

    @Inject
    private BuildStatusPoster buildStatusPoster;

    @Override
    public void onCompleted(R r, TaskListener listener) {
        if (!(r instanceof AbstractBuild)) {
            return;
        }

        AbstractBuild build = (AbstractBuild) r;
        if (!(build.getProject().getScm() instanceof BitbucketSCM)) {
            return;
        }
        buildStatusPoster.postBuildStatus(build, listener);
    }
}
