package com.atlassian.bitbucket.jenkins.internal.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.equalTo;

public class BitbucketBuildStatusTest {

    private static TestResults TEST_RESULTS = new TestResults(3, 2, 1);
    
    @Test
    public void testBuild() {
        BitbucketBuildStatus expected = new BitbucketBuildStatus(
                "1", "description", 500L, "key", "name", "parent", "refs/head/master", BuildState.SUCCESSFUL, TEST_RESULTS, "url");
        
        BitbucketBuildStatus status = new BitbucketBuildStatus.Builder("key", BuildState.SUCCESSFUL, "url")
                .setBuildNumber("1")
                .setDescription("description")
                .setDuration(500L)
                .setName("name")
                .setParent("parent")
                .setRef("refs/head/master")
                .setTestResults(TEST_RESULTS)
                .build();
        
        assertThat(status, equalTo(expected));
    }
    
    @Test
    public void testBuildWithCancelledState() {
        BitbucketBuildStatus expected = new BitbucketBuildStatus(
                "1", "description", 500L, "key", "name", "parent", "refs/head/master", BuildState.CANCELLED, TEST_RESULTS, "url");

        BitbucketBuildStatus status = new BitbucketBuildStatus.Builder("key", BuildState.CANCELLED, "url")
                .setBuildNumber("1")
                .setDescription("description")
                .setDuration(500L)
                .setName("name")
                .setParent("parent")
                .setRef("refs/head/master")
                .setTestResults(TEST_RESULTS)
                .build();

        assertThat(status, equalTo(expected));
    }

    @Test
    public void testBuildLegacy() {
        BitbucketBuildStatus expected = new BitbucketBuildStatus(
                null, "description", null, "key", "name", null, null, BuildState.SUCCESSFUL, null, "url");

        BitbucketBuildStatus legacyStatus = new BitbucketBuildStatus.Builder("key", BuildState.SUCCESSFUL, "url")
                .setDescription("description")
                .setName("name")
                .legacy()
                .build();

        assertThat(legacyStatus, equalTo(expected));
    }

    @Test
    public void testBuildLegacyWithCancelledState() {
        BitbucketBuildStatus expected = new BitbucketBuildStatus(
                null, "description", null, "key", "name", null, null, BuildState.FAILED, null, "url");

        BitbucketBuildStatus legacyStatus = new BitbucketBuildStatus.Builder("key", BuildState.CANCELLED, "url")
                .setDescription("description")
                .setName("name")
                .legacy()
                .build();

        assertThat(legacyStatus, equalTo(expected));
    }

    @Test
    public void testBuildLegacyAndNoCancelledState() {
        BitbucketBuildStatus expected = new BitbucketBuildStatus(
                null, "description", null, "key", "name", null, null, BuildState.FAILED, null, "url");

        BitbucketBuildStatus legacyNoCancelledStatus = new BitbucketBuildStatus.Builder("key", BuildState.CANCELLED, "url")
                .setBuildNumber("1")
                .setDescription("description")
                .setDuration(500L)
                .setName("name")
                .setParent("parent")
                .setRef("refs/head/master")
                .setTestResults(TEST_RESULTS)
                .legacy()
                .noCancelledState()
                .build();

        assertThat(legacyNoCancelledStatus, equalTo(expected));
    }

    @Test
    public void testBuildLegacyWithUnsupportedInfo() {
        BitbucketBuildStatus expected = new BitbucketBuildStatus(
                null, "description", null, "key", "name", null, null, BuildState.SUCCESSFUL, null, "url");

        BitbucketBuildStatus legacyStatus = new BitbucketBuildStatus.Builder("key", BuildState.SUCCESSFUL, "url")
                .setBuildNumber("1")
                .setDescription("description")
                .setDuration(500L)
                .setName("name")
                .setParent("parent")
                .setRef("refs/head/master")
                .setTestResults(TEST_RESULTS)
                .legacy()
                .build();

        assertThat(legacyStatus, equalTo(expected));
    }

    @Test
    public void testBuildNoCancelledState() {
        BitbucketBuildStatus expected = new BitbucketBuildStatus(
                "1", "description", 500L, "key", "name", "parent", "refs/head/master", BuildState.SUCCESSFUL, TEST_RESULTS, "url");

        BitbucketBuildStatus noCancelledStatus = new BitbucketBuildStatus.Builder("key", BuildState.SUCCESSFUL, "url")
                .setBuildNumber("1")
                .setDescription("description")
                .setDuration(500L)
                .setName("name")
                .setParent("parent")
                .setRef("refs/head/master")
                .setTestResults(TEST_RESULTS)
                .noCancelledState()
                .build();

        assertThat(noCancelledStatus, equalTo(expected));
    }

    @Test
    public void testBuildNoCancelledStateWithCancelledState() {
        BitbucketBuildStatus expected = new BitbucketBuildStatus(
                "1", "description", 500L, "key", "name", "parent", "refs/head/master", BuildState.FAILED, TEST_RESULTS, "url");

        BitbucketBuildStatus noCancelledState = new BitbucketBuildStatus.Builder("key", BuildState.CANCELLED, "url")
                .setBuildNumber("1")
                .setDescription("description")
                .setDuration(500L)
                .setName("name")
                .setParent("parent")
                .setRef("refs/head/master")
                .setTestResults(TEST_RESULTS)
                .noCancelledState()
                .build();

        assertThat(noCancelledState, equalTo(expected));
    }
}