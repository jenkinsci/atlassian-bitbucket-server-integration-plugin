package com.atlassian.bitbucket.jenkins.internal.model.deployment;

import com.atlassian.bitbucket.jenkins.internal.deployments.Messages;

import javax.annotation.CheckForNull;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * The types of environments available via the Bitbucket Server API.
 *
 * @since 3.1.0
 */
public enum BitbucketDeploymentEnvironmentType {

    DEVELOPMENT(Messages.BitbucketDeploymentEnvironmentType_DEVELOPMENT(), 3),
    PRODUCTION(Messages.BitbucketDeploymentEnvironmentType_PRODUCTION(), 0),
    STAGING(Messages.BitbucketDeploymentEnvironmentType_STAGING(), 1),
    TESTING(Messages.BitbucketDeploymentEnvironmentType_TESTING(), 2);

    private static final Map<String, BitbucketDeploymentEnvironmentType> types =
            Stream.of(values()).collect(toMap(t -> t.name().toUpperCase(Locale.US), identity()));

    private final String displayName;
    private final int weight;

    BitbucketDeploymentEnvironmentType(String displayName, int weight) {
        this.displayName = displayName;
        this.weight = weight;
    }

    public static Optional<BitbucketDeploymentEnvironmentType> fromName(@CheckForNull String name) {
        if (isBlank(name)) {
            return empty();
        }
        return ofNullable(types.get(name.toUpperCase(Locale.US)));
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }
}
