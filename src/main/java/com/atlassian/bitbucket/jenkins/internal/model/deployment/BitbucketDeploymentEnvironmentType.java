package com.atlassian.bitbucket.jenkins.internal.model.deployment;

import com.atlassian.bitbucket.jenkins.internal.deployments.Messages;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.CheckForNull;
import java.util.Arrays;
import java.util.Optional;

import static java.util.Optional.empty;

/**
 * The types of environments available via the Bitbucket Server API.
 *
 * @since deployments
 */
public enum BitbucketDeploymentEnvironmentType {

    DEVELOPMENT(Messages.BitbucketDeploymentEnvironmentType_DEVELOPMENT(), 3),
    PRODUCTION(Messages.BitbucketDeploymentEnvironmentType_PRODUCTION(), 0),
    STAGING(Messages.BitbucketDeploymentEnvironmentType_STAGING(), 1),
    TESTING(Messages.BitbucketDeploymentEnvironmentType_TESTING(), 2);

    private final String displayName;
    private final int weight;

    BitbucketDeploymentEnvironmentType(String displayName, int weight) {
        this.displayName = displayName;
        this.weight = weight;
    }

    public static Optional<BitbucketDeploymentEnvironmentType> fromName(@CheckForNull String name) {
        if (StringUtils.isBlank(name)) {
            return empty();
        }
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }
}
