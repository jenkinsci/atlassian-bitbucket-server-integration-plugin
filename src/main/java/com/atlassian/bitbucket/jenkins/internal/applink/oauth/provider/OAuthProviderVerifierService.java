package com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider;

/**
 * Generates OAuth verifiers
 */
public interface OAuthProviderVerifierService {

    /**
     * Generate a new OAuth verifier
     *
     * @return a new OAuth verifier
     */
    String generateVerifier();
}
