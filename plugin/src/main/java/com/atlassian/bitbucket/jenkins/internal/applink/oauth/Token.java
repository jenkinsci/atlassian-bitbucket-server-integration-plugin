package com.atlassian.bitbucket.jenkins.internal.applink.oauth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Base type for OAuth tokens.  This type should never be used directly.  Instead, use the more specific
 * com.atlassian.oauth.serviceprovider.ServiceProviderToken
 */
public abstract class Token {

    private final Type type;
    private final String token;
    private final String tokenSecret;
    private final Consumer consumer;
    private final Map<String, String> properties;

    protected Token(TokenBuilder<?, ?> builder) {
        type = checkNotNull(builder.type, "type");
        token = checkNotNull(builder.token, "token");
        tokenSecret = checkNotNull(builder.tokenSecret, "tokenSecret");
        consumer = checkNotNull(builder.consumer, "consumer");
        properties = Collections.unmodifiableMap(new HashMap<>(builder.properties));
    }

    /**
     * Returns the token value used to identify this token in OAuth messages.
     *
     * @return the token value used to identify this token in OAuth messages
     */
    public final String getToken() {
        return token;
    }

    /**
     * Returns the secret used by the {@code Consumer} to establish ownership of a given {@code Token}.
     *
     * @return the secret used by the {@code Consumer} to establish ownership of a given {@code Token}
     */
    public final String getTokenSecret() {
        return tokenSecret;
    }

    /**
     * Returns the {@code Consumer} that owns this token.
     *
     * @return the {@code Consumer} that owns this token
     */
    public final Consumer getConsumer() {
        return consumer;
    }

    /**
     * Returns {@code true} if this is a request token, {@code false} otherwise.
     *
     * @return {@code true} if this is a request token, {@code false} otherwise.
     */
    public final boolean isRequestToken() {
        return type == Type.REQUEST;
    }

    /**
     * Returns {@code true} if this is an access token, {@code false} otherwise.
     *
     * @return {@code true} if this is an access token, {@code false} otherwise.
     */
    public final boolean isAccessToken() {
        return type == Type.ACCESS;
    }

    /**
     * Returns {@code true} if this token contains the optional property, {@code false} otherwise.
     *
     * @param property name of the property to check the token for
     * @return {@code true} if this token contains the optional property, {@code false} otherwise.
     */
    public final boolean hasProperty(String property) {
        return properties.containsKey(property);
    }

    /**
     * Returns the value of the property, or {@code null} if the property doesn't exist.
     *
     * @param property name of the property to whose value is to be returned
     * @return the value of the property, or {@code null} if the property doesn't exist
     */
    public final String getProperty(String property) {
        return properties.get(property);
    }

    /**
     * Returns the names of the properties for this token.
     *
     * @return the names of the properties for this token
     */
    public final Iterable<String> getPropertyNames() {
        return properties.keySet();
    }

    /**
     * Returns an immutable map of the tokens properties.
     *
     * @return an immutable map of the tokens properties
     */
    public final Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return token;
    }

    /**
     * Base builder that can be used by {@code Token} subclasses to build token instances.  All attributes are required
     * to be set for the {@code Token} to be constructed properly.
     *
     * @param <T> type of the token being constructed
     * @param <B> type of the {@code TokenBuilder} implementation, so that the attribute setting methods can return
     *            objects of the type of the subclassed builder in case additional properties are added and need to be
     *            set
     */
    public abstract static class TokenBuilder<T, B extends TokenBuilder<T, B>> {

        private final Type type;
        private final String token;
        private String tokenSecret;
        private Consumer consumer;
        private Map<String, String> properties = new HashMap<String, String>();

        public TokenBuilder(Type type, String token) {
            this.type = type;
            this.token = token;
        }

        /**
         * Sets the {@code tokenSecret} attribute of the {@code Token} under construction and returns this builder
         * to allow for other attributes to be set.
         *
         * @param tokenSecret the secret used by the {@code Consumer} to establish ownership of a given {@code Token}
         * @return this builder to allow other properties to be set
         */
        @SuppressWarnings("unchecked")
        public final B tokenSecret(String tokenSecret) {
            this.tokenSecret = checkNotNull(tokenSecret, "tokenSecret");
            return (B) this;
        }

        /**
         * Sets the {@code consumer} attribute of the request {@code Token} object under construction and returns the
         * next builder in the chain, which allows optional attributes to be set and the final request {@code Token}
         * instance to be constructed.
         *
         * @param consumer the {@code Consumer} that owns the token
         * @return the next builder in the chain
         */
        @SuppressWarnings("unchecked")
        public final B consumer(Consumer consumer) {
            this.consumer = checkNotNull(consumer, "consumer");
            return (B) this;
        }

        /**
         * Sets the {@code properties} for the request token and returns {@code this} builder.
         *
         * @param properties {@code Map<String, String>} of properties to associate with the request token
         * @return {@code this} builder
         */
        @SuppressWarnings("unchecked")
        public final B properties(Map<String, String> properties) {
            if (properties != null) {
                this.properties = properties;
            }
            return (B) this;
        }

        public abstract T build();
    }

    protected enum Type {
        REQUEST, ACCESS
    }
}
