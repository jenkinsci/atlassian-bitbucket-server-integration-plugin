package com.atlassian.bitbucket.jenkins.internal.provider;

import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

import javax.inject.Singleton;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Singleton
public class DefaultInstanceKeyPairProvider implements InstanceKeyPairProvider {

    @Override
    public RSAPrivateKey getPrivate() {
        return InstanceIdentity.get().getPrivate();
    }

    @Override
    public RSAPublicKey getPublic() {
        return InstanceIdentity.get().getPublic();
    }
}
