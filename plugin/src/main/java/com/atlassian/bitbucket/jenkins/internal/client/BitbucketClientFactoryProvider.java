package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;

import static java.util.Objects.requireNonNull;

/**
 * Client factory provider, use to ensure that expensive objects are only created once and re-used.
 */
@ThreadSafe
@Singleton
public class BitbucketClientFactoryProvider {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpRequestExecutor httpRequestExecutor;

    @Inject
    public BitbucketClientFactoryProvider(HttpRequestExecutor httpRequestExecutor) {
        this.httpRequestExecutor = httpRequestExecutor;
    }

    /**
     * Return a client factory for the given base URL.
     *
     * @param baseUrl     the URL to connect to
     * @param credentials the credentials to use while making the HTTP request
     * @return a ready to use client factory
     */
    public BitbucketClientFactory getClient(String baseUrl, BitbucketCredentials credentials) {
        requireNonNull(baseUrl, "Bitbucket Server base url cannot be null.");
        requireNonNull(credentials, "Credentials can't be null. For no credentials use anonymous.");
        return new BitbucketClientFactoryImpl(
                baseUrl,
                credentials,
                objectMapper,
                httpRequestExecutor);
    }
}
