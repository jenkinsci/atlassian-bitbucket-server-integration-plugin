package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
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
import static java.util.stream.Collectors.toSet;
import static okhttp3.HttpUrl.parse;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.*;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketBranchClientImplTest {

    private static final String BRANCHES_URL = "%s/rest/api/1.0/projects/%s/repos/%s/branches";
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

    @Test
    public void testNextPageFetching() {
        BitbucketBranchClientImpl.NextPageFetcherImpl fetcher = new BitbucketBranchClientImpl.NextPageFetcherImpl(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor);
        int nextPageStart = 2;
        fakeRemoteHttpServer.mapUrlToResult(
                BITBUCKET_BASE_URL + "?start=" + nextPageStart,
                readFileToString("/branches-last-page.json"));
        BitbucketPage<BitbucketDefaultBranch> firstPage = new BitbucketPage<>();
        firstPage.setNextPageStart(nextPageStart);

        BitbucketPage<BitbucketDefaultBranch> next = fetcher.next(firstPage);
        List<BitbucketDefaultBranch> values = next.getValues();
        assertEquals(next.getSize(), values.size());
        assertTrue(next.getSize() > 0);

        assertThat(values.stream().map(BitbucketDefaultBranch::getId).collect(toSet()), hasItems("refs/heads/master", "refs/heads/branch1"));
        assertThat(next.isLastPage(), is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLastPageDoesNotHaveNext() {
        BitbucketBranchClientImpl.NextPageFetcherImpl fetcher = new BitbucketBranchClientImpl.NextPageFetcherImpl(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor);
        BitbucketPage<BitbucketDefaultBranch> page = new BitbucketPage<>();
        page.setLastPage(true);

        fetcher.next(page);
    }
}
