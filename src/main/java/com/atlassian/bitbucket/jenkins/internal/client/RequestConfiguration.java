package com.atlassian.bitbucket.jenkins.internal.client;

import okhttp3.Request;

/**
 * Additional HTTP request configuration. The configuration is applied <em>before</em> the request is made. It is applied
 * exactly once, and in the case of re-try they will not be applied again for new attempts. an {@link IllegalArgumentException}
 * is thrown if the same {@link RequestConfiguration} is added more than once to a single request.
 *
 * @since 3.1
 */
public interface RequestConfiguration {

    /**
     * Update the supplied builder with the configuration required. The order in which configurations are applied is not
     * defined nor guaranteed to be the same between invocations.
     *
     * @param builder the request builder, some {@link RequestConfiguration}s may already have been applied to the builder
     */
    void apply(Request.Builder builder);
}