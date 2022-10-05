package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.http.RetryOnRateLimitConfig;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BuildState;
import com.atlassian.bitbucket.jenkins.internal.model.TestResults;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.annotation.Nullable;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketBuildStatusClientImplTest {

    @Mock
    BitbucketRequestExecutor bitbucketRequestExecutor;

    String revisionSha = "revisionSha";

    BitbucketBuildStatusClientImpl bitbucketBuildStatusClient;

    @Before
    public void setup() {
        when(bitbucketRequestExecutor.getBaseUrl()).thenReturn(HttpUrl.parse("http://example.com"));
        bitbucketBuildStatusClient = new BitbucketBuildStatusClientImpl(bitbucketRequestExecutor, revisionSha);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPost() {
        BitbucketBuildStatus.Builder buildStatusBuilder = createTestBuildStatus("ref");
        BitbucketBuildStatus buildStatus = buildStatusBuilder.legacy().build();
        Consumer<BitbucketBuildStatus> consumer = (Consumer<BitbucketBuildStatus>) mock(Consumer.class);

        bitbucketBuildStatusClient.post(buildStatusBuilder, consumer);

        HttpUrl url = HttpUrl.parse("http://example.com/rest/build-status/1.0/commits/revisionSha");

        verify(bitbucketRequestExecutor).makePostRequest(eq(url), eq(buildStatus), ArgumentMatchers.any(RetryOnRateLimitConfig.class));
        verify(consumer).accept(buildStatus);
    }

    private BitbucketBuildStatus.Builder createTestBuildStatus(@Nullable String ref) {
        return new BitbucketBuildStatus.Builder("REPO-42", BuildState.CANCELLED, "http://example.com/builds/repo-42")
                .setDescription("Test description")
                .setTestResults(new TestResults(42, 21, 12))
                .setName("Test-Name")
                .setDuration(21L)
                .setRef(StringUtils.stripToNull(ref));
    }

}