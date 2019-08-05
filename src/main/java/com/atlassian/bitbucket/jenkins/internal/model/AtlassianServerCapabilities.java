package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

/**
 * Capabilities as advertised by Atlassian products.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AtlassianServerCapabilities {

    public static final String WEBHOOK_CAPABILITY_KEY = "webhooks";

    private final String application;
    private final Map<String, String> capabilities;

    @JsonCreator
    public AtlassianServerCapabilities(@JsonProperty(value = "application") String application,
                                       @JsonProperty(value = "capabilities") Map<String, String> capabilities) {
        this.application = application;
        this.capabilities = Collections.unmodifiableMap(capabilities);
    }

    /**
     * Return the application type as provided by the remote side.
     *
     * @return the application type, or null if it was not part of the response
     */
    @Nullable
    public String getApplication() {
        return application;
    }

    /**
     * @return true if the server claims to be a Bitbucket server instance. False if the application
     *         type was not provided or if it is some other Atlassian server
     */
    public boolean isBitbucketServer() {
        return "stash".equals(application);
    }

    public Map<String, String> getCapabilities() {
        return capabilities;
    }
}
