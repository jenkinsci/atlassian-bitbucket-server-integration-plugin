package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import hudson.model.Item;
import hudson.scm.SCMDescriptor;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.Optional;

import static java.util.Collections.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

public class BitbucketSCMTest {

    @Test
    public void testCredentialsIdAreSavedIfServerIdNotSelected() {
        String credentialsId = "valid-credentials";
        BitbucketSCM bitbucketSCM = createInstance(credentialsId);

        assertThat(bitbucketSCM.getCredentialsId(), is(equalTo(credentialsId)));
    }

    @Test
    public void testCredentialAndServerIdSaved() {
        String credentialsId = "valid-credentials";
        String serverId = "serverId1";

        BitbucketSCM bitbucketSCM = createInstance(credentialsId, serverId);

        assertThat(bitbucketSCM.getCredentialsId(), is(equalTo(credentialsId)));
        assertThat(bitbucketSCM.getServerId(), is(equalTo(serverId)));
    }

    @Test
    public void testCredentialServerProjectSaved() {
        String credentialsId = "valid-credentials";
        String serverId = "serverId1";
        String projectName = "proj1";

        BitbucketSCM bitbucketSCM = createInstance(credentialsId, serverId, projectName);

        assertThat(bitbucketSCM.getCredentialsId(), is(equalTo(credentialsId)));
        assertThat(bitbucketSCM.getServerId(), is(equalTo(serverId)));
        assertThat(bitbucketSCM.getProjectName(), is(equalTo(projectName)));
    }

    @Test
    public void testPrivateProjectName() {
        String credentialsId = "valid-credentials";
        String serverId = "serverId1";
        String projectName = "USER";
        String projectKey = "~USER";

        BitbucketSCMRepository scmRepository =
                new BitbucketSCMRepository(credentialsId, null, projectName, projectKey, "", "", serverId, "");
        BitbucketSCM scm = spy(createInstance(credentialsId, serverId));
        doReturn(scmRepository).when(scm).getBitbucketSCMRepository();

        assertEquals(projectKey, scm.getProjectName());
    }

    @Test
    public void testProjectName() {
        String credentialsId = "valid-credentials";
        String serverId = "serverId1";
        String projectName = "Project 1";
        String projectKey = "PROJECT_1";

        BitbucketSCMRepository scmRepository =
                new BitbucketSCMRepository(credentialsId, null, projectName, projectKey, "", "", serverId, "");
        BitbucketSCM scm = spy(createInstance(credentialsId, serverId));
        doReturn(scmRepository).when(scm).getBitbucketSCMRepository();

        assertEquals(projectName, scm.getProjectName());
    }

    @Test
    public void testInitializeScm() {
        String credentialsId = "valid-credentials";
        String serverId = "serverId1";
        String projectName = "Project 1";
        String projectKey = "PROJECT_1";
        String repoName = "Repository 1";
        String repoSlug = "rep_1";
        String cloneUrl = "http://upstream";

        BitbucketSCM.DescriptorImpl descriptor = mockDescriptor();
        mockFetchedRepository(descriptor, projectName, projectKey, repoName, repoSlug, cloneUrl, "", "");
        BitbucketSCM scm = spy(new BitbucketSCM("1", emptyList(), credentialsId, "", emptyList(), "", projectName,
                repoName, serverId, "") {
            @Override
            public SCMDescriptor<?> getDescriptor() {
                return descriptor;
            }
        });

        BitbucketSCMRepository scmRepository = new BitbucketSCMRepository(credentialsId, null, projectName,
                projectKey, repoName, repoSlug, serverId, "");
        doReturn(scmRepository).when(scm).getBitbucketSCMRepository();

        Item item = mock(Item.class);
        scm.getAndInitializeGitScmIfNull(item);

        assertEquals(cloneUrl, scm.getGitSCM().getUserRemoteConfigs().get(0).getUrl());
    }

