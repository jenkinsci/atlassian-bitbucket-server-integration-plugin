package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import org.junit.Test;

import java.util.List;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static okhttp3.HttpUrl.parse;
import static java.lang.String.format;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.*;

public class BitbucketRepositoryClientImplTest {

    private static final String DEFAULT_BRANCH_URL = "%s/rest/api/1.0/projects/%s/repos/%s/default-branch";
    private static final String WEBHOOK_URL = "%s/rest/api/1.0/projects/%s/repos/%s/pull-requests?withAttributes=false&withProperties=false&state=OPEN";
    private static final String projectKey = "PROJECT_1";
    private static final String repoSlug = "rep_1";

    private final FakeRemoteHttpServer fakeRemoteHttpServer = new FakeRemoteHttpServer();
    private final HttpRequestExecutor requestExecutor = new HttpRequestExecutorImpl(fakeRemoteHttpServer);
    private final BitbucketRequestExecutor bitbucketRequestExecutor = new BitbucketRequestExecutor(BITBUCKET_BASE_URL,
            requestExecutor, OBJECT_MAPPER, ANONYMOUS_CREDENTIALS);
    private BitbucketRepositoryClientImpl client =
            new BitbucketRepositoryClientImpl(bitbucketRequestExecutor, projectKey, repoSlug);

    @Test
    public void testFetchingOfExistingOpenPullRequests() {
        String response = readFileToString("/open-pull-requests.json");
        String url = format(WEBHOOK_URL, BITBUCKET_BASE_URL, projectKey, repoSlug);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        List<BitbucketPullRequest> pullRequests = client.getPullRequests(BitbucketPullRequestState.OPEN).collect(toList());

        assertThat(pullRequests.size(), is(equalTo(2)));
        assertThat(pullRequests.stream().map(BitbucketPullRequest::getId).collect(toSet()), hasItems(new Long(96), new Long(97)));
        assertThat(pullRequests.stream().map(BitbucketPullRequest::getState).collect(toSet()), hasItems(BitbucketPullRequestState.OPEN));
    }

    @Test
    public void testFetchingOfExistingPullRequests() {
        String response = readFileToString("/open-pull-requests.json");
        String webhookUrl = "%s/rest/api/1.0/projects/%s/repos/%s/pull-requests?withAttributes=false&withProperties=false&state=ALL";
        String url = format(webhookUrl, BITBUCKET_BASE_URL, projectKey, repoSlug);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        List<BitbucketPullRequest> pullRequests = client.getPullRequests().collect(toList());

        assertThat(pullRequests.size(), is(equalTo(2)));
        assertThat(pullRequests.stream().map(BitbucketPullRequest::getId).collect(toSet()), hasItems(new Long(96), new Long(97)));
    }

    @Test
    public void testNextPageFetching() {
        BitbucketRepositoryClientImpl.NextPageFetcherImpl fetcher = new BitbucketRepositoryClientImpl.NextPageFetcherImpl(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor);
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

        assertThat(values.stream().map(BitbucketPullRequest::getId).collect(toSet()), hasItems(new Long(96), new Long(97)));
        assertThat(next.isLastPage(), is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLastPageDoesNotHaveNext() {
        BitbucketRepositoryClientImpl.NextPageFetcherImpl fetcher = new BitbucketRepositoryClientImpl.NextPageFetcherImpl(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor);
        BitbucketPage<BitbucketPullRequest> page = new BitbucketPage<>();
        page.setLastPage(true);

        fetcher.next(page);
    }
    
    @Test
    public void testFetchDefaultBranch() {
        String response = readFileToString("/default-branch.json");
        String url = format(DEFAULT_BRANCH_URL, BITBUCKET_BASE_URL, projectKey, repoSlug);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        BitbucketDefaultBranch defaultBranch = client.getDefaultBranch();

        assertNotNull(defaultBranch);
    }
}
