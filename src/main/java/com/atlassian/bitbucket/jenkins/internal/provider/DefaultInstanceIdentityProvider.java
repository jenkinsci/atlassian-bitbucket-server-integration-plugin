package com.atlassian.bitbucket.jenkins.internal.provider;

import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

import javax.inject.Singleton;

@Singleton
public class DefaultInstanceIdentityProvider implements InstanceIdentityProvider {

    @Override
    public InstanceIdentity getInstanceIdentity() {
        return InstanceIdentity.get();
    }
}
