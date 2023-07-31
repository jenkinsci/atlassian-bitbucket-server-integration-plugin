package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.lang.String.format;
import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketBranchClientImplTest {

    private static final String BRANCHES_URL = "%s/rest/api/1.0/projects/%s/repos/%s/branches?filterTest=";
    private static final String PROJECT_KEY = "PROJECT_1";
    private static final String REPO_SLUG = "rep_1";

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
    public void testGetRemoteBranches() {
        String response = readFileToString("/branches.json");
        String url = format(BRANCHES_URL, BITBUCKET_BASE_URL, PROJECT_KEY, REPO_SLUG);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        BitbucketBranchClient branchClient = client.getBranchClient();
        List<BitbucketDefaultBranch> branchList = branchClient.getRemoteBranches().collect(Collectors.toList());

        assertEquals(branchList.size(), 1);
        assertEquals(branchList.get(0).getDisplayId(), "master");
    }
}
