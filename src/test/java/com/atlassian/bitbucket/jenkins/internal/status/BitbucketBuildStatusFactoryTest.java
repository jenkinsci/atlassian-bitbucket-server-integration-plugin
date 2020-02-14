package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BuildState;
import hudson.model.AbstractBuild;
import hudson.model.Project;
import hudson.model.Result;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketBuildStatusFactoryTest {

    private static final String BUILD_URL = "http://www.example.com:8000";
    private static final String BUILD_DISPLAY_NAME = "#15";
    private static final String BUILD_DURATION = "400 secs";
    private static final String PROJECT_NAME = "Project Name";

    @Rule
    public final JenkinsRule jenkinsRule = new JenkinsRule();
    @Mock
    private AbstractBuild build;
    @Mock
    private Jenkins parent;
    @Mock
    private Project project;

    @Before
    public void setup() {
        when(build.getUrl()).thenReturn(BUILD_URL);
        when(build.getDisplayName()).thenReturn(BUILD_DISPLAY_NAME);
        when(build.getDurationString()).thenReturn(BUILD_DURATION);
        when(build.getParent()).thenReturn(project);
        when(project.getName()).thenReturn(PROJECT_NAME);
        when(project.getDisplayName()).thenReturn(PROJECT_NAME);
        when(project.getParent()).thenReturn(parent);
        when(parent.getFullName()).thenReturn("");
        when(parent.getFullDisplayName()).thenReturn("");
    }

    @Test
    public void testBuildInProgressStatus() {
        when(build.isBuilding()).thenReturn(true);

        BitbucketBuildStatus result = BitbucketBuildStatusFactory.fromBuild(build);

        assertThat(result.getState(), equalTo(BuildState.INPROGRESS.toString()));
    }

    @Test
    public void testBuildUnstableStatus() {
        when(build.isBuilding()).thenReturn(false);
        when(build.getResult()).thenReturn(Result.UNSTABLE);

        BitbucketBuildStatus result = BitbucketBuildStatusFactory.fromBuild(build);

        assertThat(result.getState(), equalTo(BuildState.SUCCESSFUL.toString()));
    }

    @Test
    public void testFullBuildSuccessfulStatus() {
        when(build.isBuilding()).thenReturn(false);
        when(build.getResult()).thenReturn(Result.SUCCESS);

        BitbucketBuildStatus result = BitbucketBuildStatusFactory.fromBuild(build);

        assertThat(result.getName(), equalTo(PROJECT_NAME));
        assertThat(result.getDescription(), equalTo(BuildState.SUCCESSFUL.getDescriptiveText(
                BUILD_DISPLAY_NAME, BUILD_DURATION)));
        assertThat(result.getKey(), equalTo(PROJECT_NAME));
        assertThat(result.getState(), equalTo(BuildState.SUCCESSFUL.toString()));
        assertThat(result.getUrl(), equalTo(DisplayURLProvider.get().getRunURL(build)));
    }

    @Test
    public void testBuildFailedStatus() {
        when(build.isBuilding()).thenReturn(false);
        when(build.getResult()).thenReturn(Result.FAILURE);

        BitbucketBuildStatus result = BitbucketBuildStatusFactory.fromBuild(build);

        assertThat(result.getState(), equalTo(BuildState.FAILED.toString()));
    }
}