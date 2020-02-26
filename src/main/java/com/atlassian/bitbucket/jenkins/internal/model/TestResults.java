package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestResults {

    private TestResultsSummary summary;

    public TestResults(@Nullable TestResultsSummary summary) {
        this.summary = summary;
    }

    @JsonProperty(value = "summary")
    @Nullable
    public TestResultsSummary getSummary() {
        return summary;
    }
}
