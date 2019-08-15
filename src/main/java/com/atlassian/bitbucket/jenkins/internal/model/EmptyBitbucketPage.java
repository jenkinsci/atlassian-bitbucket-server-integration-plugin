package com.atlassian.bitbucket.jenkins.internal.model;

public final class EmptyBitbucketPage<T> extends BitbucketPage<T> {

    @Override
    public boolean isLastPage() {
        return true;
    }
}
