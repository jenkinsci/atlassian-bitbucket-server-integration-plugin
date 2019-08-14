package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketUser;
import hudson.model.*;
import hudson.util.SequentialExecutionQueue;
import jenkins.triggers.SCMTriggerItem;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketWebhookTriggerImplTest {

    private SequentialExecutionQueue queue = mock(SequentialExecutionQueue.class);
    private BitbucketWebhookTriggerImpl.BitbucketWebhookTriggerDescriptor descriptor =
            new BitbucketWebhookTriggerImpl.BitbucketWebhookTriggerDescriptor(queue);

    @Test
    public void testDescriptorIsApplicableForNonSCMTriggerItem() {
        assertFalse(descriptor.isApplicable(mock(Item.class)));
    }

    @Test
    public void testDescriptorIsApplicableForSCMTriggerItem() {
        assertTrue(descriptor.isApplicable(mock(Item.class, withSettings().extraInterfaces(SCMTriggerItem.class))));
    }

    @Test
    public void testDescriptorIsApplicableForSCMedItem() {
        assertTrue(descriptor.isApplicable(mock(Item.class, withSettings().extraInterfaces(SCMedItem.class))));
    }

    @Test
    public void testTrigger() {
        BitbucketWebhookTriggerImpl.BitbucketWebhookTriggerDescriptor mockDescriptor = mock(BitbucketWebhookTriggerImpl.BitbucketWebhookTriggerDescriptor.class);
        BitbucketWebhookTriggerImpl trigger =
                new BitbucketWebhookTriggerImpl() {

                    @Override
                    public BitbucketWebhookTriggerImpl.BitbucketWebhookTriggerDescriptor
                    getDescriptor() {
                        // There is no running Jenkins instance, so Trigger.getDescriptor won't work in
                        // the test.
                        return mockDescriptor;
                    }
                };
        FreeStyleProject project = mock(FreeStyleProject.class);
        Hudson itemGroup = mock(Hudson.class);
        when(itemGroup.getFullName()).thenReturn("Item name");
        when(project.getParent()).thenReturn(itemGroup);
        when(project.getName()).thenReturn("Project name");
        BitbucketUser user = new BitbucketUser("me", "me@test.atlassian", "Me");
        BitbucketWebhookTriggerRequest request = BitbucketWebhookTriggerRequest.builder()
                .actor(user)
                .build();

        trigger.start(project, true);
        trigger.trigger(request);
        verify(mockDescriptor).schedule(eq(project), eq(project), eq(request));
    }

    @Test
    public void testDescriptorSchedule() {
        Job job = mock(Job.class);
        SCMTriggerItem triggerItem = mock(SCMTriggerItem.class);
        BitbucketUser user = new BitbucketUser("me", "me@test.atlassian", "Me");
        BitbucketWebhookTriggerRequest request = BitbucketWebhookTriggerRequest.builder()
                .actor(user)
                .build();
        CauseAction causeAction = new CauseAction(new BitbucketWebhookTriggerCause(request));
        BitbucketTriggerWorker expectedValue = new BitbucketTriggerWorker(job, triggerItem,
                causeAction, request.getAdditionalActions());

        descriptor.schedule(job, triggerItem, request);
        verify(queue).execute(argThat((ArgumentMatcher<BitbucketTriggerWorker>) argument -> deepEqual(expectedValue, argument)));
    }

    /**
     * Since the BitbucketTriggerWorker has a specific requirement around equals that does not consider all the things in the
     * class this method is a substitute to compare everything we need for testing purposes.
     */
    private boolean deepEqual(BitbucketTriggerWorker expected, BitbucketTriggerWorker actual) {

        List<Action> expectedActions = expected.getActions();
        List<Action> actualActions = actual.getActions();
        if (expectedActions.size() != actualActions.size()) {
            return false;
        }
        //Cause does not have an equals so we need to manually compare the fields.
        for (int i = 0; i < expectedActions.size(); i++) {
            Action action = expectedActions.get(i);
            Action thatAction = actualActions.get(i);
            if (action.getClass() != thatAction.getClass()) {
                return false;
            }
            boolean equals = Objects.equals(action.getDisplayName(), thatAction.getDisplayName()) &&
                    Objects.equals(action.getIconFileName(), thatAction.getIconFileName()) &&
                    Objects.equals(action.getUrlName(), thatAction.getUrlName());
            if (!equals) {
                return false;
            }
            //Cause actions have more fields and do not necessarily differ in the fields tested above
            if (action instanceof CauseAction && thatAction instanceof CauseAction) {
                if (!((CauseAction) action).getCauses().equals(((CauseAction) thatAction).getCauses())) {
                    return false;
                }
            }
        }
        return Objects.equals(expected.getJob(), actual.getJob()) &&
                Objects.equals(expected.getTriggerItem(), actual.getTriggerItem());
    }
}