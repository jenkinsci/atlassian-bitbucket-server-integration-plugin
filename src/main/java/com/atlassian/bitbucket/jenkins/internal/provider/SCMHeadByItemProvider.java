package com.atlassian.bitbucket.jenkins.internal.provider;

import com.google.inject.ImplementedBy;
import hudson.model.Item;
import jenkins.scm.api.SCMHead;

import javax.annotation.CheckForNull;

@ImplementedBy(DefaultSCMHeadByItemProvider.class)
public interface SCMHeadByItemProvider {
    
    @CheckForNull
    SCMHead findHead(Item item);
}
