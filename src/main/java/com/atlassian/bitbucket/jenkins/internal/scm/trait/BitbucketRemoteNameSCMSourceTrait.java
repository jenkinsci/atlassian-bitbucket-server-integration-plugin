package com.atlassian.bitbucket.jenkins.internal.scm.trait;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSourceContext;
import jenkins.plugins.git.traits.RemoteNameSCMSourceTrait;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Provides backward compatibility for {@link RemoteNameSCMSourceTrait}.
 *
 * @since 4.0.0
 */
public class BitbucketRemoteNameSCMSourceTrait extends RemoteNameSCMSourceTrait {

    @DataBoundConstructor
    public BitbucketRemoteNameSCMSourceTrait(String remoteName) {
        super(remoteName);
    }

    public static class DescriptorImpl extends RemoteNameSCMSourceTrait.DescriptorImpl {

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
