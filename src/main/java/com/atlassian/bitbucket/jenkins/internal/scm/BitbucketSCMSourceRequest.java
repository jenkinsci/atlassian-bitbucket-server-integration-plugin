package com.atlassian.bitbucket.jenkins.internal.scm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceRequest;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public class BitbucketSCMSourceRequest extends SCMSourceRequest {

    private final Map<BitbucketDiscoverableHeadType, BitbucketSCMHeadDiscoveryHandler> discoveryHandlers;
    private final boolean fetchChangeRequests;

    protected BitbucketSCMSourceRequest(@NonNull SCMSource source,
                                        @NonNull BitbucketSCMSourceContext context,
                                        @CheckForNull TaskListener listener) {
        super(source, context, listener);
        this.discoveryHandlers = requireNonNull(context.getDiscoveryHandlers(), "discoveryHandlers");
        // copy the relevant details from the context into the request
        this.fetchChangeRequests = context.needsChangeRequests();
    }

    public Map<BitbucketDiscoverableHeadType, BitbucketSCMHeadDiscoveryHandler> getDiscoveryHandlers() {
        return discoveryHandlers;
    }

    public boolean isFetchChangeRequests() {
        return fetchChangeRequests;
    }
}
