package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCommit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketCommitClientImplTest {

    private static final String PROJECT_KEY = "PROJECT_1";
    private static final String REPO_SLUG = "rep_1";
    private static final String COMMITS_URL = "%s/rest/api/1.0/projects/%s/repos/%s/commits?until=%s&start=0&limit=1";
    private final FakeRemoteHttpServer fakeRemoteHttpServer = new FakeRemoteHttpServer();
    private final HttpRequestExecutor requestExecutor = new HttpRequestExecutorImpl(fakeRemoteHttpServer);
    private final BitbucketRequestExecutor bitbucketRequestExecutor = new BitbucketRequestExecutor(BITBUCKET_BASE_URL,
            requestExecutor, OBJECT_MAPPER, ANONYMOUS_CREDENTIALS);
    @Mock
    private BitbucketCapabilitiesClient capabilitiesClient;
    private BitbucketRepositoryClientImpl client;

    @Before
    public void setup() {
        client = new BitbucketRepositoryClientImpl(bitbucketRequestExecutor,
                capabilitiesClient, PROJECT_KEY, REPO_SLUG);
    }

    @Test
    public void testGetCommit() throws UnsupportedEncodingException {
        String commitId = "feature/myfeature";
        String encodedCommitId = URLEncoder.encode(commitId, "UTF-8");
        String response = readFileToString("/commit.json");
        String url = format(COMMITS_URL, BITBUCKET_BASE_URL, PROJECT_KEY, REPO_SLUG, encodedCommitId);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        BitbucketCommitClient commitClient = client.getCommitClient();
        BitbucketCommit commit = commitClient.getCommit(commitId);

        assertEquals("559aa7ba386219254f9448ed24cdaa6e914e5fde", commit.getId());
        assertEquals("feature/myfeature", commit.getDisplayId());
        assertEquals("Commit message", commit.getMessage());
        assertEquals(1421908805000L, commit.getCommitterTimestamp());
    }

    @Test
    public void testGetCommitNotFound() throws UnsupportedEncodingException {
        String commitId = "doesnotexist";
        String encodedCommitId = URLEncoder.encode(commitId, "UTF-8");
        String response = readFileToString("/missing-commit.json");
        String url = format(COMMITS_URL, BITBUCKET_BASE_URL, PROJECT_KEY, REPO_SLUG, encodedCommitId);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        BitbucketCommitClient commitClient = client.getCommitClient();
        BitbucketCommit commit = commitClient.getCommit(commitId);

        assertNull(commit);
    }
}
