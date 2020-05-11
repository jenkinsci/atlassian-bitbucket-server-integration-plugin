package com.atlassian.bitbucket.jenkins.internal.provider;

import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

public interface InstanceIdentityProvider {

    /**
     * Returns the result of calling InstanceIdentity.get();
     *
     * @return the InstanceIdentity instance
     */
    InstanceIdentity getInstanceIdentity();
}
