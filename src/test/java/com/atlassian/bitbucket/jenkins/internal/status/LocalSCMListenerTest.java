package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import hudson.model.*;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RemoteConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner.Silent;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@RunWith(Silent.class)
public class LocalSCMListenerTest {

    @Mock
    private GitSCM gitSCM;
    @Mock
    private BuildStatusPoster buildStatusPoster;
    @Mock
    private AbstractBuild run;
    @Mock
    private TaskListener taskListener;
    @Mock
    private BitbucketSCM bitbucketSCM;
    @Mock
    private BitbucketSCMRepository scmRepository;
    private LocalSCMListener listener;
    private Map<String, String> buildMap = new HashMap<>();

    @Before
    public void setup() throws URISyntaxException {
        buildMap.put(GitSCM.GIT_BRANCH, "master");
        buildMap.put(GitSCM.GIT_COMMIT, "c1");
        when(bitbucketSCM.getGitSCM()).thenReturn(gitSCM);
        doAnswer(invocation -> {
            Map<String, String> m = (Map<String, String>) invocation.getArguments()[1];
            m.putAll(buildMap);
            return null;
        }).when(gitSCM).buildEnvironment(notNull(), anyMap());
        RemoteConfig rc = new RemoteConfig(new Config(), "origin");
        when(gitSCM.getRepositories()).thenReturn(singletonList(rc));
        when(scmRepository.getRepositorySlug()).thenReturn("repo1");
        when(bitbucketSCM.getServerId()).thenReturn("ServerId");
        when(bitbucketSCM.getBitbucketSCMRepository()).thenReturn(scmRepository);
        listener = new LocalSCMListener(buildStatusPoster);
    }

    @Test
    public void testListenerDoNotActForNonGitSCM() {
        SCM scm = mock(SCM.class);

        listener.onCheckout(run, scm, null, taskListener, null, null);

        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void buildStatusForFreestyleWithBitbucketSCMArePosted() {
        FreeStyleProject project = mock(FreeStyleProject.class);
        FreeStyleBuild build = mock(FreeStyleBuild.class);
        when(build.getParent()).thenReturn(project);

        listener.onCheckout(build, bitbucketSCM, null, taskListener, null, null);

        verify(buildStatusPoster).postBuildStatus(
                argThat(
                        revision -> revision.getBitbucketSCMRepo().equals(scmRepository)),
                argThat(r -> r.equals(build)),
                argThat(tl -> tl.equals(taskListener)));
    }

    @Test
    public void buildStatusForPipelineWithJenkinsFileFromBitbucketSCM() throws Exception {
        listener = new TestLocalSCMListener(true, buildStatusPoster);

        //Can't mock WorkFlow classes so using Project instead.
        Project job = mock(Project.class);
        Run run = mock(Run.class);
        when(run.getParent()).thenReturn(job);
        when(job.getSCMs()).thenReturn(singletonList(bitbucketSCM));
        when(gitSCM.getKey()).thenReturn("repo-key");

        listener.onCheckout(run, gitSCM, null, taskListener, null, null);

        verify(buildStatusPoster).postBuildStatus(
                argThat(
                        revision -> revision.getBitbucketSCMRepo().equals(scmRepository)),
                argThat(r -> r.equals(run)),
                argThat(tl -> tl.equals(taskListener)));
    }

    private static class TestLocalSCMListener extends LocalSCMListener {

        private final boolean isWorkflowRun;

        private TestLocalSCMListener(boolean isWorkflowRun, BuildStatusPoster buildStatusPoster) {
            super(buildStatusPoster);
            this.isWorkflowRun = isWorkflowRun;
        }

        @Override
        boolean isWorkflowRun(Run<?, ?> build) {
            return isWorkflowRun;
        }
    }
}