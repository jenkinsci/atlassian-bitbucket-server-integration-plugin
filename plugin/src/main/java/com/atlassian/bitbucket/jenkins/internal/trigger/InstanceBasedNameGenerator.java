package com.atlassian.bitbucket.jenkins.internal.trigger;

import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

import javax.inject.Inject;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;

/**
 * Generates Unique name for the current Jenkins instance.
 */
public class InstanceBasedNameGenerator {

    private final String uniqueName;

    @Inject
    public InstanceBasedNameGenerator(InstanceIdentity instanceIdentity) {
        requireNonNull(instanceIdentity);
        uniqueName = sha1Hex(instanceIdentity.getPublic().getEncoded());
    }

    public String getUniqueName() {
        return uniqueName;
    }
}
