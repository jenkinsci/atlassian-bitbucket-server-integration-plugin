package com.atlassian.bitbucket.jenkins.internal.util;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketBranchClient;

/**
 * A list of all system properties used by the plugin.
 */
public class SystemPropertiesConstants {

    /**
     * If set to true, this property will prevent build statuses being sent by jobs to Bitbucket. This applies to all
     * jobs running on the instance. This may be done for testing purposes, or to stop multiple Jenkins instances posting
     * duplicate build statuses to Bitbucket.
     * Defaults to FALSE.
     */
    public static final String BUILD_STATUS_DISABLED_KEY = "bitbucket.status.disable";
    /**
     * Specifies the duration of the Bitbucket capabilities cache. This cache is used to determine which features of
     * Bitbucket are available to Jenkins. Jenkins makes a request to Bitbucket whenever the cache expires.
     * Defaults to 1 hour.
     */
    public static final String CAPABILITIES_CACHE_DURATION_KEY = "bitbucket.client.capabilities.cache.duration";
    /**
     * Specifies the time to live (TTL) of an OAuth access token, used when making API requests in Jenkins on
     * behalf of the Bitbucket user, such as starting jobs. When the token expires, the user will have to acquire
     * another before being able to make further API requests.
     * Defaults to 5 years.
     */
    public static final String DEFAULT_OAUTH_ACCESS_TOKEN_TTL_KEY = "bitbucket.oauth.default.access.token.ttl";
    /**
     * Specifies the time to live (TTL) of an OAuth request token, which is used when performing an authorization flow
     * between Bitbucket and Jenkins. This token TTL determines the maximum length of that flow before the user must
     * attempt again.
     * Defaults to 10 minutes.
     */
    public static final String DEFAULT_OAUTH_REQUEST_TOKEN_TTL_KEY = "bitbucket.oauth.default.request.token.ttl";
    /**
     * Specifies the time to live (TTL) of an OAuth session. This is the period after an OAuth token has been acquired
     * as part of applinking Bitbucket and Jenkins. So long as the session is active, old or expired access tokens
     * can be swapped for new ones. This can be lengthened or shortened, but it's recommended the session is never
     * shorter than the {@link SystemPropertiesConstants#DEFAULT_OAUTH_ACCESS_TOKEN_TTL_KEY}.
     * Defaults to 5 years and 30 days.
     */
    public static final String DEFAULT_OAUTH_SESSION_TTL_KEY = "bitbucket.oauth.default.session.ttl";
    /**
     * Specifies the maximum number of pages to fetch when
     * {@link BitbucketBranchClient#getRemoteBranches() fetching remote branches}.
     * Defaults to 5.
     * @since 4.0.0
     */
    public static final String REMOTE_BRANCHES_RETRIEVAL_MAX_PAGES = "bitbucket.remote.branches.retrieval.max.pages";
    /**
     * Specifies the size of a page to fetch when
     * {@link BitbucketBranchClient#getRemoteBranches() fetching remote branches}.
     * Defaults to 1000.
     * @since 4.0.0
     */
    public static final String REMOTE_BRANCHES_RETRIEVAL_PAGE_SIZE = "bitbucket.remote.branches.retrieval.page.size";
    /**
     * Maximum number of attempts to retry when posting a build status and the server responds wth a 429.
     * Defaults 3. Care should be taken when adjusting this as to not overload a server that is already under load.
     */
    public static final String REQUEST_RETRY_MAX_ATTEMPTS = "bitbucket.build.post.retry.request.attempts";
}
