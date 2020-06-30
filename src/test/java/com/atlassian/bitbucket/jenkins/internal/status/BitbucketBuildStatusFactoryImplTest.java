package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BuildState;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import hudson.model.*;
import hudson.tasks.junit.TestResultAction;
import jenkins.branch.MultiBranchProject;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketBuildStatusFactoryImplTest {

    private static final String BUILD_DURATION = "400 secs";
    private static final String BUILD_ID = "15";
    private static final String BUILD_DISPLAY_NAME = "#" + BUILD_ID;
    private static final String BASE_URL = "http://www.example.com:8000";
    private static final String FREESTYLE_PROJECT_NAME = "Freestyle Project";
    private static final String FREESTYLE_BUILD_URL = "/job/Freestyle%20Project/15";
    private static final String WORKFLOW_JOB_NAME = "folder/branch";
    private static final String WORKFLOW_PROJECT_NAME = "MultiBranch Project";
    //Yeah that's actually what happens.
    private static final String WORKFLOW_BUILD_URL = "/job/MultiBranch%20Project/job/folder%252Fbranch/15";

    @Mock
    private JenkinsProvider jenkinsProvider;
    @Mock
    private Run freeStyleRun;
    private Job freeStyleProject;
    @Mock
    private Jenkins parent;
    @Mock
    private Run workflowRun;
    private Job workflowJob;
    @Mock
    private MultiBranchProject<?, ?> workflowProject;

    @Before
    public void setup() {
        when(workflowProject.getName()).thenReturn(WORKFLOW_PROJECT_NAME);

        freeStyleProject = new FreeStyleProject((ItemGroup) parent, FREESTYLE_PROJECT_NAME);
        workflowJob = new WorkflowJob(workflowProject, WORKFLOW_JOB_NAME);
        when(jenkinsProvider.get()).thenReturn(parent);
        when(parent.getRootUrl()).thenReturn(BASE_URL);

        when(freeStyleRun.getId()).thenReturn(BUILD_ID);
        when(freeStyleRun.getDisplayName()).thenReturn(BUILD_DISPLAY_NAME);
        when(freeStyleRun.getDurationString()).thenReturn(BUILD_DURATION);
        when(freeStyleRun.getParent()).thenReturn(freeStyleProject);
        when(freeStyleRun.getUrl()).thenReturn(FREESTYLE_BUILD_URL);
        when(workflowRun.getId()).thenReturn(BUILD_ID);
        when(workflowRun.getDisplayName()).thenReturn(BUILD_DISPLAY_NAME);
        when(workflowRun.getDurationString()).thenReturn(BUILD_DURATION);
        when(workflowRun.getUrl()).thenReturn(WORKFLOW_BUILD_URL);
        doReturn(workflowJob).when(workflowRun).getParent();

        when(parent.getFullName()).thenReturn("");
        doReturn(parent).when(workflowProject).getParent();
    }

    @Test
    public void testBuildStatusFailed() {
        when(workflowRun.isBuilding()).thenReturn(false);
        when(workflowRun.getResult()).thenReturn(Result.FAILURE);

        BitbucketBuildStatus result = createBitbucketBuildStatus(workflowRun);

        assertThat(result.getState(), equalTo(BuildState.FAILED.toString()));
    }

    @Test
    public void testBuildStatusFreestyleSuccessful() {
        when(freeStyleRun.isBuilding()).thenReturn(false);
        when(freeStyleRun.getResult()).thenReturn(Result.SUCCESS);

        BitbucketBuildStatus result = createBitbucketBuildStatus(freeStyleRun);

        assertThat(result.getName(), equalTo(FREESTYLE_PROJECT_NAME));
        assertThat(result.getDescription(), equalTo(BuildState.SUCCESSFUL.getDescriptiveText(
                BUILD_DISPLAY_NAME, BUILD_DURATION)));
        assertThat(result.getKey(), equalTo(freeStyleProject.getFullName()));
        assertThat(result.getState(), equalTo(BuildState.SUCCESSFUL.toString()));
        assertThat(result.getUrl(), equalTo(BASE_URL + FREESTYLE_BUILD_URL));
    }

    @Test
    public void testBuildStatusInProgress() {
        when(workflowRun.isBuilding()).thenReturn(true);

        BitbucketBuildStatus result = createBitbucketBuildStatus(workflowRun);

        assertThat(result.getState(), equalTo(BuildState.INPROGRESS.toString()));
    }

    @Test
    public void testBuildStatusUnstable() {
        when(workflowRun.isBuilding()).thenReturn(false);
        when(workflowRun.getResult()).thenReturn(Result.UNSTABLE);

        BitbucketBuildStatus result = createBitbucketBuildStatus(workflowRun);

        assertThat(result.getState(), equalTo(BuildState.SUCCESSFUL.toString()));
    }

    @Test
    public void testBuildStatusWorkflowSuccessful() {
        when(workflowRun.isBuilding()).thenReturn(false);
        when(workflowRun.getResult()).thenReturn(Result.SUCCESS);

        BitbucketBuildStatus result = createBitbucketBuildStatus(workflowRun);

        assertThat(result.getName(), equalTo(WORKFLOW_JOB_NAME));
        assertThat(result.getDescription(), equalTo(BuildState.SUCCESSFUL.getDescriptiveText(
                BUILD_DISPLAY_NAME, BUILD_DURATION)));
        assertThat(result.getKey(), equalTo(workflowJob.getFullName()));
        assertThat(result.getState(), equalTo(BuildState.SUCCESSFUL.toString()));
        assertThat(result.getUrl(), equalTo(BASE_URL + WORKFLOW_BUILD_URL));
    }

    @Test
    public void testDurationIsNotSetForInProgress() {
        when(workflowRun.isBuilding()).thenReturn(true);
        BitbucketBuildStatus result = createBitbucketBuildStatus(workflowRun);

        assertThat(result.getDuration(), nullValue());
    }

    @Test
    public void testModernBuildStatusFreestyleSuccessful() {
        long duration = 123456L;
        int failCount = 1;
        int skipCount = 2;
        int passCount = 3;
        TestResultAction testResultAction = mock(TestResultAction.class);
        when(freeStyleRun.isBuilding()).thenReturn(false);
        when(freeStyleRun.getResult()).thenReturn(Result.SUCCESS);
        when(freeStyleRun.getDuration()).thenReturn(duration);
        when(freeStyleRun.getAction(TestResultAction.class)).thenReturn(testResultAction);
        when(testResultAction.getFailCount()).thenReturn(failCount);
        when(testResultAction.getSkipCount()).thenReturn(skipCount);
        when(testResultAction.getTotalCount()).thenReturn(failCount + skipCount + passCount);

        BitbucketBuildStatus result = createBitbucketBuildStatus(freeStyleRun, true);

        assertThat(result.getName(), equalTo(freeStyleProject.getDisplayName()));
        assertThat(result.getDescription(), equalTo(BuildState.SUCCESSFUL.getDescriptiveText(
                BUILD_DISPLAY_NAME, BUILD_DURATION)));
        assertThat(result.getKey(), equalTo(freeStyleProject.getFullName()));
        assertThat(result.getState(), equalTo(BuildState.SUCCESSFUL.toString()));
        assertThat(result.getUrl(), equalTo(BASE_URL + FREESTYLE_BUILD_URL));
        assertThat(result.getParent(), equalTo(freeStyleProject.getFullName()));
        assertThat(result.getDuration(), equalTo(duration));
        assertThat(result.getTestResults(), notNullValue());
        assertThat(result.getTestResults().getFailed(), equalTo(failCount));
        assertThat(result.getTestResults().getIgnored(), equalTo(skipCount));
        assertThat(result.getTestResults().getSuccessful(), equalTo(passCount));
    }

    @Test
    public void testModernBuildStatusWorkflowSuccessful() {
        long duration = 123456L;
        int failCount = 1;
        int skipCount = 2;
        int passCount = 3;
        TestResultAction testResultAction = mock(TestResultAction.class);
        when(workflowRun.isBuilding()).thenReturn(false);
        when(workflowRun.getResult()).thenReturn(Result.SUCCESS);
        when(workflowRun.getDuration()).thenReturn(duration);
        when(workflowRun.getAction(TestResultAction.class)).thenReturn(testResultAction);
        when(testResultAction.getFailCount()).thenReturn(failCount);
        when(testResultAction.getSkipCount()).thenReturn(skipCount);
        when(testResultAction.getTotalCount()).thenReturn(failCount + skipCount + passCount);

        BitbucketBuildStatus result = createBitbucketBuildStatus(workflowRun, true);

        assertThat(result.getName(), equalTo(workflowJob.getDisplayName()));
        assertThat(result.getDescription(), equalTo(BuildState.SUCCESSFUL.getDescriptiveText(
                BUILD_DISPLAY_NAME, BUILD_DURATION)));
        assertThat(result.getKey(), equalTo(workflowJob.getFullName()));
        assertThat(result.getState(), equalTo(BuildState.SUCCESSFUL.toString()));
        assertThat(result.getUrl(), equalTo(BASE_URL + WORKFLOW_BUILD_URL));
        assertThat(result.getParent(), equalTo(workflowProject.getFullName()));
        assertThat(result.getDuration(), equalTo(duration));
        assertThat(result.getTestResults(), notNullValue());
        assertThat(result.getTestResults().getFailed(), equalTo(failCount));
        assertThat(result.getTestResults().getIgnored(), equalTo(skipCount));
        assertThat(result.getTestResults().getSuccessful(), equalTo(passCount));
    }

    private BitbucketBuildStatus createBitbucketBuildStatus(Run<?, ?> run) {
        return createBitbucketBuildStatus(run, false);
    }

    private BitbucketBuildStatus createBitbucketBuildStatus(Run<?, ?> run, boolean createRich) {
        BitbucketBuildStatusFactoryImpl statusFactory =
                new BitbucketBuildStatusFactoryImpl(jenkinsProvider);
        return createRich ? statusFactory.createRichBuildStatus(run) :
                            statusFactory.createLegacyBuildStatus(run);
    }
}
