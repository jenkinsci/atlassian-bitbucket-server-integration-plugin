package com.atlassian.bitbucket.jenkins.internal.status;

import hudson.model.Action;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class BitbucketRevisionAction implements Action {

    private final String ref;
    private final String revisionSha1;
    private final String serverId;

    public BitbucketRevisionAction(@Nullable String ref, String revisionSha1, String serverId) {
        this.ref = ref;
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

    @CheckForNull
    public String getRefName() {
        return ref;
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
