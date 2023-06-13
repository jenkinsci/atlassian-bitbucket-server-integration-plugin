package com.atlassian.bitbucket.jenkins.internal.scm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceRequest;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public class BitbucketSCMSourceRequest extends SCMSourceRequest {

    private final Map<BitbucketDiscoverableHeadType, BitbucketSCMHeadDiscoveryHandler> discoveryHandlers;

    protected BitbucketSCMSourceRequest(SCMSource source,
                                        BitbucketSCMSourceContext context,
                                        @CheckForNull TaskListener listener) {
        super(source, context, listener);
        this.discoveryHandlers = requireNonNull(context.getDiscoveryHandlers(), "discoveryHandlers");

    }

    public Map<BitbucketDiscoverableHeadType, BitbucketSCMHeadDiscoveryHandler> getDiscoveryHandlers() {
        return discoveryHandlers;
    }

}
