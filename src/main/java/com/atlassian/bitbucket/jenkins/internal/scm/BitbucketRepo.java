package com.atlassian.bitbucket.jenkins.internal.scm;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class BitbucketRepo {

    private final String projectNameOrKey;
    private final String repoNameOrSlug;

    public BitbucketRepo(String projectNameOrKey, String repoNameOrSlug) {
        this.projectNameOrKey = projectNameOrKey;
        this.repoNameOrSlug = repoNameOrSlug;
    }

    public String getProjectNameOrKey() {
        return projectNameOrKey;
    }

    public String getRepoNameOrSlug() {
        return repoNameOrSlug;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketRepo that = (BitbucketRepo) o;

        return new EqualsBuilder()
                .append(projectNameOrKey, that.projectNameOrKey)
                .append(repoNameOrSlug, that.repoNameOrSlug)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(projectNameOrKey)
                .append(repoNameOrSlug)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("projectNameOrKey", projectNameOrKey)
                .append("repoNameOrSlug", repoNameOrSlug)
                .toString();
    }
}
