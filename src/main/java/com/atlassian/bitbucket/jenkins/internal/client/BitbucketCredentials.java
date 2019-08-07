package com.atlassian.bitbucket.jenkins.internal.client;

/**
 * Represents Bitbucket credential that will be used to make remote calls to Bitbucket server.
 */
public interface BitbucketCredentials {

    /**
     * The authorization header key which will be sent with all authorized request.
     */
    String AUTHORIZATION_HEADER_KEY = "Authorization";
    BitbucketCredentials ANONYMOUS_CREDENTIALS = new AnonymousCredentials();

    /**
     * Convert this representation to authorization header value.
     *
     * @return header value.
     */
    String toHeaderValue();

    class AnonymousCredentials implements BitbucketCredentials {

        private AnonymousCredentials() {
        }

        @Override
        public String toHeaderValue() {
            return "";
        }
    }
}
