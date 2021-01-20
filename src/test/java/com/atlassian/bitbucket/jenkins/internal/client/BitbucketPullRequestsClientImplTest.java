package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import org.junit.Test;

import java.util.List;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static okhttp3.HttpUrl.parse;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.*;

public class BitbucketPullRequestsClientImplTest {

    private static final String WEBHOOK_URL = "%s/rest/api/1.0/projects/%s/repos/%s/pull-requests?state=OPEN";
    private static final String projectKey = "proj";
    private static final String repoSlug = "repo";

    private final FakeRemoteHttpServer fakeRemoteHttpServer = new FakeRemoteHttpServer();
    private final HttpRequestExecutor requestExecutor = new HttpRequestExecutorImpl(fakeRemoteHttpServer);
    private final BitbucketRequestExecutor bitbucketRequestExecutor = new BitbucketRequestExecutor(BITBUCKET_BASE_URL,
            requestExecutor, OBJECT_MAPPER, ANONYMOUS_CREDENTIALS);
    private BitbucketPullRequestsClientImpl client =
            new BitbucketPullRequestsClientImpl(bitbucketRequestExecutor, projectKey, repoSlug);

    @Test
    public void testFetchingOfExistingOpenPullRequests() {
        String response = readFileToString("/open-pull-requests.json");
        String url = format(WEBHOOK_URL, BITBUCKET_BASE_URL, projectKey, repoSlug);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        List<BitbucketPullRequest> pullRequests = client.getOpenPullRequests().collect(toList());

        assertThat(pullRequests.size(), is(equalTo(2)));
        assertThat(pullRequests.stream().map(BitbucketPullRequest::getId).collect(toSet()), hasItems(96, 97));
         assertThat(pullRequests.stream().map(BitbucketPullRequest::getState).collect(toSet()), hasItems(BitbucketPullState.OPEN));
    }

    @Test
    public void testNextPageFetching() {
        BitbucketPullRequestsClientImpl.NextPageFetcherImpl fetcher = new BitbucketPullRequestsClientImpl.NextPageFetcherImpl(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor);
        int nextPageStart = 2;
        fakeRemoteHttpServer.mapUrlToResult(
                BITBUCKET_BASE_URL + "?start=" + nextPageStart,
                readFileToString("/open-pull-requests-last-page.json"));
        BitbucketPage<BitbucketPullRequest> firstPage = new BitbucketPage<>();
        firstPage.setNextPageStart(nextPageStart);

        BitbucketPage<BitbucketPullRequest> next = fetcher.next(firstPage);
        List<BitbucketPullRequest> values = next.getValues();
        assertEquals(next.getSize(), values.size());
        assertTrue(next.getSize() > 0);

        assertThat(values.stream().map(BitbucketPullRequest::getId).collect(toSet()), hasItems(96, 97));
        assertThat(next.isLastPage(), is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLastPageDoesNotHaveNext() {
        BitbucketPullRequestsClientImpl.NextPageFetcherImpl fetcher = new BitbucketPullRequestsClientImpl.NextPageFetcherImpl(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor);
        BitbucketPage<BitbucketPullRequest> page = new BitbucketPage<>();
        page.setLastPage(true);

        fetcher.next(page);
    }
}