package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.*;

import java.util.ArrayList;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * @since 4.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketCommit {

    public ArrayList<BitbucketValueProperty> values;

    @JsonCreator
    public BitbucketCommit(@JsonProperty("values") ArrayList<BitbucketValueProperty> values) {
        this.values = requireNonNull(values, "values");

    }

    public long getCommitterTimestamp() {
        return values.get(0).getCommitterTimestamp();
    }

    public String getDisplayId() {
        return values.get(0).getDisplayId();
    }

    public final String getId() {
        return values.get(0).getId();
    }

    public String getMessage() {
        return values.get(0).getMessage();
    }
}
