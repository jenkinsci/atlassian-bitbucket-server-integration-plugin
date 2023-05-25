package com.atlassian.bitbucket.jenkins.internal.scm;

import com.cloudbees.plugins.credentials.Credentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceContext;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class BitbucketSCMSourceContext extends SCMSourceContext<BitbucketSCMSourceContext, BitbucketSCMSourceRequest> {

    private final Credentials credentials;
    private final Map<BitbucketDiscoverableHeadType, BitbucketSCMHeadDiscoveryHandler> discoveryHandlers =
            new HashMap<>();
    private final BitbucketSCMRepository repository;

    public BitbucketSCMSourceContext(SCMSourceCriteria criteria,
                                     SCMHeadObserver observer,
                                     @Nullable Credentials credentials,
                                     BitbucketSCMRepository repository) {
        super(criteria, observer);
        this.credentials = credentials;
        this.repository = repository;
    }

    public Map<BitbucketDiscoverableHeadType, BitbucketSCMHeadDiscoveryHandler> getDiscoveryHandlers() {
        return discoveryHandlers;
    }

    @Override
    public BitbucketSCMSourceRequest newRequest(SCMSource source, @CheckForNull TaskListener listener) {
        return new BitbucketSCMSourceRequest(source, this, listener);
    }

    public void withDiscoveryHandler(BitbucketDiscoverableHeadType discoverableHeadType,
                                     BitbucketSCMHeadDiscoveryHandler discoveryHandler) {
        requireNonNull(discoverableHeadType, "discoverableHeadType");
        requireNonNull(discoveryHandler, "discoveryHandler");

        discoveryHandlers.put(discoverableHeadType, discoveryHandler);
    }
}
