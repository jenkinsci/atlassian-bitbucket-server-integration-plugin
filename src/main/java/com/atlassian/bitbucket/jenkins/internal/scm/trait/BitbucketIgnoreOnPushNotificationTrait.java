package com.atlassian.bitbucket.jenkins.internal.scm.trait;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSourceContext;
import jenkins.plugins.git.traits.IgnoreOnPushNotificationTrait;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;

/**
 * Provides backward compatibility for {@link IgnoreOnPushNotificationTrait}.
 *
 * @since 4.0.0
 */
public class BitbucketIgnoreOnPushNotificationTrait extends IgnoreOnPushNotificationTrait {

    public static class DescriptorImpl extends IgnoreOnPushNotificationTrait.DescriptorImpl {

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
