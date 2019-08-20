package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest.BitbucketWebhookRequestBuilder;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.assertThat;

public class BitbucketWebhookClientImplTest {

    private static final String projectKey = "proj";
    private static final String repoSlug = "repo";

    private final FakeRemoteHttpServer fakeRemoteHttpServer = new FakeRemoteHttpServer();
    private final HttpRequestExecutor requestExecutor = new HttpRequestExecutorImpl(fakeRemoteHttpServer);
    private BitbucketWebhookClientImpl client =
            new BitbucketWebhookClientImpl(projectKey, repoSlug, new BitbucketRequestExecutor(BITBUCKET_BASE_URL,
                    requestExecutor, OBJECT_MAPPER, ANONYMOUS_CREDENTIALS));

    @Test
    public void testFetchingOfExistingWebhooks() {
        String response = readFileToString("/webhook/configured_web_hooks_response.json");
        String url = format("%s/rest/api/1.0/projects/%s/repos/%s/webhooks", BITBUCKET_BASE_URL, projectKey, repoSlug);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        List<BitbucketWebhook> webhooks = client.getWebhooks().getValues();

        assertThat(webhooks.size(), is(equalTo(2)));
        assertThat(webhooks.stream().map(BitbucketWebhookRequest::getName).collect(toSet()), hasItems("w1", "w2"));
        assertThat(webhooks.stream().map(BitbucketWebhookRequest::getUrl).collect(toSet()), hasItems("http://localhost:8090"));
        assertThat(webhooks.stream().map(BitbucketWebhookRequest::isActive).collect(toSet()), hasItems(true));
    }

    @Test
    public void testFetchingOfExistingWebhooksWithFilter() {
        String repoRefEvent = "repo:refs_changed";
        String mirrorSyncEvent = "mirror:repo_synchronized";
        String response = readFileToString("/webhook/configured_web_hooks_response.json");
        String url =
                format("%s/rest/api/1.0/projects/%s/repos/%s/webhooks?event=%s&event=%s",
                        BITBUCKET_BASE_URL,
                        projectKey,
                        repoSlug,
                        encode(repoRefEvent),
                        encode(mirrorSyncEvent));
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        List<BitbucketWebhook> webhooks =
                client.getWebhooks(repoRefEvent, mirrorSyncEvent).getValues();

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

        BitbucketWebhookRequest request = BitbucketWebhookRequestBuilder
                .aRequestFor(repoRefEvent, mirrorSyncEvent)
                .withCallbackTo(url)
                .name("WebhookName")
                .withIsActive(true)
                .build();
        BitbucketWebhook result = client.registerWebhook(request);

        assertThat(result.getEvents(), hasItems(repoRefEvent, mirrorSyncEvent));
    }
}
