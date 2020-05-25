package com.atlassian.bitbucket.jenkins.internal.provider;

import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

public interface InstanceIdentityProvider {

    /**
     * @return the InstanceIdentity instance
     */
    InstanceIdentity getInstanceIdentity();
}
