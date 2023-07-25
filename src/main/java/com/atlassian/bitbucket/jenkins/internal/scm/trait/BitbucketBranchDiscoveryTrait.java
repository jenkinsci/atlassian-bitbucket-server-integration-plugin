package com.atlassian.bitbucket.jenkins.internal.scm.trait;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSourceContext;
import hudson.Extension;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.impl.trait.Discovery;

/**
 * Provides backward compatibility for {@link BranchDiscoveryTrait}.
 * <p>
 * TODO: This is just a temporary placeholder and will be replaced with our own implementation.
 *
 * @since 4.0.0
 */
public class BitbucketBranchDiscoveryTrait extends BranchDiscoveryTrait {

    @Discovery
    @Extension
    public static class DescriptorImpl extends BranchDiscoveryTrait.DescriptorImpl {

        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return BitbucketSCMSourceContext.class;
        }

        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return BitbucketSCMSource.class;
        }
    }
}
