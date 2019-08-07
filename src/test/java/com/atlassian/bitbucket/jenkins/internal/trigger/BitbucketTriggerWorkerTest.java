package com.atlassian.bitbucket.jenkins.internal.trigger;

import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.scm.PollingResult;
import hudson.util.StreamTaskListener;
import jenkins.model.RunAction2;
import jenkins.triggers.SCMTriggerItem;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.nio.file.Files;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketTriggerWorkerTest {

    @Mock
    private CauseAction causeAction;
    @Mock
    private Job job;
    @Mock
    private SCMTriggerItem triggerItem;

    private File tempDir;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("BitbucketTriggerWorkerTest").toFile();
        when(job.getRootDir()).thenReturn(tempDir);
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(tempDir);
    }

    @Test
    public void testTriggerBuildNow() {
        when(triggerItem.poll(any(StreamTaskListener.class))).thenReturn(PollingResult.BUILD_NOW);

        BitbucketTriggerWorker worker = new BitbucketTriggerWorker(job, triggerItem, causeAction, emptyList());
        worker.run();
        verify(triggerItem).poll(any(StreamTaskListener.class));
        verify(triggerItem).scheduleBuild2(eq(0), eq(causeAction));
    }

    @Test
    public void testTriggerNoChanges() {
        when(triggerItem.poll(any(StreamTaskListener.class))).thenReturn(PollingResult.NO_CHANGES);

        BitbucketTriggerWorker worker = new BitbucketTriggerWorker(job, triggerItem, causeAction, emptyList());

        worker.run();
        verify(triggerItem).poll(any(StreamTaskListener.class));
        verify(triggerItem, never()).scheduleBuild2(anyInt(), any(CauseAction.class));
    }

    @Test
    public void testTriggerSignificantChanges() {
        when(triggerItem.poll(any(StreamTaskListener.class))).thenReturn(PollingResult.SIGNIFICANT);

        BitbucketTriggerWorker worker = new BitbucketTriggerWorker(job, triggerItem, causeAction, emptyList());
        worker.run();
        verify(triggerItem).poll(any(StreamTaskListener.class));
        verify(triggerItem).scheduleBuild2(eq(0), eq(causeAction));
    }

    @Test
    public void testTriggerWithAdditionalActions() {
        when(triggerItem.poll(any(StreamTaskListener.class))).thenReturn(PollingResult.SIGNIFICANT);

        RunAction2 additionalAction = mock(RunAction2.class);
        BitbucketTriggerWorker worker = new BitbucketTriggerWorker(job, triggerItem, causeAction, singletonList(additionalAction));

        worker.run();
        verify(triggerItem).poll(any(StreamTaskListener.class));
        verify(triggerItem).scheduleBuild2(eq(0), eq(causeAction), eq(additionalAction));
    }
}