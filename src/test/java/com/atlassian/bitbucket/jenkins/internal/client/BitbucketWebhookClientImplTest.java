package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookClientImpl.NextPageFetcherImpl;
import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest.Builder;
import okhttp3.Request;
import okio.Buffer;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static okhttp3.HttpUrl.parse;
import static org.apache.commons.lang3.StringUtils.deleteWhitespace;
import static org.apache.commons.lang3.StringUtils.normalizeSpace;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.*;

public class BitbucketWebhookClientImplTest {

    private static final String WEBHOOK_URL = "%s/rest/api/1.0/projects/%s/repos/%s/webhooks";
    private static final String projectKey = "proj";
    private static final String repoSlug = "repo";

    private final FakeRemoteHttpServer fakeRemoteHttpServer = new FakeRemoteHttpServer();
    private final HttpRequestExecutor requestExecutor = new HttpRequestExecutorImpl(fakeRemoteHttpServer);
    private final BitbucketRequestExecutor bitbucketRequestExecutor = new BitbucketRequestExecutor(BITBUCKET_BASE_URL,
            requestExecutor, OBJECT_MAPPER, ANONYMOUS_CREDENTIALS);
    private BitbucketWebhookClientImpl client =
            new BitbucketWebhookClientImpl(bitbucketRequestExecutor, projectKey, repoSlug);

    @Test
    public void testFetchingOfExistingWebhooks() {
        String response = readFileToString("/webhook/web_hooks_in_system.json");
        String url = format(WEBHOOK_URL, BITBUCKET_BASE_URL, projectKey, repoSlug);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        List<BitbucketWebhook> webhooks = client.getWebhooks().collect(toList());

        assertThat(webhooks.size(), is(equalTo(2)));
        assertThat(webhooks.stream().map(BitbucketWebhookRequest::getName).collect(toSet()), hasItems("w1", "w2"));
        assertThat(webhooks.stream().map(BitbucketWebhookRequest::getUrl).collect(toSet()), hasItems("http://localhost:8090"));
        assertThat(webhooks.stream().map(BitbucketWebhookRequest::isActive).collect(toSet()), hasItems(true));
    }

    @Test
    public void testFetchingOfExistingWebhooksWithFilter() {
        String repoRefEvent = "repo:refs_changed";
        String mirrorSyncEvent = "mirror:repo_synchronized";
        String response = readFileToString("/webhook/web_hooks_in_system.json");
        String url =
                format(WEBHOOK_URL + "?event=%s&event=%s",
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
    public void testRegisterWebhook() throws IOException {
        String repoRefEvent = "repo:refs_changed";
        String mirrorSyncEvent = "mirror:repo_synchronized";
        String response = readFileToString("/webhook/webhook_created_response.json");
        String url = "www.example.com";
        String requestBody = readFileToString("/webhook/webhook_creation_request.json");
        String registerUrl =
                format(WEBHOOK_URL,
                        BITBUCKET_BASE_URL,
                        projectKey,
                        repoSlug);
        fakeRemoteHttpServer.mapPostRequestToResult(registerUrl, requestBody, response);

        BitbucketWebhookRequest request = Builder
                .aRequestFor(repoRefEvent, mirrorSyncEvent)
                .withCallbackTo(url)
                .name("WebhookName")
                .withIsActive(true)
                .build();
        BitbucketWebhook result = client.registerWebhook(request);

        assertThat(result.getEvents(), hasItems(repoRefEvent, mirrorSyncEvent));

        Request recordedRequest = fakeRemoteHttpServer.getRequest(registerUrl);
        Buffer b = new Buffer();
        recordedRequest.body().writeTo(b);
        assertEquals("Request body not same as expected.", deleteWhitespace(normalizeSpace(requestBody)), new String(b.readByteArray()));
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
        List<BitbucketWebhook> values = next.getValues();
        assertEquals(next.getSize(), values.size());
        assertTrue(next.getSize() > 0);

        assertThat(values.stream().map(BitbucketWebhook::getId).collect(toSet()), hasItems(3, 4));
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
