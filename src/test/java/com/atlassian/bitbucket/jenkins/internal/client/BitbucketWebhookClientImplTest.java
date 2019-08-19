package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookClientImpl.NextPageFetcherImpl;
import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static junit.framework.TestCase.assertNotNull;
import static okhttp3.HttpUrl.parse;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.*;

public class BitbucketWebhookClientImplTest {

    private static final String projectKey = "proj";
    private static final String repoSlug = "repo";

    private final FakeRemoteHttpServer fakeRemoteHttpServer = new FakeRemoteHttpServer();
    private final HttpRequestExecutor requestExecutor = new HttpRequestExecutorImpl(fakeRemoteHttpServer);
    private final BitbucketRequestExecutor bitbucketRequestExecutor = new BitbucketRequestExecutor(BITBUCKET_BASE_URL,
            requestExecutor, OBJECT_MAPPER, ANONYMOUS_CREDENTIALS);
    private BitbucketWebhookClientImpl client =
            new BitbucketWebhookClientImpl(projectKey, repoSlug, bitbucketRequestExecutor);

    @Test
    public void testFetchingOfExistingWebhooks() {
        String response = readFileToString("/webhook/web_hooks_in_system.json");
        String url = format("%s/rest/api/1.0/projects/%s/repos/%s/webhooks", BITBUCKET_BASE_URL, projectKey, repoSlug);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        List<BitbucketWebhook> webhooks = client.getWebhooks().collect(toList());

        assertThat(webhooks.size(), is(equalTo(2)));
    }

    @Test
    public void testFetchingOfExistingWebhooksWithFilter() {
        String repoRefEvent = "repo:refs_changed";
        String mirrorSyncEvent = "mirror:repo_synchronized";
        String response = readFileToString("/webhook/web_hooks_in_system.json");
        String url =
                format("%s/rest/api/1.0/projects/%s/repos/%s/webhooks?event=%s&event=%s",
                        BITBUCKET_BASE_URL,
                        projectKey,
                        repoSlug,
                        encode(repoRefEvent),
                        encode(mirrorSyncEvent));
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        List<BitbucketWebhook> webhooks =
                client.getWebhooks(repoRefEvent, mirrorSyncEvent).collect(toList());

        assertThat(webhooks.size(), is(equalTo(2)));
        Set<String> events =
                webhooks.stream().map(BitbucketWebhook::getEvents).flatMap(Set::stream).collect(toSet());
        assertThat(events, hasItems(repoRefEvent, mirrorSyncEvent));
    }

    @Test
    public void testRegisterWebhook() {
        String repoRefEvent = "repo:refs_changed";
        String mirrorSyncEvent = "mirror:repo_synchronized";
        String response = readFileToString("/webhook/webhook_created_response.json");
        String url = "www.example.com";
        String registerUrl =
                format("%s/rest/api/1.0/projects/%s/repos/%s/webhooks",
                        BITBUCKET_BASE_URL,
                        projectKey,
                        repoSlug);
        fakeRemoteHttpServer.mapPostRequestToResult(registerUrl, readFileToString("/webhook/webhook_creation_request.json"), response);

        WebhookRegisterRequest request = WebhookRegisterRequest.WebhookRegisterRequestBuilder
                .aRequestFor(repoRefEvent, mirrorSyncEvent)
                .withCallbackTo(url)
                .name("WebhookName")
                .build();
        BitbucketWebhook result = client.registerWebhook(request);

        assertThat(result.getEvents(), hasItems(repoRefEvent, mirrorSyncEvent));
    }

    @Test
    public void testNextPageFetching() {
        NextPageFetcherImpl fetcher = new NextPageFetcherImpl(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor);
        int nextPageStart = 2;
        fakeRemoteHttpServer.mapUrlToResult(
                BITBUCKET_BASE_URL + "?start=" + nextPageStart,
                readFileToString("/webhook/web_hooks_in_system_last_page.json"));
        BitbucketPage<BitbucketWebhook> firstPage = new BitbucketPage<>();
        firstPage.setNextPageStart(nextPageStart);

        BitbucketPage<BitbucketWebhook> next = fetcher.next(firstPage);
        assertEquals(next.getSize(), next.getValues().size());
        assertTrue(next.getSize() > 0);

        BitbucketWebhook webhook = next.getValues().get(0);
        assertNotNull(webhook.getName());
        assertTrue(webhook.getId() > 0);
        assertThat(next.isLastPage(), is(true));

        assertThat(next.isLastPage(), is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLastPageDoesNotHaveNext() {
        NextPageFetcherImpl fetcher = new NextPageFetcherImpl(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor);
        BitbucketPage<BitbucketWebhook> page = new BitbucketPage<>();
        page.setLastPage(true);

        fetcher.next(page);
    }
}
