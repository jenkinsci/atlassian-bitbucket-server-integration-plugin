package com.atlassian.bitbucket.jenkins.internal.provider;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public interface InstanceKeyPairProvider {

    /**
     * Returns a private RSA key associated with this instance
     * @return the private key
     */
    RSAPrivateKey getPrivate();

    /**
     * Returns a public RSA key associated with this instance
     * @return the public key
     */
    RSAPublicKey getPublic();
}