    @Test
    public void testInitializeScmForMirror() {
        String credentialsId = "valid-credentials";
        String serverId = "serverId1";
        String projectName = "Project 1";
        String projectKey = "PROJECT_1";
        String repoName = "Repository 1";
        String repoSlug = "rep_1";
        String mirrorName = "myMirror";
        String upstreamUrl = "http://upstream";
        String mirrorUrl = "http://mirror";

        BitbucketSCM.DescriptorImpl descriptor = mockDescriptor();
        mockFetchedRepository(descriptor, projectName, projectKey, repoName, repoSlug, upstreamUrl, mirrorName, mirrorUrl);

        BitbucketSCM scm = spy(new BitbucketSCM("1", emptyList(), credentialsId, "", emptyList(), "", projectName,
                repoName, serverId, mirrorName) {
            @Override
            public SCMDescriptor<?> getDescriptor() {
                return descriptor;
            }
        });

        BitbucketSCMRepository scmRepository = new BitbucketSCMRepository(credentialsId, null, projectName,
                projectKey, repoName, repoSlug, serverId, mirrorName);
        doReturn(scmRepository).when(scm).getBitbucketSCMRepository();

        Item item = mock(Item.class);
        scm.getAndInitializeGitScmIfNull(item);

        assertEquals(mirrorUrl, scm.getGitSCM().getUserRemoteConfigs().get(0).getUrl());
    }

    private BitbucketSCM createInstance(String credentialId) {
        return createInstance(credentialId, null);
    }

    private BitbucketSCM createInstance(String credentialId, String serverId) {
        return createInstance(credentialId, serverId, null);
    }

    private BitbucketSCM createInstance(String credentialId, String serverId, String projectName) {
        return createInstance(credentialId, serverId, projectName, null);
    }

    private BitbucketSCM createInstance(String credentialId, String serverId, String projectName, String repo) {
        return createInstance(credentialId, serverId, projectName, repo, null);
    }

    private BitbucketSCM createInstance(String credentialsId, String serverId, String project, String repo,
                                        String mirror) {
        return new BitbucketSCM(
                "1",
                emptyList(),
                credentialsId,
                "",
                emptyList(),
                "",
                project,
                repo,
                serverId,
                mirror) {
            @Override
            public SCMDescriptor<?> getDescriptor() {
                return mockDescriptor();
            }
        };
    }

    private BitbucketSCM.DescriptorImpl mockDescriptor() {
        BitbucketServerConfiguration bitbucketServerConfiguration = mock(BitbucketServerConfiguration.class);
        doReturn(mock(GlobalCredentialsProvider.class))
                .when(bitbucketServerConfiguration).getGlobalCredentialsProvider(any(String.class));
        BitbucketSCM.DescriptorImpl descriptor = mock(BitbucketSCM.DescriptorImpl.class);
        when(descriptor.getConfiguration(argThat(serverId -> !isBlank(serverId))))
                .thenReturn(Optional.of(bitbucketServerConfiguration));
        when(descriptor.getConfiguration(argThat(StringUtils::isBlank)))
                .thenReturn(Optional.empty());
        when(descriptor.getBitbucketScmHelper(
                nullable(String.class),
                nullable(BitbucketTokenCredentials.class)))
                .thenReturn(mock(BitbucketScmHelper.class));
        return descriptor;
    }

    private void mockFetchedRepository(BitbucketSCM.DescriptorImpl descriptor, String projectName, String projectKey,
                                       String repoName, String repoSlug, String upstreamUrl,
                                       String mirrorName, String mirrorUrl) {
        BitbucketProject bitbucketProject = new BitbucketProject(projectKey, emptyMap(), projectName);
        BitbucketRepository bitbucketRepository = new BitbucketRepository(1, repoName, bitbucketProject, repoSlug,
                RepositoryState.AVAILABLE, singletonList(new BitbucketNamedLink("http", upstreamUrl)), "");
        BitbucketScmHelper scmHelper = descriptor.getBitbucketScmHelper(null, null);
        when(scmHelper.getRepository(projectName, repoName)).thenReturn(bitbucketRepository);
        BitbucketMirroredRepository mirroringDetails = new BitbucketMirroredRepository(true, singletonMap("clone",
                singletonList(new BitbucketNamedLink("http", mirrorUrl))), mirrorName, 1,
                BitbucketMirroredRepositoryStatus.AVAILABLE);
        BitbucketMirrorHandler mirrorHandler = mock(BitbucketMirrorHandler.class);
        EnrichedBitbucketMirroredRepository mirroredRepository = new EnrichedBitbucketMirroredRepository(bitbucketRepository, mirroringDetails);
        when(mirrorHandler.fetchRepository(any())).thenReturn(mirroredRepository);
        when(descriptor.createMirrorHandler(any())).thenReturn(mirrorHandler);
    }
}