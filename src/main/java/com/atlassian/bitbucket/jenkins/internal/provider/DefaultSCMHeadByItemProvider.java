package com.atlassian.bitbucket.jenkins.internal.provider;

import hudson.model.Item;
import jenkins.scm.api.SCMHead;

import javax.annotation.CheckForNull;
import javax.inject.Singleton;

@Singleton
public class DefaultSCMHeadByItemProvider implements SCMHeadByItemProvider {

    @CheckForNull
    @Override
    public SCMHead findHead(Item item) {
        return SCMHead.HeadByItem.findHead(item);
    }
}
