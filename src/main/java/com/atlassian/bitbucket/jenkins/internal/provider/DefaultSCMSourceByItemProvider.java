package com.atlassian.bitbucket.jenkins.internal.provider;

import hudson.model.Item;
import jenkins.scm.api.SCMSource;

import javax.annotation.CheckForNull;
import javax.inject.Singleton;

@Singleton
public class DefaultSCMSourceByItemProvider implements SCMSourceByItemProvider{

    @CheckForNull
    @Override
    public SCMSource findSource(Item item) {
        return SCMSource.SourceByItem.findSource(item);
    }
}
