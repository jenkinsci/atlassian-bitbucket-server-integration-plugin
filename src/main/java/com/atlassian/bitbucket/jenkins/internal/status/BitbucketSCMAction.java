package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import hudson.model.Action;

import javax.annotation.CheckForNull;

/**
 * Holds the Bitbucket SCM that was used to checkout the sources. This is needed when build status is posted to find
 * the project key and repository slug.
 */
public class BitbucketSCMAction implements Action {

    private final BitbucketSCM bitbucketSCM;

    public BitbucketSCMAction(BitbucketSCM scm) {
        bitbucketSCM = scm;
    }

    public BitbucketSCM getBitbucketSCM() {
        return bitbucketSCM;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return null;
    }
}
