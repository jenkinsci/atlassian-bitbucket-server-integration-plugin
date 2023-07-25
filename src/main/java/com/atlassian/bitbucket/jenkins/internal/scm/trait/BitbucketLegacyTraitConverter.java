package com.atlassian.bitbucket.jenkins.internal.scm.trait;

import jenkins.plugins.git.traits.DiscoverOtherRefsTrait;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.plugins.git.traits.TagDiscoveryTrait;
import jenkins.scm.api.trait.SCMSourceTrait;

import javax.annotation.CheckForNull;

/**
 * @since 4.0.0
 */
public class BitbucketLegacyTraitConverter {

    private BitbucketLegacyTraitConverter() {
        throw new IllegalStateException("This is a utility class and should not be instantiated");
    }

    /**
     * Attempts to convert known legacy traits into their newer equivalents. May return one of the following.
     * <ul>
     *     <li>Returns null if support for the trait has been dropped.</li>
     *     <li>Returns the new equivalent of the trait if there is one available.</li>
     *     <li>Returns the same trait instance if neither of the previous conditions have been met.</li>
     * </ul>
     */
    @CheckForNull
    public static SCMSourceTrait maybeConvert(SCMSourceTrait trait) {
        if (trait instanceof TagDiscoveryTrait || trait instanceof DiscoverOtherRefsTrait) {
            return null;
        }

        if (trait instanceof GitBrowserSCMSourceTrait) {
            return new BitbucketGitBrowserSCMSourceTrait((GitBrowserSCMSourceTrait) trait);
        }

        return trait;
    }
}
