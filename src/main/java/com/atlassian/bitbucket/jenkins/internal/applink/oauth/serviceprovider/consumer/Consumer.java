package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer;

import net.jcip.annotations.Immutable;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.net.URI;
import java.security.PublicKey;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

/**
 * <p>Immutable representation of an OAuth consumer.  At a minimum a consumer is required to have a key, a name, and a
 * public key.  A consumer can also be configured to have a description and a default callback URL that will be used
 * if a callback URL is not provided after a user authorizes a request token.</p>
 *
 * <p>Instances of {@code Consumer} objects can be constructed using the builder. For example</p>
 * <pre>
 *   Consumer c = Consumer.key("consumer-key").name("Consumer").publicKey(publicRSAKey).build();
 * </pre>
 */
@Immutable
public final class Consumer {

    private final String key;
    private final String name;
    private final String description;
    private final SignatureMethod signatureMethod;
    private final PublicKey publicKey;
    private final URI callback;
    private final String consumerSecret;

    private Consumer(Builder builder) {
        key = builder.key;
        name = builder.name;
        signatureMethod = builder.signatureMethod;
        publicKey = builder.publicKey;
        description = builder.description;
        callback = builder.callback;
        consumerSecret = builder.consumerSecret;
    }

    /**
     * Static factory method that starts the process of building a {@code Consumer} instance.  Returns an
     * {@code Builder} so the other attribute can be set.
     *
     * @param key unique key used to identify the consumer in requests unauthorized OAuth request tokens
     * @return the builder for constructing the rest of the {@code Consumer} instance
     */
    public static Builder key(String key) {
        return new Builder(requireNonNull(key, "key"));
    }

    /**
     * Returns the unique key used to identify the consumer in requests unauthorized OAuth request tokens.
     *
     * @return the unique key
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the name of the consumer as it will be displayed to the user.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the method the consumer uses to sign requests.
     *
     * @return the signature method
     */
    public SignatureMethod getSignatureMethod() {
        return signatureMethod;
    }

    /**
     * Returns optional RSA public key for the consumer,
     * If the signature method is RSA-SHA1, this key is used in verifying the signature in requests from the consumer.
     *
     * @return the RSA public key for the consumer
     */
    public Optional<PublicKey> getPublicKey() {
        return ofNullable(publicKey);
    }

    /**
     * Returns the optional description of the consumer as it would be displayed to the user, empty if the
     * description was not set.
     *
     * @return the optional description of the consumer as it would be displayed to the user, or
     *         {@link Optional#empty() empty} if the description was not set
     */
    public Optional<String> getDescription() {
        return ofNullable(description);
    }

    /**
     * Returns the default callback {@code URI} used after a request token has been authorized if no callback
     * {@code URI} was provided in the authorization request.
     *
     * @return the default callback {@code URI} used after a request token has been authorized if one is provided,
     *         {@link Optional#empty() empty} otherwise
     */
    public Optional<URI> getCallback() {
        return ofNullable(callback);
    }

    /**
     * Returns optional secret for the consumer,
     * If the signature method is HMAC_SHA1, this secret is used in verifying the signature in requests from the consumer.
     *
     * @return the secret
     */
    public Optional<String> getConsumerSecret() {
        return ofNullable(consumerSecret);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("key", key)
                .append("name", name)
                .append("description", description)
                .append("callback", callback)
                .append("signatureMethod", signatureMethod)
                .append("publicKey", publicKey)
                .toString();
    }

    /**
     * The ways in which consumers can sign requests, as per
     *
     * @see <a href="http://oauth.net/core/1.0/#signing_process">OAuth spec, Section 9</a>
     */
    public enum SignatureMethod {
        HMAC_SHA1, RSA_SHA1
    }

    /**
     * Builder allowing the optional attributes of the {@code Consumer} object under construction to be set and
     * construction of the final {@code Consumer} instance.
     * <p>
     * We only support 3 legged authentication at the moment
     */
    public static final class Builder {

        private final String key;

        private String name;
        private SignatureMethod signatureMethod;
        private PublicKey publicKey;
        private String description = "";
        private URI callback;
        private String consumerSecret;

        public Builder(String key) {
            this.key = requireNonNull(key, "key");
        }

        /**
         * Sets the {@code name} attribute of the {@code Consumer} object under construction and returns {@code this}
         * builder to allow other attributes to be set
         *
         * @param name value to be used as the {@code name} attribute of the {@code Consumer} being constructed
         * @return {@code this} builder
         */
        public Builder name(String name) {
            this.name = requireNonNull(name);
            return this;
        }

        /**
         * Sets the {@code signatureMethod} attribute of the {@code Consumer} object under construction and returns
         * {@code this} builder to allow other attributes to be set
         *
         * @param signatureMethod {@code SignatureMethod} to be used when signing requests as this consumer
         * @return {@code this} builder
         */
        public Builder signatureMethod(SignatureMethod signatureMethod) {
            this.signatureMethod = requireNonNull(signatureMethod);
            return this;
        }

        /**
         * Sets the {@code publicKey} attribute of the {@code Consumer} object under construction and returns
         * {@code this} builder to allow other attributes to be set
         *
         * @param publicKey RSA {@code PublicKey} to be used as the {@code publicKey} attribute of the {@code Consumer}
         *                  being constructed
         * @return {@code this} builder
         */
        public Builder publicKey(PublicKey publicKey) {
            this.signatureMethod = SignatureMethod.RSA_SHA1;
            this.publicKey = requireNonNull(publicKey);
            return this;
        }

        /**
         * Sets the description of the consumer as it would be displayed to the user and returns
         * {@code this} builder to allow other attributes to be set
         *
         * @param description the description of the consumer as it would be displayed to the user
         * @return {@code this} builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the default callback URL used after a request token has been authorized and returns
         * {@code this} builder to allow other attributes to be set
         *
         * @param callback the default callback URI used after a request token has been authorized
         * @return {@code this} builder
         */
        public Builder callback(URI callback) {
            this.callback = callback;
            return this;
        }

        /**
         * Sets the consumer secret which is used in HMAC_SHA1 signature method and possibly other symmetric crypto methods.
         *
         * @param secret the secret
         * @return {@code this} builder
         */
        public Builder consumerSecret(String secret) {
            this.consumerSecret = secret;
            return this;
        }

        /**
         * Constructs and returns the final{@code Consumer} instance.
         *
         * @return the final {@code Consumer} instance
         */
        public Consumer build() {
            requireNonNull(name, "name");
            requireNonNull(signatureMethod, "signatureMethod");
            if (signatureMethod == SignatureMethod.RSA_SHA1) {
                requireNonNull(publicKey, "publicKey must be set when the signature method is RSA-SHA1");
            }
            return new Consumer(this);
        }
    }
}
