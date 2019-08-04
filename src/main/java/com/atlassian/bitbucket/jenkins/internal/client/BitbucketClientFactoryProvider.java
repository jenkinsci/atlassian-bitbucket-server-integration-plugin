package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.utils.JenkinsToBitbucketCredentialFactory;
import com.cloudbees.plugins.credentials.Credentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Plugin;
import jenkins.model.Jenkins;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Singleton;
import java.io.IOException;

/**
 * Client factory provider, use to ensure that expensive objects are only created once and re-used.
 */
@ThreadSafe
@Singleton
public class BitbucketClientFactoryProvider {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient okHttpClient =
            new OkHttpClient.Builder().addInterceptor(new UserAgentInterceptor()).build();

    /**
     * Return a client factory for the given server.
     *
     * @param server the server to connect to
     * @return a ready to use client factory
     */
    public BitbucketClientFactory getClient(
            @Nonnull BitbucketServerConfiguration server, @Nullable Credentials credentials) {
        credentials = credentials == null ? server.getCredentials() : credentials;
        BitbucketCredential bitbucketCredential = JenkinsToBitbucketCredentialFactory.create(credentials);
        return new BitbucketClientFactoryImpl(
                server.getBaseUrl(),
                bitbucketCredential,
                objectMapper,
                new HttpRequestExecutorImpl(okHttpClient));
    }

    /**
     * Having this as a client level interceptor means we can configure it once to set the
     * user-agent and not have to worry about setting the header for every request.
     */
    private static class UserAgentInterceptor implements Interceptor {

        private final String bbJenkinsUserAgent;

        UserAgentInterceptor() {
            String version = "unknown";
            try {
                Plugin plugin = Jenkins.get().getPlugin("atlassian-bitbucket-server-scm");
                if (plugin != null) {
                    version = plugin.getWrapper().getVersion();
                }
            } catch (IllegalStateException e) {
                Logger.getLogger(UserAgentInterceptor.class).warn("Jenkins not available", e);
            }
            bbJenkinsUserAgent = "Bitbucket Jenkins Integration/" + version;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request =
                    chain.request().newBuilder().header("User-Agent", bbJenkinsUserAgent).build();
            return chain.proceed(request);
        }
    }
}
