package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A user in Bitbucket
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketUser {

    private final String displayName;
    private final String emailAddress;
    private final String name;

    @JsonCreator
    public BitbucketUser(
            @JsonProperty(value = "name", required = true) String name,
            @JsonProperty(value = "emailAddress") String emailAddress,
            @JsonProperty(value = "displayName", required = true) String displayName) {
        this.name = requireNonNull(name, "name");
        this.emailAddress = requireNonNull(emailAddress, "emailAddress");
        this.displayName = requireNonNull(displayName, "displayName");
    }

    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public String getEmailAddress() {
        return emailAddress;
    }

    public String getName() {
        return name;
    }
}
