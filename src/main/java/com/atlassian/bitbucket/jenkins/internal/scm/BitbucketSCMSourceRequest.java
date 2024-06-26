package com.atlassian.bitbucket.jenkins.internal.scm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceRequest;

import java.util.Collection;

import static java.util.Objects.requireNonNull;

/**
 * @since 4.0.0
 */
public class BitbucketSCMSourceRequest extends SCMSourceRequest {

    private final Collection<BitbucketSCMHeadDiscoveryHandler> discoveryHandlers;

    protected BitbucketSCMSourceRequest(SCMSource source,
                                        BitbucketSCMSourceContext context,
                                        @CheckForNull TaskListener listener) {
        super(source, context, listener);
        this.discoveryHandlers = requireNonNull(context.getDiscoveryHandlers(), "discoveryHandlers");
    }

    public Collection<BitbucketSCMHeadDiscoveryHandler> getDiscoveryHandlers() {
        return discoveryHandlers;
    }
}
