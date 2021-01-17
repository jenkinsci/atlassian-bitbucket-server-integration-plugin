package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCapabilitiesClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketMissingCapabilityException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.WebhookNotSupportedException;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import java.util.*;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.BIBUCKET_WEBHOOK_URL;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.*;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.PROJECT;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.REPO;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketWebhookHandlerTest {

    private static final String JENKINS_URL = "www.example.com";
    private static final String EXPECTED_URL = JENKINS_URL + "/" + BIBUCKET_WEBHOOK_URL + "/trigger";
    private static final Set<String> REF_AND_PR_EVENTS = new HashSet<>();
    private static final Set<String> PR_EVENTS = new HashSet<>();
    private static final Set<String> ALL_EVENTS = new HashSet<>();
    private static final String WEBHOOK_NAME = "webhook";

    @Mock
    private BitbucketCapabilitiesClient capabilitiesClient;
    private final WebhookRegisterRequest.Builder defaultBuilder = getRequestBuilder();
    private BitbucketWebhookHandler handler;
    @Mock
    private BitbucketWebhookClient webhookClient;

    @Before
    public void setup() {
        REF_AND_PR_EVENTS.addAll(REPO_REF_CHANGE.getEventIds());
        REF_AND_PR_EVENTS.addAll(PULL_REQUEST_CLOSED_EVENT.getEventIds());
        REF_AND_PR_EVENTS.addAll(PULL_REQUEST_OPENED_EVENT.getEventIds());
        PR_EVENTS.addAll(PULL_REQUEST_OPENED_EVENT.getEventIds());
        PR_EVENTS.addAll(PULL_REQUEST_CLOSED_EVENT.getEventIds());
        ALL_EVENTS.addAll(MIRROR_SYNCHRONIZED_EVENT.getEventIds());
        ALL_EVENTS.addAll(REPO_REF_CHANGE.getEventIds());
        ALL_EVENTS.addAll(PULL_REQUEST_OPENED_EVENT.getEventIds());
        ALL_EVENTS.addAll(PULL_REQUEST_CLOSED_EVENT.getEventIds());

        when(capabilitiesClient.getWebhookSupportedEvents()).thenReturn(new BitbucketWebhookSupportedEvents(ALL_EVENTS));
        doAnswer(answer -> create((BitbucketWebhookRequest) answer.getArguments()[0])).when(webhookClient).registerWebhook(any(BitbucketWebhookRequest.class));
        doAnswer(answer -> create((Integer) answer.getArguments()[0], (BitbucketWebhookRequest) answer.getArguments()[1])).when(webhookClient).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        handler = new BitbucketWebhookHandler(capabilitiesClient, webhookClient);
    }

    @Test
    public void testConstructCorrectCallbackUrl() {
        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPush(true).build());

        assertThat(result.getUrl(), is(equalTo(EXPECTED_URL)));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testCorrectPushEventSubscription() {
        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPush(true).build());

        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(REPO_REF_CHANGE.getEventIds().get(0)));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testCorrectPREventSubscription() {
        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPR(true).build());

        assertThat(result.getEvents(), iterableWithSize(4));
        assertEquals(result.getEvents(), PR_EVENTS);

        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testCorrectPushAndPREventSubscription() {
        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPush(true).shouldTriggerOnPR(true).build());

        assertThat(result.getEvents(), iterableWithSize(5));
        assertEquals(result.getEvents(), REF_AND_PR_EVENTS);
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testCorrectEventSubscriptionForMirrors() {
        WebhookRegisterRequest request = defaultBuilder.isMirror(true).shouldTriggerOnPush(true).build();

        BitbucketWebhook result = handler.register(request);

        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(MIRROR_SYNCHRONIZED_EVENT.getEventIds().get(0)));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testDeleteObsoleteWebhookWithSameNameDifferentURLMirror() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventIds().get(0)), EXPECTED_URL, true);
        BitbucketWebhook event2 =
                new BitbucketWebhook(2, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventIds().get(0)), JENKINS_URL, false);

        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event1, event2).stream());

        BitbucketWebhook result =
                handler.register(defaultBuilder.isMirror(true).shouldTriggerOnPush(false).shouldTriggerOnPR(false).build());

        assertThat(result.getId(), is(equalTo(1)));
        verify(webhookClient).deleteWebhook(2);
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
    }

    @Test
    public void testDeleteObsoleteWebhookWithSameNameDifferentURLRefChange() {
        BitbucketWebhook event3 =
                new BitbucketWebhook(3, WEBHOOK_NAME, REF_AND_PR_EVENTS, EXPECTED_URL, true);
        BitbucketWebhook event4 =
                new BitbucketWebhook(4, WEBHOOK_NAME, REF_AND_PR_EVENTS, JENKINS_URL, true);

        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event3, event4).stream());

        BitbucketWebhook result1 =
                handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPush(true).shouldTriggerOnPR(false).build());

        assertThat(result1.getId(), is(equalTo(3)));
        verify(webhookClient).deleteWebhook(4);
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
    }

    @Test
    public void testDeleteObsoleteWebhookWithSameCallbackDifferentName() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, REF_AND_PR_EVENTS, EXPECTED_URL, true);
        BitbucketWebhook event2 =
                new BitbucketWebhook(2, WEBHOOK_NAME + "123", REF_AND_PR_EVENTS, EXPECTED_URL, true);
        BitbucketWebhook event3 =
                new BitbucketWebhook(3, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventIds().get(0)), EXPECTED_URL, true);
        BitbucketWebhook event4 =
                new BitbucketWebhook(4,
                        WEBHOOK_NAME + "123", singleton(MIRROR_SYNCHRONIZED_EVENT.getEventIds().get(0)), EXPECTED_URL, true);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event1, event2, event3, event4).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(true).shouldTriggerOnPR(true).shouldTriggerOnPush(true).build());

        assertThat(result.getId(), is(equalTo(3)));
        verify(webhookClient).deleteWebhook(2);
        verify(webhookClient).deleteWebhook(4);
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
    }

    @Test
    public void testSkipRegistrationIfPresentForRefChange() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(2, WEBHOOK_NAME, REF_AND_PR_EVENTS, EXPECTED_URL, true);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT))).thenReturn(asList(event1).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPush(true).shouldTriggerOnPR(false).build());
        assertThat(result.getEvents(), iterableWithSize(5));
        assertEquals(result.getEvents(), REF_AND_PR_EVENTS);
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testSkipRegistrationIfPresentForPR() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(2, WEBHOOK_NAME, REF_AND_PR_EVENTS, EXPECTED_URL, true);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event1).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPush(false).shouldTriggerOnPR(true).build());
        assertThat(result.getEvents(), iterableWithSize(5));
        assertEquals(result.getEvents(), REF_AND_PR_EVENTS);
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testSkipRegistrationIfPresentForMirrors() {
        BitbucketWebhook event =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventIds().get(0)), EXPECTED_URL, true);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(true).shouldTriggerOnPush(true).build());

        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(MIRROR_SYNCHRONIZED_EVENT.getEventIds().get(0)));
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testSeparateWebhooksEvents() {
        BitbucketWebhook event =
                new BitbucketWebhook(-1234, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventIds().get(0)), EXPECTED_URL, true);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event).stream());

        BitbucketWebhook result = handler.register(getRequestBuilder().isMirror(true).shouldTriggerOnPush(true).build());

        assertThat(result.getEvents(), hasItem(MIRROR_SYNCHRONIZED_EVENT.getEventIds().get(0)));
        assertThat(result.getId(), is(not(equalTo(event.getId()))));
        verify(webhookClient).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testUpdateExistingWebhookWithCorrectCallback() {
        String wrongCallback = JENKINS_URL;
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, REF_AND_PR_EVENTS, wrongCallback, true);
        BitbucketWebhook event2 =
                new BitbucketWebhook(2, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventIds().get(0)), wrongCallback, true);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event2, event1).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(true).shouldTriggerOnPush(true).shouldTriggerOnPR(true).build());

        assertThat(result.getUrl(), is(equalTo(EXPECTED_URL)));
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, times(2)).updateWebhook(anyInt(), argThat((BitbucketWebhookRequest request) -> request.getUrl().equals(EXPECTED_URL)));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testUpdateNonActiveExistingWebhook() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventIds().get(0)), EXPECTED_URL, false);
        BitbucketWebhook event2 =
                new BitbucketWebhook(2, WEBHOOK_NAME, singleton(MIRROR_SYNCHRONIZED_EVENT.getEventIds().get(0)), EXPECTED_URL, false);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event1, event2).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(true).shouldTriggerOnPR(true).shouldTriggerOnPush(true).build());

        assertThat(result.isActive(), is(equalTo(true)));
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, times(2)).updateWebhook(anyInt(), argThat((BitbucketWebhookRequest request) -> request.isActive()));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testDuplicatePushWebhooks() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, singleton(REPO_REF_CHANGE.getEventIds().get(0)), EXPECTED_URL, false);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event1).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPR(false).shouldTriggerOnPush(true).build());

        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(REPO_REF_CHANGE.getEventIds().get(0)));
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, times(1)).updateWebhook(anyInt(), argThat((BitbucketWebhookRequest request) -> request.isActive()));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testDuplicatePullWebhooks() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, PR_EVENTS, EXPECTED_URL, false);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event1).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPR(true).shouldTriggerOnPush(false).build());

        assertThat(result.getEvents(), iterableWithSize(4));
        assertEquals(result.getEvents(), PR_EVENTS);
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, times(1)).updateWebhook(anyInt(), argThat((BitbucketWebhookRequest request) -> request.isActive()));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testDuplicatePushAndPullWebhooks() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, REF_AND_PR_EVENTS, EXPECTED_URL, false);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event1).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPR(true).shouldTriggerOnPush(true).build());

        assertThat(result.getEvents(), iterableWithSize(5));
        assertEquals(result.getEvents(), REF_AND_PR_EVENTS);
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, times(1)).updateWebhook(anyInt(), argThat((BitbucketWebhookRequest request) -> request.isActive()));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testAddingPullHandlingToExistingPushWebhook() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, Collections.singleton(REPO_REF_CHANGE.getEventIds().get(0)), EXPECTED_URL, false);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event1).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPR(true).shouldTriggerOnPush(true).build());

        assertThat(result.getEvents(), iterableWithSize(5));
        assertEquals(result.getEvents(), REF_AND_PR_EVENTS);
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, times(1)).updateWebhook(anyInt(), argThat((BitbucketWebhookRequest request) -> request.isActive()));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testAddingPushHandlingToExistingPullWebhook() {
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, Collections.singleton(PULL_REQUEST_OPENED_EVENT.getEventIds().get(0)), EXPECTED_URL, false);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event1).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPR(true).shouldTriggerOnPush(true).build());

        assertThat(result.getEvents(), iterableWithSize(5));
        assertEquals(result.getEvents(), REF_AND_PR_EVENTS);
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, times(1)).updateWebhook(anyInt(), argThat((BitbucketWebhookRequest request) -> request.isActive()));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testUpdatingPushAndPullWebhookWithLessEvents() {
        //webhook should not change
        BitbucketWebhook event1 =
                new BitbucketWebhook(1, WEBHOOK_NAME, REF_AND_PR_EVENTS, EXPECTED_URL, false);
        when(webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT)))
                .thenReturn(asList(event1).stream());

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(false).shouldTriggerOnPR(false).shouldTriggerOnPush(true).build());

        assertThat(result.getEvents(), iterableWithSize(5));
        assertEquals(result.getEvents(), REF_AND_PR_EVENTS);
        verify(webhookClient, never()).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, times(1)).updateWebhook(anyInt(), argThat((BitbucketWebhookRequest request) -> request.isActive()));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testUnsupportedMirrorEvent() {
        when(capabilitiesClient.getWebhookSupportedEvents()).thenThrow(BitbucketMissingCapabilityException.class);

        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(true).shouldTriggerOnPush(true).shouldTriggerOnPR(true).build());

        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(REPO_REF_CHANGE.getEventIds().get(0)));
        verify(webhookClient).registerWebhook(any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test
    public void testJenkinsUrlContainsTrailingSlash() {
        BitbucketWebhook result =
                handler.register(defaultBuilder.withJenkinsBaseUrl(JENKINS_URL + "/").isMirror(false).shouldTriggerOnPush(true).build());

        assertThat(result.getUrl(), is(equalTo(EXPECTED_URL)));
        verify(webhookClient, never()).updateWebhook(anyInt(), any(BitbucketWebhookRequest.class));
        verify(webhookClient, never()).deleteWebhook(anyInt());
    }

    @Test(expected = WebhookNotSupportedException.class)
    public void testCapabilitiesNeedToAtleastSupportRepoRef() {
        when(capabilitiesClient.getWebhookSupportedEvents()).thenReturn(new BitbucketWebhookSupportedEvents(new HashSet<>()));

        handler.register(defaultBuilder.isMirror(true).shouldTriggerOnPush(true).build());
    }

    @Test
    public void testMirrorRequestWillBeRepoRefSubscribedIfUnsupported() {
        when(capabilitiesClient.getWebhookSupportedEvents()).thenReturn(new BitbucketWebhookSupportedEvents(new HashSet<>(asList(REPO_REF_CHANGE.getEventIds().get(0)))));
        BitbucketWebhook result = handler.register(defaultBuilder.isMirror(true).shouldTriggerOnPush(true).build());

        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(REPO_REF_CHANGE.getEventIds().get(0)));
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

    private String[] getEventIdAsStrings(BitbucketWebhookEvent... event) {
        List<String> list = new ArrayList<>();
        for (BitbucketWebhookEvent e : event) {
            list.addAll(e.getEventIds());
        }
        return list.toArray(new String[0]);
    }
}