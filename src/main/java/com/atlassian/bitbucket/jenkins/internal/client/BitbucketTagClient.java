package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketTag;

import java.util.stream.Stream;

public interface BitbucketTagClient {

    Stream<BitbucketTag> getRemoteTags();
}
