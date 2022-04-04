package com.atlassian.bitbucket.jenkins.internal.provider;

import com.google.inject.ImplementedBy;
import hudson.model.Item;
import jenkins.scm.api.SCMHead;

import javax.annotation.CheckForNull;

@ImplementedBy(DefaultSCMHeadByItemProvider.class)
public interface SCMHeadByItemProvider {

    /**
     * @return the result of calling SCMHead.HeadByItem.findHead(item)
     * @since 3.0.0
     */
    @CheckForNull
    SCMHead findHead(Item item);
}
