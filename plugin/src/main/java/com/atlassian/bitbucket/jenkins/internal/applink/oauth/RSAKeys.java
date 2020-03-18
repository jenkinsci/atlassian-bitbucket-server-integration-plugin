package com.atlassian.bitbucket.jenkins.internal.applink.oauth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

public class RSAKeys {

    private static final String RSA = "RSA";

    /**
     * Returns a newly created RSA public/private {@code KeyPair}.
     *
     * @return newly created RSA public/private {@code KeyPair}
     * @throws NoSuchAlgorithmException thrown if there are no RSA providers available
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        final KeyPairGenerator gen = KeyPairGenerator.getInstance(RSA);
        return gen.generateKeyPair();
    }
}
