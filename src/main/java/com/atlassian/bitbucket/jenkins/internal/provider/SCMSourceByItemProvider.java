package com.atlassian.bitbucket.jenkins.internal.provider;

import com.google.inject.ImplementedBy;
import hudson.model.Item;
import jenkins.scm.api.SCMSource;

import javax.annotation.CheckForNull;

@ImplementedBy(DefaultSCMSourceByItemProvider.class)
public interface SCMSourceByItemProvider {
    
    @CheckForNull
    SCMSource findSource(Item item);
}
