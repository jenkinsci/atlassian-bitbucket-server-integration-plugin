package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketSingleTag {

    public ArrayList<BitbucketValueProperty> values;

    @JsonCreator
    public BitbucketSingleTag(@JsonProperty("values") ArrayList<BitbucketValueProperty> values) {
        this.values = requireNonNull(values, "values");

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketSingleTag that = (BitbucketSingleTag) o;
        return Objects.equals(getId(), that.getId()) &&
                Objects.equals(getDisplayId(), that.getDisplayId()) &&
                Objects.equals(getCommitterTimestamp(), that.getCommitterTimestamp()) &&
                Objects.equals(getMessage(), that.getMessage());
    }

    public final long getCommitterTimestamp() {
        return values.get(0).getCommitterTimestamp();
    }

    public final String getDisplayId() {
        return values.get(0).getDisplayId();
    }

    public final String getId() {
        return values.get(0).getId();
    }

    public final String getMessage() {
        return values.get(0).getMessage();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getDisplayId(), getCommitterTimestamp(), getMessage());
    }
}
