package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.Run;
import hudson.model.TaskListener;

public interface BitbucketSCMCheckoutListener {

    void onCheckout(Run<?, ?> build, TaskListener listener);
}
