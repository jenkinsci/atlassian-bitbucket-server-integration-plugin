package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketUser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BitbucketWebhookTriggerCauseTest {

    @Test
    public void testTriggerWithAuthor() {
        BitbucketUser user = new BitbucketUser("me", "me@test.atlassian", "Me");
        String shortDescription = new BitbucketWebhookTriggerCause(BitbucketWebhookTriggerRequest.builder()
                .actor(user)
                .build()).getShortDescription();
        assertEquals(Messages.BitbucketWebhookTriggerCause_withAuthor(user.getDisplayName()), shortDescription);
    }

    @Test
    public void testTriggerWithoutAuhtor() {
        String shortDescription = new BitbucketWebhookTriggerCause(BitbucketWebhookTriggerRequest.builder().build()).getShortDescription();
        assertEquals(Messages.BitbucketWebhookTriggerCause_withoutAuthor(), shortDescription);
    }
}