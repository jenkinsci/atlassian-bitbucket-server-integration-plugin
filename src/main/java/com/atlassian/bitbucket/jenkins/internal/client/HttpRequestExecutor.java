package com.atlassian.bitbucket.jenkins.internal.client;

import okhttp3.HttpUrl;
import okhttp3.Response;

import javax.annotation.Nonnull;

public interface HttpRequestExecutor {

    <T> T executeGet(@Nonnull HttpUrl url, @Nonnull BitbucketCredential credential, @Nonnull ResponseConsumer<T> consumer);

    interface ResponseConsumer<T> {
        T consume(Response response);
    }
}
