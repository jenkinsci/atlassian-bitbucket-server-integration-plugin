package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.client.RequestConfiguration;
import okhttp3.Request;

import static org.apache.http.HttpHeaders.AUTHORIZATION;

/**
 * Represents Bitbucket credential that will be used to make remote calls to Bitbucket server.
 */
public interface BitbucketCredentials extends RequestConfiguration {

    /**
     * The authorization header key which will be sent with all authorized request.
     */
    BitbucketCredentials ANONYMOUS_CREDENTIALS = new AnonymousCredentials();

    /**
     * Convert this representation to authorization header value.
     *
     * @return header value.
     */
    String toHeaderValue();

    default void apply(Request.Builder builder) {
        builder.addHeader(AUTHORIZATION, toHeaderValue());
    }

    final class AnonymousCredentials implements BitbucketCredentials {

        private AnonymousCredentials() {
        }

        @Override
        public String toHeaderValue() {
            return "";
        }

        @Override
        public void apply(Request.Builder builder) {
            //anonymous credentials, nothing to do
        }
    }
}
