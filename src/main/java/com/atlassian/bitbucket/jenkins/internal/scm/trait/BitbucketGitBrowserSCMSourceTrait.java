package com.atlassian.bitbucket.jenkins.internal.scm.trait;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSourceContext;
import hudson.Extension;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;

/**
 * Provides backward compatibility for {@link GitBrowserSCMSourceTrait}.
 *
 * @since 4.0.0
 */
public class BitbucketGitBrowserSCMSourceTrait extends GitBrowserSCMSourceTrait {

    @DataBoundConstructor
    public BitbucketGitBrowserSCMSourceTrait(@CheckForNull GitRepositoryBrowser browser) {
        super(browser);
    }

    @Extension
    public static class DescriptorImpl extends GitBrowserSCMSourceTrait.DescriptorImpl {

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
