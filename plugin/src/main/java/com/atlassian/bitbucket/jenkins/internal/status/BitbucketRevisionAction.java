package com.atlassian.bitbucket.jenkins.internal.status;

import hudson.model.Action;

import javax.annotation.CheckForNull;

public class BitbucketRevisionAction implements Action {

    private final String revisionSha1;
    private final String serverId;

    public BitbucketRevisionAction(String revisionSha1, String serverId) {
        this.revisionSha1 = revisionSha1;
        this.serverId = serverId;
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

    public String getRevisionSha1() {
        return revisionSha1;
    }

    public String getServerId() {
        return serverId;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return null;
    }
}
