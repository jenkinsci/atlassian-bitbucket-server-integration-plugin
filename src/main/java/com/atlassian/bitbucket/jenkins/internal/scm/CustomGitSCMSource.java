package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.TaskListener;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSourceCriteria;

import javax.annotation.CheckForNull;
import java.io.IOException;

/**
 * This class exists to work around the following issue: we do not want to re-implement the retrieve found in the
 * {@link GitSCMSource}, however it is protected so we can't access it from our class.
 * <p>
 * This class inherits from the {@link GitSCMSource} and thus can access it and expose a method wrapper.
 * ---- also need repo in select branch trait
 */
public class CustomGitSCMSource extends GitSCMSource {

    private BitbucketSCMRepository repository;

    public CustomGitSCMSource(String remote, BitbucketSCMRepository repository) {
        super(remote);
        this.repository = repository;
    }

    public void accessibleRetrieve(@CheckForNull SCMSourceCriteria criteria, SCMHeadObserver observer,
                                   @CheckForNull SCMHeadEvent<?> event,
                                   TaskListener listener) throws IOException, InterruptedException {
        super.retrieve(criteria, observer, event, listener);
    }

    public BitbucketSCMRepository getRepository() {
        return repository;
    }
}