package com.atlassian.bitbucket.jenkins.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestClientFactory {

    public static BitbucketClientFactory create(String url, BitbucketCredentials credentials, ObjectMapper objectMapper, HttpRequestExecutor httpRequestExecutor) {
        return new BitbucketClientFactoryImpl(url, credentials, objectMapper, httpRequestExecutor);
    }

}
