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

import java.util.HashSet;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.BIBUCKET_WEBHOOK_URL;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.MIRROR_SYNCHRONIZED_EVENT;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.REPO_REF_CHANGE;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.PROJECT;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.REPO;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketWebhookHandlerTest {

    private static final String JENKINS_URL = "www.jenkins.mycompany.com";
    private static final String EXPECTED_URL = JENKINS_URL + "/" + BIBUCKET_WEBHOOK_URL + "/trigger";
    private static final String WEBHOOK_NAME = "webhook";

    @Mock
    private BitbucketCapabilitiesClient capabilitiesClient;
    private final WebhookRegisterRequest.Builder defaultBuilder = getRequestBuilder();
    private BitbucketWebhookHandler handler;
    @Mock
    private BitbucketWebhookSupportedEventsClient webhookSupportedEventsClient;
    @Mock
    private BitbucketWebhookClient webhookClient;

    @Before
    public void setup() {
        when(capabilitiesClient.getWebhookSupportedClient()).thenReturn(webhookSupportedEventsClient);
        when(webhookSupportedEventsClient.get()).thenReturn(new BitbucketWebhookSupportedEvents(new HashSet<>(asList(MIRROR_SYNCHRONIZED_EVENT.getEventId(), REPO_REF_CHANGE.getEventId()))));
        doAnswer(answer -> create((BitbucketWebhookRequest) answer.getArguments()[0])).when(webhookClient).registerWebhook(any(BitbucketWebhookRequest.class));
        doAnswer(answer -> create((BitbucketWebhookRequest) answer.getArguments()[1])).when(webhookClient).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        handler = new BitbucketWebhookHandler(capabilitiesClient, webhookClient);
    }

    @Test
    public void testConstructCorrectCallbackUrl() {
        WebhookRegisterResult result = handler.register(defaultBuilder.build());

        assertThat(result.isNewlyAdded(), is(true));
        assertThat(result.isAlreadyRegistered(), is(false));
        assertThat(result.getWebhook().getUrl(), equalTo(EXPECTED_URL));
    }

    @Test
    public void testCorrectEventSubscription() {
        WebhookRegisterResult result = handler.register(defaultBuilder.build());

        assertThat(result.isNewlyAdded(), is(true));
        assertThat(result.isAlreadyRegistered(), is(false));
        assertThat(result.getWebhook().getEvents(), hasItems(REPO_REF_CHANGE.getEventId()));
    }

    @Test
    public void testCorrectEventSubscriptionForMirrors() {
        WebhookRegisterRequest request = defaultBuilder.isMirror(true).build();

        WebhookRegisterResult result = handler.register(request);

        assertThat(result.isNewlyAdded(), is(true));
        assertThat(result.isAlreadyRegistered(), is(false));
        assertThat(result.getWebhook().getEvents(), hasItems(MIRROR_SYNCHRONIZED_EVENT.getEventId()));
    }

    @Test
    public void testCorrectDeletionBasedOnEvents() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventId()), JENKINS_URL, false);
        BitbucketWebhook event2 =
                new BitbucketWebhook(3, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), EXPECTED_URL, true);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())).thenReturn(asList(event1, event2).stream());

        WebhookRegisterResult result = handler.register(defaultBuilder.isMirror(false).build());

        assertThat(result.isNewlyAdded(), is(false));
        assertThat(result.isAlreadyRegistered(), is(true));
        assertThat(result.getWebhook().getEvents(), hasItems(MIRROR_SYNCHRONIZED_EVENT.getEventId()));
        assertThat(result.getWebhook().isActive(), is(equalTo(true)));
        verify(webhookClient).deleteWebhook(anyInt());
    }

    @Test
    public void testDeleteObsoleteWebhook() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), EXPECTED_URL, false);
        BitbucketWebhook event2 =
                new BitbucketWebhook(2, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), EXPECTED_URL, true);
        BitbucketWebhook event3 =
                new BitbucketWebhook(3, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventId()), EXPECTED_URL, false);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())).thenReturn(asList(event1, event2, event3).stream());

        WebhookRegisterResult result = handler.register(defaultBuilder.isMirror(false).build());

        assertThat(result.isNewlyAdded(), is(false));
        assertThat(result.isAlreadyRegistered(), is(true));
        assertThat(result.getWebhook().getEvents(), hasItems(MIRROR_SYNCHRONIZED_EVENT.getEventId()));
        assertThat(result.getWebhook().isActive(), is(equalTo(true)));
        verify(webhookClient, times(2)).deleteWebhook(anyInt());
    }

    @Test
    public void testSkipRegistrationIfPresent() {
        BitbucketWebhook hook =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), EXPECTED_URL, true);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())).thenReturn(asList(hook).stream());

        WebhookRegisterResult result = handler.register(defaultBuilder.build());

        assertThat(result.isNewlyAdded(), is(false));
        assertThat(result.isAlreadyRegistered(), is(true));
        assertThat(result.getWebhook(), is(hook));
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
    }

    @Test
    public void testUpdateExistingWebhookWithCorrectEvent() {
        BitbucketWebhook event =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), EXPECTED_URL, true);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())).thenReturn(asList(event).stream());
        WebhookRegisterRequest request = getRequestBuilder().isMirror(true).build();

        WebhookRegisterResult result = handler.register(request);

        assertThat(result.isNewlyAdded(), is(false));
        assertThat(result.isAlreadyRegistered(), is(true));
        assertThat(result.getWebhook().getEvents(), hasItems(MIRROR_SYNCHRONIZED_EVENT.getEventId()));
    }

    @Test
    public void testUpdateExistingWebhookWithCorrectCallback() {
        String wrongCallback = JENKINS_URL;
        BitbucketWebhook event =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), wrongCallback, true);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())).thenReturn(asList(event).stream());

        WebhookRegisterResult result = handler.register(defaultBuilder.build());

        assertThat(result.isNewlyAdded(), is(false));
        assertThat(result.isAlreadyRegistered(), is(true));
        assertThat(result.getWebhook().getUrl(), is(equalTo(EXPECTED_URL)));
    }

    @Test
    public void testUpdateNonActiveExistingWebhook() {
        BitbucketWebhook event =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), EXPECTED_URL, false);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())).thenReturn(asList(event).stream());

        WebhookRegisterResult result = handler.register(defaultBuilder.build());

        assertThat(result.isNewlyAdded(), is(false));
        assertThat(result.isAlreadyRegistered(), is(true));
        assertThat(result.getWebhook().isActive(), is(equalTo(true)));
    }

    @Test
    public void testUpdateDoesNotHappenWithExistingMirrorSync() {
        BitbucketWebhook event =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventId()), EXPECTED_URL, false);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())).thenReturn(asList(event).stream());

        WebhookRegisterResult result = handler.register(defaultBuilder.isMirror(false).build());

        assertThat(result.isNewlyAdded(), is(false));
        assertThat(result.isAlreadyRegistered(), is(true));
        assertThat(result.getWebhook().getEvents(), hasItems(MIRROR_SYNCHRONIZED_EVENT.getEventId()));
        assertThat(result.getWebhook().isActive(), is(equalTo(true)));
    }

    private BitbucketWebhook create(BitbucketWebhookRequest request) {
        return new BitbucketWebhook(1, request.getName(), request.getEvents(), request.getUrl(), request.isActive());
    }

    private WebhookRegisterRequest.Builder getRequestBuilder() {
        return WebhookRegisterRequest.Builder
                .aRequest(PROJECT, REPO)
                .withJenkinsBaseUrl(JENKINS_URL)
                .withName(WEBHOOK_NAME);
    }
}