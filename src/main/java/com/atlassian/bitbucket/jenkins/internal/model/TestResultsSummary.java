package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestResultsSummary {

    private int successful;
    private int failed;
    private int ignored;

    public TestResultsSummary(int successful, int failed, int ignored) {
        this.successful = successful;
        this.failed = failed;
        this.ignored = ignored;
    }

    @JsonProperty(value = "successful")
    public int getSuccessful() {
        return successful;
    }

    @JsonProperty(value = "failed")
    public int getFailed() {
        return failed;
    }

    @JsonProperty(value = "ignored")
    public int getIgnored() {
        return ignored;
    }
}
