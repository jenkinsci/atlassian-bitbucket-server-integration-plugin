package com.atlassian.bitbucket.jenkins.internal.scm.trait;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSourceContext;
import hudson.Extension;
import jenkins.plugins.git.traits.RefSpecsSCMSourceTrait;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

/**
 * Provides backward compatibility for {@link RefSpecsSCMSourceTrait}.
 *
 * @since 4.0.0
 */
public class BitbucketRefSpecsSCMSourceTrait extends RefSpecsSCMSourceTrait {

    @DataBoundConstructor
    public BitbucketRefSpecsSCMSourceTrait(List<RefSpecTemplate> templates) {
        super(templates);
    }

    /**
     * The parent trait casts the context to a {@code GitSCMSourceContext}, which {@link BitbucketSCMSourceContext} is
     * not. Refspecs are only relevant when building the SCM, so there is nothing to apply to the context.
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
    }

    @Extension
    public static class DescriptorImpl extends RefSpecsSCMSourceTrait.DescriptorImpl {

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
