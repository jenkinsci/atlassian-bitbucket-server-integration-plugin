package com.atlassian.bitbucket.jenkins.internal.applink.oauth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.signature.RSA_SHA1;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Underlying net.oauth library is not test friendly and relies on properties. It is assumed that the client of library
 * knows about keys for these properties. Classes like {@link Consumer} fixes this by encapsulating these details. This
 * class acts as a bridge between the library and local classes.
 */
public class OAuthConverter {

    /***
     *
     * Creates an OAuth Accessor based on input token
     *
     * @param token input token
     * @return Oauth accessor
     */
    public static OAuthAccessor createOAuthAccessor(ServiceProviderToken token) {
        requireNonNull(token, "token");

        OAuthAccessor accessor = new OAuthAccessor(OAuthConverter.toOAuthConsumer(token.getConsumer()));
        setTokenData(accessor, token);
        return accessor;
    }

    /**
     * Converts our {@code consumer} to OAuthConsumer
     *
     * @param consumer the consumer
     * @return the OAuthConsumer
     */
    public static OAuthConsumer toOAuthConsumer(Consumer consumer) {
        String callback = consumer.getCallback() != null ? consumer.getCallback().toString() : null;
        OAuthConsumer oauthConsumer = new OAuthConsumer(callback,
                consumer.getKey(),
                consumer.getConsumerSecret().orElse(null),
                null);
        oauthConsumer.setProperty(ConsumerProperty.NAME, consumer.getName());
        oauthConsumer.setProperty(ConsumerProperty.DESCRIPTION, consumer.getDescription());
        if (consumer.getSignatureMethod() == Consumer.SignatureMethod.RSA_SHA1) {
            oauthConsumer.setProperty(OAuth.OAUTH_SIGNATURE_METHOD, OAuth.RSA_SHA1);
            oauthConsumer.setProperty(RSA_SHA1.PUBLIC_KEY, consumer.getPublicKey());
        } else {
            oauthConsumer.setProperty(OAuth.OAUTH_SIGNATURE_METHOD, OAuth.HMAC_SHA1);
        }
        return oauthConsumer;
    }

    private static void setTokenData(OAuthAccessor accessor, ServiceProviderToken token) {
        setCommonTokenData(accessor, token);
        if (token.isRequestToken()) {
            if (token.getAuthorization() == ServiceProviderToken.Authorization.AUTHORIZED) {
                accessor.setProperty(AccessorProperty.USER, token.getUser());
                accessor.setProperty(AccessorProperty.AUTHORIZED, true);
            } else if (token.getAuthorization() == ServiceProviderToken.Authorization.DENIED) {
                accessor.setProperty(AccessorProperty.USER, token.getUser());
                accessor.setProperty(AccessorProperty.AUTHORIZED, false);
            }
        } else {
            accessor.accessToken = token.getToken();
            accessor.setProperty(AccessorProperty.USER, token.getUser());
            accessor.setProperty(AccessorProperty.AUTHORIZED, true);
        }
        accessor.tokenSecret = token.getTokenSecret();
        accessor.setProperty(AccessorProperty.CREATION_TIME, token.getCreationTime());
    }

    private static void setCommonTokenData(OAuthAccessor accessor, Token token) {
        if (token.isRequestToken()) {
            accessor.requestToken = token.getToken();
        } else {
            accessor.accessToken = token.getToken();
        }
        accessor.tokenSecret = token.getTokenSecret();
        for (Map.Entry<String, String> property : token.getProperties().entrySet()) {
            accessor.setProperty(property.getKey(), property.getValue());
        }
    }

    public static final class ConsumerProperty {

        public static final String NAME = "name";
        public static final String DESCRIPTION = "description";
    }

    private static final class AccessorProperty {

        private static final String USER = "user";
        private static final String AUTHORIZED = "authorized";
        private static final String VERIFIER = "verifier";
        private static final String CALLBACK = "callback";
        private static final String CREATION_TIME = "creationTime";
    }
}
