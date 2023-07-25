package com.atlassian.bitbucket.jenkins.internal.scm;

import com.cloudbees.plugins.credentials.Credentials;
import hudson.model.TaskListener;
import jenkins.scm.api.*;
import jenkins.scm.api.trait.SCMSourceContext;

import javax.annotation.CheckForNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static java.util.Objects.requireNonNull;

public class BitbucketSCMSourceContext extends SCMSourceContext<BitbucketSCMSourceContext, BitbucketSCMSourceRequest> {

    private final Credentials credentials;
    private final Collection<BitbucketSCMHeadDiscoveryHandler> discoveryHandlers = new ArrayList<>();
    private final Collection<SCMHead> eventHeads;
    private final BitbucketSCMRepository repository;
    private final TaskListener listener;
    private final SCMSourceOwner owner;

    public BitbucketSCMSourceContext(@CheckForNull SCMSourceCriteria criteria,
                                     SCMHeadObserver observer,
                                     @CheckForNull Credentials credentials,
                                     Collection<SCMHead> eventHeads,
                                     BitbucketSCMRepository repository,
                                     TaskListener listener,
                                     SCMSourceOwner owner) {
        super(criteria, observer);
        this.credentials = credentials;
        this.eventHeads = requireNonNull(eventHeads, "eventHeads");
        this.repository = requireNonNull(repository, "repository");
        this.listener = requireNonNull(listener, "listener");
        this.owner = requireNonNull(owner, "owner");
    }

    @CheckForNull
    public Credentials getCredentials() {
        return credentials;
    }

    public Collection<BitbucketSCMHeadDiscoveryHandler> getDiscoveryHandlers() {
        return Collections.unmodifiableCollection(discoveryHandlers);
    }

    public Collection<SCMHead> getEventHeads() {
        return Collections.unmodifiableCollection(eventHeads);
    }

    public SCMSourceOwner getOwner() {
        return owner;
    }

    @Override
    public BitbucketSCMSourceRequest newRequest(SCMSource source, @CheckForNull TaskListener listener) {
        return new BitbucketSCMSourceRequest(source, this, listener);
    }

    public BitbucketSCMRepository getRepository() {
        return repository;
    }

    public TaskListener getTaskListener() {
        return listener;
    }

    public void withDiscoveryHandler(BitbucketSCMHeadDiscoveryHandler handler) {
        discoveryHandlers.add(requireNonNull(handler, "handler"));
    }
}
