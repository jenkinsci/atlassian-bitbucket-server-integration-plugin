package com.atlassian.bitbucket.jenkins.internal.link;

import java.util.function.Supplier;

public enum BitbucketLinkType {

    BRANCH(Messages::bitbucket_link_type_branch),
    REPOSITORY(Messages::bitbucket_link_type_repository);

    private final Supplier<String> displayNameProvider;

    BitbucketLinkType(Supplier<String> displayNameProvider) {
        this.displayNameProvider = displayNameProvider;
    }

    public String getDisplayName() {
        return displayNameProvider.get();
    }
}
