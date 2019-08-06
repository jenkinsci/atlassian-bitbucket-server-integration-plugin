package com.atlassian.bitbucket.jenkins.internal.listener;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class BitbucketBuildListener<R extends Run> extends RunListener<R> {

    @Override
    public void onCompleted(R r, TaskListener listener) {
        return;
    }
}
