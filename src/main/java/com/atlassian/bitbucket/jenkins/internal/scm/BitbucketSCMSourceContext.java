package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRepositoryClient;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static java.util.Objects.requireNonNull;

public class BitbucketSCMSourceContext extends SCMSourceContext<BitbucketSCMSourceContext, BitbucketSCMSourceRequest> {

    private final Collection<BitbucketSCMHeadDiscoveryHandler> discoveryHandlers = new ArrayList<>();
    private final BitbucketRepositoryClient repositoryClient;

    public BitbucketSCMSourceContext(SCMSourceCriteria criteria,
                                     SCMHeadObserver observer,
                                     BitbucketRepositoryClient repositoryClient) {
        super(criteria, observer);
        this.repositoryClient = requireNonNull(repositoryClient, "repositoryClient");
    }

    public final Collection<BitbucketSCMHeadDiscoveryHandler> discoveryHandlers() {
        return Collections.unmodifiableCollection(discoveryHandlers);
    }

    @Override
    public final BitbucketSCMSourceRequest newRequest(SCMSource source, @CheckForNull TaskListener listener) {
        return new BitbucketSCMSourceRequest(source, this, listener);
    }

    public final BitbucketRepositoryClient repositoryClient() {
        return repositoryClient;
    }

    public final BitbucketSCMSourceContext withDiscoveryHandler(BitbucketSCMHeadDiscoveryHandler handler) {
        discoveryHandlers.add(requireNonNull(handler, "handler"));
        return this;
    }
}
