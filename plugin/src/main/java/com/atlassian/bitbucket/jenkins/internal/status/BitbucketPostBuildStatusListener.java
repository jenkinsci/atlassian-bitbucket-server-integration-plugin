package com.atlassian.bitbucket.jenkins.internal.status;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import javax.inject.Inject;

import static com.atlassian.bitbucket.jenkins.internal.scm.BitbucketScmRunHelper.hasBitbucketScmOrBitbucketScmSource;

@SuppressWarnings("unused")
@Extension
public class BitbucketPostBuildStatusListener<R extends Run<?, ?>> extends RunListener<R> {

    @Inject
    private BuildStatusPoster buildStatusPoster;

    @Override
    public void onCompleted(R run, TaskListener listener) {
        if (hasBitbucketScmOrBitbucketScmSource(run)) {
            buildStatusPoster.postBuildStatus(run, listener);
        }
    }
}
