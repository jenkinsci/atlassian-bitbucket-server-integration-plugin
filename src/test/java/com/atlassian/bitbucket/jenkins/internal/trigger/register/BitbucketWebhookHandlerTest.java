package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCapabilitiesClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookSupportedEventsClient;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashSet;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.BIBUCKET_WEBHOOK_URL;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.MIRROR_SYNC;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.REPO_REF_CHANGE;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.PROJECT;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.REPO;
import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketWebhookHandlerTest {

    @Mock
    private BitbucketCapabilitiesClient capabilitiesClient;
    @Mock
    private BitbucketWebhookSupportedEventsClient webhookSupportedEventsClient;
    @Mock
    private BitbucketWebhookClient webhookClient;

    @Before
    public void setup() {
        when(capabilitiesClient.getWebhookSupportedClient()).thenReturn(webhookSupportedEventsClient);
        when(webhookSupportedEventsClient.get()).thenReturn(new BitbucketWebhookSupportedEvents(new HashSet<>(asList(MIRROR_SYNC.getEventId(), REPO_REF_CHANGE.getEventId()))));
        doAnswer(answer -> create((BitbucketWebhookRequest) answer.getArguments()[0])).when(webhookClient).registerWebhook(any(BitbucketWebhookRequest.class));
    }

    @Test
    public void testConstructCorrectCallbackUrl() {
        String jenkinUrl = "www.jenkins.mycompany.com";
        String expectedCallbackUrl = jenkinUrl + "/" + BIBUCKET_WEBHOOK_URL + "/trigger";
        BitbucketWebhookHandler handler = new BitbucketWebhookHandler(capabilitiesClient, webhookClient);
        WebhookRegisterRequest request = WebhookRegisterRequest.Builder
                .aRequest(PROJECT, REPO)
                .withJenkinsBaseUrl(jenkinUrl)
                .isMirror(false)
                .withName("id1")
                .build();

        WebhookRegisterResult result = handler.register(request);

        assertThat(result.isSuccess(), is(true));
        assertThat(result.isAlreadyRegistered(), is(false));
        assertThat(result.getWebhook().getUrl(), equalTo(expectedCallbackUrl));
    }

    @Test
    public void testCorrectEventSubscription() {
        String serverId = "id1";
        String jenkinUrl = "www.jenkins.mycompany.com";
        BitbucketWebhookHandler handler = new BitbucketWebhookHandler(capabilitiesClient, webhookClient);
        WebhookRegisterRequest request = WebhookRegisterRequest.Builder
                .aRequest(PROJECT, REPO)
                .withJenkinsBaseUrl(jenkinUrl)
                .withName(serverId)
                .build();

        WebhookRegisterResult result = handler.register(request);

        assertThat(result.isSuccess(), is(true));
        assertThat(result.isAlreadyRegistered(), is(false));
        assertThat(result.getWebhook().getEvents(), hasItems(REPO_REF_CHANGE.getEventId()));
    }

    @Test
    public void testCorrectEventSubscriptionForMirrors() {
        String serverId = "id1";
        String jenkinUrl = "www.jenkins.mycompany.com";

        BitbucketWebhookHandler handler = new BitbucketWebhookHandler(capabilitiesClient, webhookClient);
        WebhookRegisterRequest request = WebhookRegisterRequest.Builder
                .aRequest(PROJECT, REPO)
                .withJenkinsBaseUrl(jenkinUrl)
                .withName(serverId)
                .isMirror(true)
                .build();
        WebhookRegisterResult result = handler.register(request);
        assertThat(result.isSuccess(), is(true));
        assertThat(result.isAlreadyRegistered(), is(false));
        assertThat(result.getWebhook().getEvents(), hasItems(MIRROR_SYNC.getEventId()));
    }

    @Test
    public void testSkipRegistrationIfPresent() {
        String serverId = "id1";
        String jenkinUrl = "www.jenkins.mycompany.com";
        String expectedCallbackUrl = jenkinUrl + "/" + BIBUCKET_WEBHOOK_URL + "/trigger";
        BitbucketWebhook event =
                new BitbucketWebhook(1, serverId, Collections.singleton(REPO_REF_CHANGE.getEventId()), expectedCallbackUrl, true);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNC.getEventId())).thenReturn(asList(event).stream());

        BitbucketWebhookHandler handler = new BitbucketWebhookHandler(capabilitiesClient, webhookClient);
        WebhookRegisterRequest request = WebhookRegisterRequest.Builder
                .aRequest(PROJECT, REPO)
                .withJenkinsBaseUrl(jenkinUrl)
                .withName(serverId)
                .build();
        WebhookRegisterResult result = handler.register(request);

        assertThat(result.isSuccess(), is(false));
        assertThat(result.isAlreadyRegistered(), is(true));
        assertThat(result.getWebhook(), is(event));
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
    }

    @Test
    public void testSuccessfulRegistrationIfExistingHookWithWrongEventSubscription() {
        String serverId = "id1";
        String jenkinUrl = "www.jenkins.mycompany.com";
        String expectedCallbackUrl = jenkinUrl + BIBUCKET_WEBHOOK_URL + "/trigger";
        BitbucketWebhook event =
                new BitbucketWebhook(1, serverId, Collections.singleton(REPO_REF_CHANGE.getEventId()), expectedCallbackUrl, true);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNC.getEventId())).thenReturn(asList(event).stream());

        BitbucketWebhookHandler handler = new BitbucketWebhookHandler(capabilitiesClient, webhookClient);
        WebhookRegisterRequest request = WebhookRegisterRequest.Builder
                .aRequest(PROJECT, REPO)
                .withJenkinsBaseUrl(jenkinUrl)
                .withName(serverId)
                .isMirror(true)
                .build();
        WebhookRegisterResult result = handler.register(request);

        assertThat(result.isSuccess(), is(true));
        assertThat(result.isAlreadyRegistered(), is(false));
        assertThat(result.getWebhook().getEvents(), hasItems(MIRROR_SYNC.getEventId()));
    }

    private BitbucketWebhook create(BitbucketWebhookRequest request) {
        return new BitbucketWebhook(1, request.getName(), request.getEvents(), request.getUrl(), request.isActive());
    }
}