package com.atlassian.bitbucket.jenkins.internal.applink.oauth.util;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.RSAKeys;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer.SignatureMethod;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;

import java.net.URI;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.Session.newSession;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.Consumers.RSA_CONSUMER;

public class TestData {

    public static final String USER = "bob";
    public static final KeyPair KEYS;

    static {
        try {
            KEYS = RSAKeys.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static final class Consumers {

        public static final Consumer RSA_CONSUMER = Consumer.key("consumer-rsa")
                .name("Consumer using RSA")
                .description("description")
                .signatureMethod(SignatureMethod.RSA_SHA1)
                .publicKey(KEYS.getPublic())
                .callback(URI.create("http://consumer/callback"))
                .build();

        public static final Consumer RSA_CONSUMER_WITH_2LO = Consumer.key("consumer-rsa-with-2lo")
                .name("Consumer using RSA")
                .description("description")
                .signatureMethod(SignatureMethod.RSA_SHA1)
                .publicKey(KEYS.getPublic())
                .callback(URI.create("http://consumer/callback"))
                .build();
    }

    public static final class Tokens {

        public static final String TOKEN_VALUE = "1234";
        public static final String TOKEN_SECRET = "5678";
        public static final String VERIFIER = "9876";
        public static final String SESSION_HANDLE = "abcd";
        public static final ServiceProviderToken UNAUTHORIZED_REQUEST_TOKEN =
                ServiceProviderToken.newRequestToken(TOKEN_VALUE).tokenSecret(TOKEN_SECRET).consumer(RSA_CONSUMER).build();

        public static final ServiceProviderToken AUTHORIZED_REQUEST_TOKEN =
                UNAUTHORIZED_REQUEST_TOKEN.authorize(USER, VERIFIER);

        public static final ServiceProviderToken ACCESS_TOKEN = createAccessTokenForUser(USER);

        public static ServiceProviderToken createAccessTokenForUser(String user) {
            return ServiceProviderToken.newAccessToken(TOKEN_VALUE)
                    .tokenSecret(TOKEN_SECRET)
                    .consumer(RSA_CONSUMER)
                    .authorizedBy(user)
                    .session(newSession(SESSION_HANDLE).build()).build();
        }
    }
}
