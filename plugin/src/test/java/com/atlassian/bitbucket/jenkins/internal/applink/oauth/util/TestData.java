package com.atlassian.bitbucket.jenkins.internal.applink.oauth.util;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.RSAKeys;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer.SignatureMethod;

import java.net.URI;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

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
}
