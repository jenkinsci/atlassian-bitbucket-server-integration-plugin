package com.atlassian.bitbucket.jenkins.internal.client.paging;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import org.junit.Test;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static okhttp3.HttpUrl.parse;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class GetBasedNextPageFetcherTest {

    private final FakeRemoteHttpServer fakeRemoteHttpServer = new FakeRemoteHttpServer();
    private final HttpRequestExecutor requestExecutor = new HttpRequestExecutorImpl(fakeRemoteHttpServer);
    private final BitbucketRequestExecutor bitbucketRequestExecutor =
            new BitbucketRequestExecutor(BITBUCKET_BASE_URL, requestExecutor, OBJECT_MAPPER, ANONYMOUS_CREDENTIALS);
    private GetBasedNextPageFetcher fetcher =
            new GetBasedNextPageFetcher(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor);

    @Test
    public void testNextPageFetching() {
        int nextPageStart = 2;
        fakeRemoteHttpServer.mapUrlToResult(
                BITBUCKET_BASE_URL + "?start=" + nextPageStart,
                readFileToString("/webhook/web_hooks_in_system_last_page.json"));
        BitbucketPage<BitbucketWebhook> firstPage = new BitbucketPage<>();
        firstPage.setNextPageStart(nextPageStart);

        BitbucketPage next = fetcher.next(firstPage);

        assertThat(next.isLastPage(), is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLastPageDoesNotHaveNext() {
        BitbucketPage<BitbucketWebhook> page = new BitbucketPage<>();
        page.setLastPage(true);

        fetcher.next(page);
    }
}