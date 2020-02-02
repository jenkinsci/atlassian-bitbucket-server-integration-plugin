package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.Token;
import net.jcip.annotations.Immutable;

import javax.annotation.Nullable;
import java.net.URI;
import java.security.Principal;
import java.time.Clock;

import static com.atlassian.bitbucket.jenkins.internal.util.SystemPropertyUtils.parsePositiveLongFromSystemProperty;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Representation of an OAuth token for use by service providers.  A token can be either a request token or an
 * access token.  Tokens always have a token value, a token secret and the {@code Consumer} the token belongs to.
 * A request token that has been authorized will also contain the user that authorized the request.  An access token
 * will always contain user that gave permission to the {@code Consumer} to make requests on their behalf.
 *
 * <p>Tokens instances are immutable.  To create a new {@code ServiceProviderToken} instance, use builder.  To build an
 * unauthorized request token, use the {@link #newRequestToken(String)} as follows
 * <pre>
 *   ServiceProviderToken unauthorizedRequestToken = ServiceProviderToken.newRequestToken("bb6dd1391ce33b5bd3ecad1175139a39")
 *          .tokenSecret("29c3005cc5fbe5d431f27b29d6191ea3")
 *          .consumer(consumer)
 *          .build();
 * </pre>
 *
 * <p>An authorized request token can be built by calling {@link #authorize(Principal, String)} method on an unauthorized request
 * token
 * <pre>
 *   ServiceProviderToken authorizedRequestToken = unauthorizedRequestToken.authorize(fred);
 * </pre>
 * or from scratch in a similar way to unauthorized request tokens, but also setting the authorizedBy attribute
 * by calling {@link ServiceProviderTokenBuilder#authorizedBy(Principal)} before calling build()
 * <pre>
 *   ServiceProviderToken authorizedRequestToken = ServiceProviderToken.newRequestToken("bb6dd1391ce33b5bd3ecad1175139a39")
 *          .tokenSecret("29c3005cc5fbe5d431f27b29d6191ea3")
 *          .consumer(consumer)
 *          .authorizedBy(fred)
 *          .build();
 * </pre>
 *
 * <p>To build an access token, use the {@link #newAccessToken(String)} method as the starting point
 * <pre>
 *   ServiceProviderToken accessToken = ServiceProviderToken.newAccessToken("bb6dd1391ce33b5bd3ecad1175139a39")
 *          .tokenSecret("29c3005cc5fbe5d431f27b29d6191ea3")
 *          .consumer(consumer)
 *          .authorizedBy(fred)
 *          .build();
 * </pre>
 *
 * <p>{@code ServiceProviderToken}s also have two additional attributes that control when they expire: the
 * {@code creationTime} and {@code timeToLive}. If these values are not specified when building a token, the defaults
 * are used.  The default value for {@code creationTime} is when the {@link ServiceProviderTokenBuilder#build()} method
 * is called and the token constructed.  The default value for {@code timeToLive} depends on the type of token being
 * constructed.  For request tokens, the default value is 10 minutes.  For access tokens, the default value is 1 week.
 * When a token has been around for longer than its {@code timeToLive}, any attempts to use it should result in an
 * OAuth problem of {@code token_expired}, as described in the</p>
 *
 * @see <a href="http://wiki.oauth.net/ProblemReporting">OAuth problem reporting spec</a>
 */
@Immutable
public final class ServiceProviderToken extends Token {

    /**
     * The default value for request token time to live.  Value corresponds to 10 minutes in ms.
     */
    public static final long DEFAULT_REQUEST_TOKEN_TTL =
            parsePositiveLongFromSystemProperty("bitbucket.oauth.default.request.token.ttl", 600000);

    /**
     * The default value for access token time to live.  Value corresponds to 5 years in ms.
     */
    public static final long DEFAULT_ACCESS_TOKEN_TTL =
            parsePositiveLongFromSystemProperty("bitbucket.oauth.default.access.token.ttl",
                    5 * 365 * 24 * 60 * 60 * 1000L);

    /**
     * The default value for session time to live.  Value corresponds to 5 years + 30 days in ms.
     * This value is supposed to be longer than {@link ServiceProviderToken#DEFAULT_ACCESS_TOKEN_TTL} so that the session is still
     * live while the access token has just expired.
     */
    public static final long DEFAULT_SESSION_TTL =
            parsePositiveLongFromSystemProperty("bitbucket.oauth.default.session.ttl",
                    DEFAULT_ACCESS_TOKEN_TTL + 30 * 24 * 60 * 60 * 1000L);

    private final Authorization authorization;
    private final Principal user;
    private final String verifier;
    private final long creationTime;
    private final long timeToLive;
    private final URI callback;
    private final Session session;

    private ServiceProviderToken(ServiceProviderTokenBuilder builder) {
        super(builder);
        if (isAccessToken()) {
            checkNotNull(builder.user, "user must be set for access tokens");
        } else {
            if (builder.user != null && builder.authorization == Authorization.AUTHORIZED) {
                if (isBlank(builder.verifier)) {
                    throw new IllegalArgumentException("verifier MUST NOT be blank if the request token has been authorized");
                }
            }
        }
        if (builder.callback != null) {
            if (!isValidCallback(builder.callback)) {
                throw new IllegalArgumentException("callback must be null or a valid, absolute URI using either the http or https scheme");
            }
        }
        this.authorization = builder.authorization;
        this.user = builder.user;
        this.verifier = builder.verifier;
        this.creationTime = builder.creationTime;
        this.timeToLive = builder.timeToLive;
        this.callback = builder.callback;
        this.session = builder.session;
    }

    /**
     * Static factory method that starts the process of building a request {@code ServiceProviderToken} instance.
     * Returns a {@code ServiceProviderTokenBuilder} so the additional attributes of the token can be set.
     *
     * @param token unique token used to the {@code ServiceProviderToken} to be used in OAuth operations
     * @return builder to set additional attributes and build the {@code ServiceProviderToken}
     */
    public static ServiceProviderTokenBuilder newRequestToken(String token) {
        return new ServiceProviderTokenBuilder(Type.REQUEST, checkNotNull(token, "token"));
    }

    /**
     * Static factory method that starts the process of building an access {@code ServiceProviderToken} instance.
     * Returns a {@code ServiceProviderTokenBuilder} so the additional attributes of the token can be set.
     *
     * @param token unique token used to the {@code ServiceProviderToken} to be used in OAuth operations
     * @return builder to set additional attributes and build the {@code ServiceProviderToken}
     */
    public static ServiceProviderTokenBuilder newAccessToken(String token) {
        return new ServiceProviderTokenBuilder(Type.ACCESS, checkNotNull(token, "token"));
    }

    /**
     * If this is an unauthorized request token, this method will return a request token that has been authorized by the
     * {@code user}.
     *
     * @param user     {@code Principal} of the user that has authorized the request token
     * @param verifier value used to prove the user authorizing the token is the same as the one swapping it for an
     *                 access token
     * @return authorized request token
     * @throws IllegalStateException thrown if the token is not a request token or has already been authorized or denied
     */
    public ServiceProviderToken authorize(Principal user, String verifier) {
        requireNonNull(user, "user");
        if (isBlank(verifier)) {
            throw new IllegalArgumentException("verifier");
        }
        if (!isRequestToken()) {
            throw new IllegalStateException("token is not a request token");
        }
        if (hasBeenAuthorized()) {
            throw new IllegalStateException("token has already been authorized");
        }
        if (hasBeenDenied()) {
            throw new IllegalStateException("token has already been denied");
        }
        return newRequestToken(getToken())
                .tokenSecret(getTokenSecret())
                .consumer(getConsumer())
                .authorizedBy(user)
                .verifier(verifier)
                .creationTime(creationTime)
                .timeToLive(timeToLive)
                .properties(getProperties())
                .callback(callback)
                .build();
    }

    /**
     * Returns {@code true} if this token has been authorized, {@code false} otherwise.  This is a short-cut for
     * calling {@link #getAuthorization()} and checking the return type.  As such, it has the same condition that
     * it will always return {@code true} if the token is an access token.
     *
     * @return {@code true} if this token has been authorized, {@code false} otherwise
     */
    public boolean hasBeenAuthorized() {
        return getAuthorization() == Authorization.AUTHORIZED;
    }

    /**
     * If this is an unauthorized request token, this method will return a request token that has been denied by the
     * {@code user}.
     *
     * @param user {@code Principal} of the user that has denied the request token
     * @return denied request token
     * @throws IllegalStateException thrown if the token is not a request token or has already been authorized or denied
     */
    public ServiceProviderToken deny(Principal user) {
        checkNotNull(user, "user");
        if (!isRequestToken()) {
            throw new IllegalStateException("token is not a request token");
        }
        if (hasBeenAuthorized()) {
            throw new IllegalStateException("token has already been authorized");
        }
        if (hasBeenDenied()) {
            throw new IllegalStateException("token has already been denied");
        }
        return newRequestToken(getToken())
                .tokenSecret(getTokenSecret())
                .consumer(getConsumer())
                .deniedBy(user)
                .creationTime(creationTime)
                .timeToLive(timeToLive)
                .properties(getProperties())
                .callback(callback)
                .build();
    }

    /**
     * Returns {@code true} if this token has been denied, {@code false} otherwise.  This is a short-cut for
     * calling {@link #getAuthorization()} and checking the return type.  As such, it has the same condition that
     * it will always return {@code false} if the token is an access token.
     *
     * @return {@code true} if this token has been denied, {@code false} otherwise
     */
    public boolean hasBeenDenied() {
        return getAuthorization() == Authorization.DENIED;
    }

    /**
     * Returns the authorization status of this token.  If the token is a request token, it will return
     * {@code Authorization.NONE} if it the user has not yet approved or denied the request,
     * {@code Authorization.APPROVED} if the user approved the access request, or {@code Authorization.DENIED} if the
     * user denied the access request.  For access tokens, {@code Authorized.APPROVED} will always be returned.
     *
     * @return authorization status of this token
     */
    public Authorization getAuthorization() {
        return authorization;
    }

    /**
     * If this is an authorized request token, returns the user that authorized the token.  If this is an access token,
     * it's the user the {@code Consumer} is making requests on behalf of.  Returns {@code null} otherwise.
     *
     * @return {@code Principal} of the user that authorized the {@code Consumer} to make requests on behalf of themselves
     */
    @Nullable
    public Principal getUser() {
        return user;
    }

    /**
     * If this is an authorized request token, returns the verification code that is used to verify the user that
     * authorized the token is the same one that is swapping it for an access token.  Returns {@code null} otherwise.
     *
     * @return verification code that is used to verify the user that authorized the token is the same one that is
     *         swapping it for an access token
     */
    @Nullable
    public String getVerifier() {
        return verifier;
    }

    /**
     * Returns the time the token was originally created for the user, in milliseconds.
     *
     * @return time the token was originally created for the user, in milliseconds
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Returns the maximum amount of time the token is considered valid, in milliseconds.
     *
     * @return maximum amount of time the token is considered valid, in milliseconds
     */
    public long getTimeToLive() {
        return timeToLive;
    }

    /**
     * Returns {@code true} if the time to live has been exceeded, {@code false} otherwise.
     *
     * @param clock a way to determine the current time
     * @return {@code true} if the time to live has been exceeded, {@code false} otherwise
     */
    public boolean hasExpired(Clock clock) {
        return clock.millis() - creationTime > timeToLive;
    }

    /**
     * Returns the {@code URI} the consumer should be redirected to after the user has completed authorization.
     * It will be {@code null} if the {@code URI} was communicated out-of-band via another form of communication
     * between the service provider and consumer.  It will also be {@code null} if the token is a version 1.0 request
     * token.
     *
     * @return {@code URI} the consumer should be redirected to after the user has completed authorization
     */
    public URI getCallback() {
        return callback;
    }

    public static boolean isValidCallback(URI callback) {
        return callback.isAbsolute() && ("https".equals(callback.getScheme()) || "http".equals(callback.getScheme()));
    }

    /**
     * Returns the {@code Session} associated with the token.
     */
    public Session getSession() {
        return session;
    }

    /**
     * Returns {@code true} if there is a {@code Session} associated with the token.
     */
    public boolean hasSession() {
        return session != null;
    }

    /**
     * Defines the status of request tokens.
     */
    public enum Authorization {
        /**
         * The user has neither approved nor denied access.
         */
        NONE,

        /**
         * The user has approved access.
         */
        AUTHORIZED,

        /**
         * The user has denied access.
         */
        DENIED
    }

    /**
     * Representation of an OAuth session.  As long as the session is valid, old access tokens can be swapped for new
     * access tokens.  A session is valid as long as the last renewal time plus the time to live is greater than the
     * current time.
     */
    public static final class Session {

        private final String handle;
        private final long creationTime;
        private final long lastRenewalTime;
        private final long timeToLive;

        Session(Builder builder) {
            this.handle = builder.handle;
            this.creationTime = builder.creationTime;
            this.lastRenewalTime = builder.lastRenewalTime;
            this.timeToLive = builder.timeToLive;
        }

        /**
         * @return handle of the session used when swapping access tokens
         */
        public String getHandle() {
            return handle;
        }

        /**
         * @return time the session was originally created - i.e. when the first access token was created
         */
        public long getCreationTime() {
            return creationTime;
        }

        /**
         * @return last time the session was renewed by swapping an old access token
         */
        public long getLastRenewalTime() {
            return lastRenewalTime;
        }

        /**
         * @return length of time, from the last renewal time, that the session is valid
         */
        public long getTimeToLive() {
            return timeToLive;
        }

        /**
         * Static factory for creating a session builder.
         *
         * @param handle handle the session will have
         * @return new builder
         */
        public static Builder newSession(String handle) {
            return new Builder(handle);
        }

        /**
         * Builder for creating session instances.
         */
        public static final class Builder {

            private final String handle;
            private long creationTime = System.currentTimeMillis();
            private long lastRenewalTime = creationTime;
            private long timeToLive = DEFAULT_SESSION_TTL;

            Builder(String handle) {
                requireNonNull(handle);
                this.handle = handle;
            }

            /**
             * Set the time the session was originally created and return this builder.
             *
             * @param creationTime time the session was originally created
             * @return this builder
             */
            public Builder creationTime(long creationTime) {
                this.creationTime = creationTime;
                return this;
            }

            /**
             * Set the last time the session was renewed and return this builder.
             *
             * @param lastRenewalTime last time the session was renewed
             * @return this builder
             */
            public Builder lastRenewalTime(long lastRenewalTime) {
                this.lastRenewalTime = lastRenewalTime;
                return this;
            }

            /**
             * Sets the length of time the session is valid for and returns this builder.
             *
             * @param timeToLive length of time the session is valid for
             * @return this builder
             */
            public Builder timeToLive(long timeToLive) {
                this.timeToLive = timeToLive;
                return this;
            }

            /**
             * @return new session instance
             */
            public Session build() {
                return new Session(this);
            }
        }

        /**
         * Returns {@code true} if the session has expired - the time to live plus the last renewal time is less than
         * the current time, {@code false} otherwise.
         *
         * @param clock clock to use to determine the current time
         * @return {@code true} if the session has expired, {@code false} otherwise.
         */
        public boolean hasExpired(Clock clock) {
            return clock.millis() - lastRenewalTime > timeToLive;
        }
    }

    public static final class ServiceProviderTokenBuilder extends TokenBuilder<ServiceProviderToken, ServiceProviderTokenBuilder> {

        private Authorization authorization;
        private Principal user;
        private String verifier;
        private long creationTime;
        private long timeToLive;
        private URI callback;
        private Session session;

        private ServiceProviderTokenBuilder(Type type, String token) {
            super(type, token);
            if (type == Type.ACCESS) {
                timeToLive = DEFAULT_ACCESS_TOKEN_TTL;
                authorization = Authorization.AUTHORIZED;
            } else {
                timeToLive = DEFAULT_REQUEST_TOKEN_TTL;
                authorization = Authorization.NONE;
            }
        }

        /**
         * Sets the {@code user} that authorized the request token and returns {@code this} builder
         * to allow other optional attributes to be set or the final request {@code Token} instance to be constructed.
         *
         * @param user Principal of the user that authorized the {@code Consumer} to make requests on behalf of themselves
         * @return {@code this} builder
         */
        public ServiceProviderTokenBuilder authorizedBy(Principal user) {
            this.user = user;
            this.authorization = Authorization.AUTHORIZED;
            return this;
        }

        /**
         * Sets the {@code user} that denied the request token and returns {@code this} builder
         * to allow other optional attributes to be set or the final request {@code Token} instance to be constructed.
         *
         * @param user Principal of the user that denied the {@code Consumer} to make requests on behalf of themselves
         * @return {@code this} builder
         */
        public ServiceProviderTokenBuilder deniedBy(Principal user) {
            this.user = user;
            this.authorization = Authorization.DENIED;
            return this;
        }

        /**
         * Sets the {@code verifier} value to use to determine that the authorizing user is the same as the user
         * swapping the request token for an access token and returns {@code this} builder
         * to allow other optional attributes to be set or the final request {@code Token} instance to be constructed.
         *
         * @param verifier value to use to determine that the authorizing user is the same as the user swapping the
         *                 request token for an access token
         * @return {@code this} builder
         */
        public ServiceProviderTokenBuilder verifier(String verifier) {
            this.verifier = verifier;
            return this;
        }

        /**
         * Sets the {@code creationTime} attribute of the token and returns {@code this} builder
         * to allow other optional attributes to be set or the final request {@code Token} instance to be constructed.
         *
         * @param creationTime time the token was originally created
         * @return {@code this} builder
         */
        public ServiceProviderTokenBuilder creationTime(long creationTime) {
            this.creationTime = creationTime;
            return this;
        }

        /**
         * Sets the {@code timeToLive} attribute of the token and returns {@code this} builder
         * to allow other optional attributes to be set or the final request {@code Token} instance to be constructed.
         *
         * @param timeToLive how long the token is valid for
         * @return {@code this} builder
         */
        public ServiceProviderTokenBuilder timeToLive(long timeToLive) {
            this.timeToLive = timeToLive;
            return this;
        }

        /**
         * Sets the {@code callback} attribute of the token and returns {@code this} builder to allow other optional
         * attributes to be set or the final request {@code Token} instance to be constructed.  A {@code null} callback
         * value indicates that the callback has been established out-of-band, via some other form of communication
         * between the consumer and the service provider.
         *
         * @param callback callback token attributes value
         * @return {@code this} builder
         */
        public ServiceProviderTokenBuilder callback(@Nullable URI callback) {
            this.callback = callback;
            return this;
        }

        /**
         * Sets the {@code session} attribute of the token and returns {@code this} builder to allow other optional
         * attributes to be set or the final request {@code Token} instance to be constructed.
         *
         * @param session the session
         * @return {@code this} builder
         */
        public ServiceProviderTokenBuilder session(Session session) {
            this.session = session;
            return this;
        }

        /**
         * Constructs and returns the final request {@code Token} instance.
         *
         * @return the final request {@code Token} instance
         */
        @Override
        public ServiceProviderToken build() {
            if (creationTime == 0) {
                creationTime = System.currentTimeMillis();
            }
            return new ServiceProviderToken(this);
        }
    }
}
