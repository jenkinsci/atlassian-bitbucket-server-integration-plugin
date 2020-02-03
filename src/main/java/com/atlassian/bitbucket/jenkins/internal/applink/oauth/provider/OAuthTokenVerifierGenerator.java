package com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider;

import com.google.inject.ImplementedBy;

/**
 * Generates OAuth token verifiers
 */
@ImplementedBy(RandomValueOAuthTokenVerifierGenerator.class)
public interface OAuthTokenVerifierGenerator {

    /**
     * Generate a new OAuth token verifier
     *
     * @return a new OAuth token verifier
     */
    String generateVerifier();
}
