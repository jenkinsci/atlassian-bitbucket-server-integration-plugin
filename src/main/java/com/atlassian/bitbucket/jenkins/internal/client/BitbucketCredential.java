package com.atlassian.bitbucket.jenkins.internal.client;

public interface BitbucketCredential {

    String AUTHORIZATION_HEADER_KEY = "Authorization";
    BitbucketCredential ANONYMOUS_CREDENTIALS = new AnonymousCredentials();

    String toHeaderValue();

    class AnonymousCredentials implements BitbucketCredential {

        private AnonymousCredentials(){}

        @Override
        public String toHeaderValue() {
            throw new IllegalStateException();
        }
    }
}
