package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BuildState;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import hudson.model.AbstractBuild;
import hudson.model.Project;
import hudson.model.Result;
import hudson.tasks.junit.TestResultAction;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
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

    private static final String BUILD_DISPLAY_NAME = "#15";
    private static final String BUILD_DURATION = "400 secs";
    private static final String BUILD_URL = "http://www.example.com:8000";
    private static final String PROJECT_NAME = "Project Name";

    @Mock
    private JenkinsProvider jenkinsProvider;
    @Mock
    private DisplayURLProvider displayURLProvider;
    @Mock
    private AbstractBuild build;
    @Mock
    private Jenkins parent;
    @Mock
    private Project project;

    @Before
    public void setup() {
        when(build.getDisplayName()).thenReturn(BUILD_DISPLAY_NAME);
        when(build.getDurationString()).thenReturn(BUILD_DURATION);
        when(build.getParent()).thenReturn(project);
        when(project.getName()).thenReturn(PROJECT_NAME);
        when(project.getDisplayName()).thenReturn(PROJECT_NAME);
        when(project.getParent()).thenReturn(parent);
        when(parent.getFullName()).thenReturn("");
        when(parent.getFullDisplayName()).thenReturn("");
        when(displayURLProvider.getRunURL(build)).thenReturn(BUILD_URL);
        Jenkins jenkins = mock(Jenkins.class);
        when(jenkinsProvider.get()).thenReturn(jenkins);
        when(jenkins.getRootUrl()).thenReturn("http://localhost:8080/jenkins");
    }

    @Test
    public void testBuildFailedStatus() {
        when(build.isBuilding()).thenReturn(false);
        when(build.getResult()).thenReturn(Result.FAILURE);

        BitbucketBuildStatus result = createBitbucketBuildStatus();

        assertThat(result.getState(), equalTo(BuildState.FAILED.toString()));
    }

    @Test
    public void testBuildInProgressStatus() {
        when(build.isBuilding()).thenReturn(true);

        BitbucketBuildStatus result = createBitbucketBuildStatus();

        assertThat(result.getState(), equalTo(BuildState.INPROGRESS.toString()));
    }

    @Test
    public void testBuildUnstableStatus() {
        when(build.isBuilding()).thenReturn(false);
        when(build.getResult()).thenReturn(Result.UNSTABLE);

        BitbucketBuildStatus result = createBitbucketBuildStatus();

        assertThat(result.getState(), equalTo(BuildState.SUCCESSFUL.toString()));
    }

    @Test
    public void testFullBuildSuccessfulStatus() {
        String externalId = project.getFullName() + BUILD_DISPLAY_NAME;
        long duration = 123456L;
        int failCount = 1;
        int skipCount = 2;
        int passCount = 3;
        TestResultAction testResultAction = mock(TestResultAction.class);
        when(build.isBuilding()).thenReturn(false);
        when(build.getResult()).thenReturn(Result.SUCCESS);
        when(build.getExternalizableId()).thenReturn(externalId);
        when(build.getDuration()).thenReturn(duration);
        when(build.getAction(TestResultAction.class)).thenReturn(testResultAction);
        when(testResultAction.getFailCount()).thenReturn(failCount);
        when(testResultAction.getSkipCount()).thenReturn(skipCount);
        when(testResultAction.getTotalCount()).thenReturn(6);

        BitbucketBuildStatus result = createBitbucketBuildStatus(true);

        assertThat(result.getName(), equalTo(PROJECT_NAME));
        assertThat(result.getDescription(), equalTo(BuildState.SUCCESSFUL.getDescriptiveText(
                BUILD_DISPLAY_NAME, BUILD_DURATION)));
        assertThat(result.getKey(), equalTo(PROJECT_NAME));
        assertThat(result.getState(), equalTo(BuildState.SUCCESSFUL.toString()));
        assertThat(result.getResultKey(), equalTo(externalId));
        assertThat(result.getDuration(), equalTo(duration));
        assertThat(result.getTestResults(), notNullValue());
        assertThat(result.getTestResults().getFailed(), equalTo(failCount));
        assertThat(result.getTestResults().getIgnored(), equalTo(skipCount));
        assertThat(result.getTestResults().getSuccessful(), equalTo(passCount));
        verify(displayURLProvider).getRunURL(build);
    }

    @Test
    public void testSuccessfulStatusOldBitbucket() {
        String externalId = project.getFullName() + BUILD_DISPLAY_NAME;
        when(build.isBuilding()).thenReturn(false);
        when(build.getResult()).thenReturn(Result.SUCCESS);
        when(build.getExternalizableId()).thenReturn(externalId);

        BitbucketBuildStatus result = createBitbucketBuildStatus();

        assertThat(result.getName(), equalTo(PROJECT_NAME));
        assertThat(result.getDescription(), equalTo(BuildState.SUCCESSFUL.getDescriptiveText(
                BUILD_DISPLAY_NAME, BUILD_DURATION)));
        assertThat(result.getKey(), equalTo(PROJECT_NAME));
        assertThat(result.getState(), equalTo(BuildState.SUCCESSFUL.toString()));
        assertThat(result.getResultKey(), equalTo(externalId));
        assertThat(result.getDuration(), nullValue());
        assertThat(result.getTestResults(), nullValue());
        verify(displayURLProvider).getRunURL(build);
    }

    @Test
    public void testDurationIsNotSetForInProgress() {
        when(build.isBuilding()).thenReturn(true);
        BitbucketBuildStatus result = createBitbucketBuildStatus();
        assertThat(result.getDuration(), nullValue());
    }

    private BitbucketBuildStatus createBitbucketBuildStatus() {
        return createBitbucketBuildStatus(false);
    }

    private BitbucketBuildStatus createBitbucketBuildStatus(boolean createRich) {
        BitbucketBuildStatusFactoryImpl statusFactory =
                new BitbucketBuildStatusFactoryImpl(jenkinsProvider, displayURLProvider);
        if (createRich) {
            return statusFactory.createRichBuildStatus(build);
        }
        return statusFactory.createLegacyBuildStatus(build);
    }
}