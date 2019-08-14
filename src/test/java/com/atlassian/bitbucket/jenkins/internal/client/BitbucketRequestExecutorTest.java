package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.NoContentException;
import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import org.junit.Test;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.OBJECT_MAPPER;

public class BitbucketRequestExecutorTest {

    private static final String BITBUCKET_BASE_URL = "http://localhost:7990/bitbucket";

    private final FakeRemoteHttpServer fakeRemoteHttpServer = new FakeRemoteHttpServer();
    private final HttpRequestExecutor requestExecutor = new HttpRequestExecutorImpl(fakeRemoteHttpServer);
    private final BitbucketRequestExecutor bitbucketRequestExecutor =
            new BitbucketRequestExecutor(BITBUCKET_BASE_URL, requestExecutor, OBJECT_MAPPER, ANONYMOUS_CREDENTIALS);

    @Test(expected = NoContentException.class)
    public void testNoBody() {
        // test that all the handling logic does not fail if there is no body available, this just
        // checks that no exceptions are thrown.
        fakeRemoteHttpServer.mapUrlToResult(BITBUCKET_BASE_URL, null);

        bitbucketRequestExecutor.makeGetRequest(bitbucketRequestExecutor.getBaseUrl(), Object.class);
    }
}