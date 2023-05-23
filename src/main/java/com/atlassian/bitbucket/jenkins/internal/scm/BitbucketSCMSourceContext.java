package com.atlassian.bitbucket.jenkins.internal.scm;

import com.cloudbees.plugins.credentials.Credentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class BitbucketSCMSourceContext extends SCMSourceContext<BitbucketSCMSourceContext, BitbucketSCMSourceRequest> {

    private final Credentials credentials;
    private final Map<BitbucketDiscoverableHeadType, BitbucketSCMHeadDiscoveryHandler> discoveryHandlers =
            new HashMap<>();
    private final BitbucketSCMRepository repository;

    private boolean needsChangeRequests;

    public BitbucketSCMSourceContext(SCMSourceCriteria criteria,
                                     @NonNull SCMHeadObserver observer,
                                     @Nullable Credentials credentials,
                                     BitbucketSCMRepository repository) {
        super(criteria, observer);
        this.credentials = credentials;
        this.repository = repository;
    }

    public Optional<Credentials> getCredentials() {
        return Optional.ofNullable(credentials);
    }

    public Map<BitbucketDiscoverableHeadType, BitbucketSCMHeadDiscoveryHandler> getDiscoveryHandlers() {
        return discoveryHandlers;
    }

    public BitbucketSCMRepository getRepository() {
        return repository;
    }

    public final boolean needsChangeRequests() {
        return needsChangeRequests;
    }

    @NonNull
    @Override
    public BitbucketSCMSourceRequest newRequest(@NonNull SCMSource source, TaskListener listener) {
        return new BitbucketSCMSourceRequest(source, this, listener);
    }

    @NonNull
    public final BitbucketSCMSourceContext wantChangeRequests() {
        needsChangeRequests = true;
        return this;
    }

    public void withDiscoveryHandler(BitbucketDiscoverableHeadType discoverableHeadType,
                                     BitbucketSCMHeadDiscoveryHandler discoveryHandler) {
        requireNonNull(discoverableHeadType, "discoverableHeadType");
        requireNonNull(discoveryHandler, "discoveryHandler");

        discoveryHandlers.put(discoverableHeadType, discoveryHandler);
    }
}
