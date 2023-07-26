package com.atlassian.bitbucket.jenkins.internal.scm.trait;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSourceContext;
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
