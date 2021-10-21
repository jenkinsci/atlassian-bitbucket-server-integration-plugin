package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.CheckForNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.logging.Logger;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * @since deployments
 */
public class DeploymentStepUtils {

    private static final Logger LOGGER = Logger.getLogger(DeploymentStepUtils.class.getName());

    /**
     * Generates a random UUID to be used as an environment key
     *
     * @return the generated {@code environmentKey}, or a generated one if the provided one is blank
     */
    public static String getOrGenerateEnvironmentKey() {
        return getOrGenerateEnvironmentKey(null);
    }

    /**
     * Gets the provided {@code environmentKey}, stripping away whitespace, or generates a random UUID if a blank
     * {@code environmentKey} was provided.
     *
     * @param environmentKey a unique identifier for the environment, or {@code null} to have one generated
     * @return the provided {@code environmentKey}, or a generated one if the provided one is blank
     */
    public static String getOrGenerateEnvironmentKey(@CheckForNull String environmentKey) {
        if (!isBlank(environmentKey)) {
            return environmentKey;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Converts an environment type String to a {@link BitbucketDeploymentEnvironmentType}
     *
     * @param environmentType the {@link BitbucketDeploymentEnvironmentType#name()}
     * @return the associated {@link BitbucketDeploymentEnvironmentType} or {@code null} if there is none that match
     */
    @CheckForNull
    public static BitbucketDeploymentEnvironmentType normalizeEnvironmentType(@CheckForNull String environmentType) {
        if (isBlank(environmentType)) {
            return null;
        }
        return BitbucketDeploymentEnvironmentType.fromName(environmentType)
                .orElseGet(() -> {
                    LOGGER.warning(format("Invalid environment type '%s'.", environmentType));
                    return null;
                });
    }

    /**
     * Create a {@link BitbucketDeploymentEnvironment}
     *
     * @param step     the {@link DeploymentStep} being executed
     * @param run      the {@link Run}
     * @param listener the {@link TaskListener}
     * @return the {@link BitbucketDeploymentEnvironment} configured by the provided step
     */
    public static BitbucketDeploymentEnvironment getEnvironment(DeploymentStep step, Run<?, ?> run, TaskListener listener) {
        BitbucketDeploymentEnvironmentType type = normalizeEnvironmentType(step.getEnvironmentType());
        String name = getOrGenerateEnvironmentName(step.getEnvironmentName(), type, run, listener);
        URI url = getEnvironmentUri(step.getEnvironmentUrl(), listener);
        return new BitbucketDeploymentEnvironment(step.getEnvironmentKey(), name, type, url);
    }

    @CheckForNull
    private static URI getEnvironmentUri(@CheckForNull String environmentUrl, TaskListener listener) {
        if (isBlank(environmentUrl)) {
            return null;
        }
        try {
            return new URI(environmentUrl);
        } catch (URISyntaxException x) {
            listener.getLogger().println(format("Invalid environment URL '%s'.", environmentUrl));
            return null;
        }
    }

    private static String getOrGenerateEnvironmentName(@CheckForNull String environmentName, @CheckForNull BitbucketDeploymentEnvironmentType environmentType,
                                                       Run<?, ?> run, TaskListener listener) {
        if (!isBlank(environmentName)) {
            return environmentName;
        }
        String generatedEnvironmentName;
        if (environmentType != null) {
            // Default to the environment type display name if there is a configured environment type
            generatedEnvironmentName = environmentType.getDisplayName();
        } else {
            // Otherwise default to the project's display name
            generatedEnvironmentName = run.getParent().getDisplayName();
        }
        listener.getLogger().println(format("Using '%s' as the environment name since it was not correctly configured. Please configure an environment name.", generatedEnvironmentName));
        return generatedEnvironmentName;
    }
}
