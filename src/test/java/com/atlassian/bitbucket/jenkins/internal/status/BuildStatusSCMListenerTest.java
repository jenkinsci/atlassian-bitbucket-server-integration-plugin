package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRevisionAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class BuildStatusSCMListenerTest {

    @Mock
    Run<?, ?> build;
    @InjectMocks
    BuildStatusSCMListener buildStatusSCMListener;
    @Mock
    BuildStatusPoster poster;
    @Mock
    TaskListener taskListener;

    @Test
    public void testBuildStatusNotPostedOnCheckoutWhenNoRevision() {
        buildStatusSCMListener.onCheckout(build, null, null, taskListener, null, null);
        verifyZeroInteractions(poster);
    }

    @Test
    public void testBuildStatusPostedOnCheckout() {
        BitbucketSCMRevisionAction action = mock(BitbucketSCMRevisionAction.class);
        when(build.getAction(BitbucketSCMRevisionAction.class)).thenReturn(action);

        buildStatusSCMListener.onCheckout(build, null, null, taskListener, null, null);

        verify(poster).postBuildStatus(action, build, taskListener);
    }

}