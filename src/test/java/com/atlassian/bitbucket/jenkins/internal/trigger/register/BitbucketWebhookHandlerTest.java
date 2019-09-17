package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCapabilitiesClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketMissingCapabilityException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.WebhookNotSupportedException;
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
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketWebhookHandlerTest {

    private static final String JENKINS_URL = "www.example.com";
    private static final String EXPECTED_URL = JENKINS_URL + "/" + BIBUCKET_WEBHOOK_URL + "/trigger";
    private static final String WEBHOOK_NAME = "webhook";

    @Mock
    private BitbucketCapabilitiesClient capabilitiesClient;
    private final WebhookRegisterRequest.Builder defaultBuilder = getRequestBuilder();
    private BitbucketWebhookHandler handler;
    @Mock
    private BitbucketWebhookClient webhookClient;

    @Before
    public void setup() {
        when(capabilitiesClient.getWebhookSupportedEvents()).thenReturn(new BitbucketWebhookSupportedEvents(new HashSet<>(asList(MIRROR_SYNCHRONIZED_EVENT.getEventId(), REPO_REF_CHANGE.getEventId()))));
        doAnswer(answer -> create((BitbucketWebhookRequest) answer.getArguments()[0])).when(webhookClient).registerWebhook(any(BitbucketWebhookRequest.class));
        doAnswer(answer -> create((Integer) answer.getArguments()[0], (BitbucketWebhookRequest) answer.getArguments()[1])).when(webhookClient).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        handler = new BitbucketWebhookHandler(capabilitiesClient, webhookClient);
    }

    @Test
    public void testConstructCorrectCallbackUrl() {
        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).build());

        assertThat(result.getUrl(), is(equalTo(EXPECTED_URL)));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testCorrectEventSubscription() {
        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).build());

        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(REPO_REF_CHANGE.getEventId()));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testCorrectEventSubscriptionForMirrors() {
        WebhookRegisterRequest request = defaultBuilder.isMirror(true).build();

        BitbucketWebhook result = handler.register(request);

        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(MIRROR_SYNCHRONIZED_EVENT.getEventId()));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testDeleteObsoleteWebhookWithSameNameDifferentURL() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventId()), EXPECTED_URL, true);
        BitbucketWebhook event2 =
                new BitbucketWebhook(2, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventId()), JENKINS_URL, false);
        BitbucketWebhook event3 =
                new BitbucketWebhook(3, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), EXPECTED_URL, true);
        BitbucketWebhook event4 =
                new BitbucketWebhook(4, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), JENKINS_URL, true);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())).thenReturn(asList(event1, event2, event3, event4).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).build());

        assertThat(result.getId(), is(equalTo(3)));
        verify(webhookClient).deleteWebhook(2);
        verify(webhookClient).deleteWebhook(4);
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
    }

    @Test
    public void testDeleteObsoleteWebhookWithSameCallbackDifferentName() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), EXPECTED_URL, true);
        BitbucketWebhook event2 =
                new BitbucketWebhook(2,
                        WEBHOOK_NAME + "123", singleton(REPO_REF_CHANGE.getEventId()), EXPECTED_URL, true);
        BitbucketWebhook event3 =
                new BitbucketWebhook(3, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventId()), EXPECTED_URL, true);
        BitbucketWebhook event4 =
                new BitbucketWebhook(4,
                        WEBHOOK_NAME + "123", singleton(MIRROR_SYNCHRONIZED_EVENT.getEventId()), EXPECTED_URL, true);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())).thenReturn(asList(event1, event2, event3, event4).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(true).build());

        assertThat(result.getId(), is(equalTo(3)));
        verify(webhookClient).deleteWebhook(2);
        verify(webhookClient).deleteWebhook(4);
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
    }

    @Test
    public void testSkipRegistrationIfPresentForRepoRef() {
        BitbucketWebhook event =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), EXPECTED_URL, true);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId()))
                .thenReturn(asList(event).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).build());

        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(REPO_REF_CHANGE.getEventId()));
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testSkipRegistrationIfPresentForMirrors() {
        BitbucketWebhook event =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventId()), EXPECTED_URL, true);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId()))
                .thenReturn(asList(event).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(true).build());

        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(MIRROR_SYNCHRONIZED_EVENT.getEventId()));
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testSeperateWebhooksEvents() {
        BitbucketWebhook event =
                new BitbucketWebhook(-1234, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), EXPECTED_URL, true);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())).thenReturn(asList(event).stream());

        BitbucketWebhook result = handler.register(getRequestBuilder().isMirror(true).build());

        assertThat(result.getEvents(), hasItem(MIRROR_SYNCHRONIZED_EVENT.getEventId()));
        assertThat(result.getId(), is(not(equalTo(event.getId()))));
        verify(webhookClient).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testUpdateExistingWebhookWithCorrectCallback() {
        String wrongCallback = JENKINS_URL;
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), wrongCallback, true);
        BitbucketWebhook event2 =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventId()), wrongCallback, true);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())).thenReturn(asList(event1, event2).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).build());

        assertThat(result.getUrl(), is(equalTo(EXPECTED_URL)));
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, times(2)).updateWebhook(anyInt(), argThat((BitbucketWebhookRequest request) -> request.getUrl().equals(EXPECTED_URL)));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testUpdateNonActiveExistingWebhook() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventId()), EXPECTED_URL, false);
        BitbucketWebhook event2 =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventId()), EXPECTED_URL, false);
        when(webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())).thenReturn(asList(event1, event2).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(true).build());

        assertThat(result.isActive(), is(equalTo(true)));
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, times(2)).updateWebhook(anyInt(), argThat((BitbucketWebhookRequest request) -> request.isActive()));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testUnsupportedMirrorEvent() {
        when(capabilitiesClient.getWebhookSupportedEvents()).thenThrow(BitbucketMissingCapabilityException.class);

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(true).build());

        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(REPO_REF_CHANGE.getEventId()));
        verify(webhookClient).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testJenkinsUrlContainsTrailingSlash() {
        BitbucketWebhook result =
                handler.register(defaultBuilder.withJenkinsBaseUrl(JENKINS_URL + "/").isMirror(false).build());

        assertThat(result.getUrl(), is(equalTo(EXPECTED_URL)));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test(expected = WebhookNotSupportedException.class)
    public void testCapabilitiesNeedToAtleastSupportRepoRef() {
        when(capabilitiesClient.getWebhookSupportedEvents()).thenReturn(new BitbucketWebhookSupportedEvents(new HashSet<>()));

        handler.register(defaultBuilder.isMirror(true).build());
    }

    @Test
    public void testMirrorRequestWillBeRepoRefSubscribedIfUnsupported() {
        when(capabilitiesClient.getWebhookSupportedEvents()).thenReturn(new BitbucketWebhookSupportedEvents(new HashSet<>(asList(REPO_REF_CHANGE.getEventId()))));
        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(true).build());

        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(REPO_REF_CHANGE.getEventId()));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    private BitbucketWebhook create(BitbucketWebhookRequest request) {
        return create(1, request);
    }

    private BitbucketWebhook create(int id, BitbucketWebhookRequest request) {
        return new BitbucketWebhook(id, request.getName(), request.getEvents(), request.getUrl(), request.isActive());
    }

    private WebhookRegisterRequest.Builder getRequestBuilder() {
        return WebhookRegisterRequest.Builder
                .aRequest(PROJECT, REPO)
                .withJenkinsBaseUrl(JENKINS_URL)
                .withName(WEBHOOK_NAME);
    }
}