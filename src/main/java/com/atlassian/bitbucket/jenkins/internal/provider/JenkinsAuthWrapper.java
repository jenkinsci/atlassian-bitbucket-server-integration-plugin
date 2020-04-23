package com.atlassian.bitbucket.jenkins.internal.provider;

import com.google.inject.ImplementedBy;
import org.acegisecurity.Authentication;

/**
 * This exists purely to support unit testing. Jenkins class is not unit test friendly. This
 * Wrapper helps to mitigate that.
 */
@ImplementedBy(DefaultJenkinsAuthWrapper.class)
public interface JenkinsAuthWrapper {

    Authentication getAuthentication();
}
