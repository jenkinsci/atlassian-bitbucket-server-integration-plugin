package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import hudson.scm.SCMDescriptor;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
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
                Collections.emptyList(),
                credentialsId,
                "",
                Collections.emptyList(),
                "",
                project,
                repo,
                serverId,
                mirror) {
            @Override
            public SCMDescriptor<?> getDescriptor() {
                BitbucketServerConfiguration bitbucketServerConfiguration = mock(BitbucketServerConfiguration.class);
                DescriptorImpl descriptor = mock(DescriptorImpl.class);
                when(descriptor.getConfiguration(argThat(serverId -> !isBlank(serverId))))
                        .thenReturn(Optional.of(bitbucketServerConfiguration));
                when(descriptor.getConfiguration(argThat(StringUtils::isBlank)))
                        .thenReturn(Optional.empty());
                when(descriptor.getBitbucketScmHelper(
                        nullable(String.class),
                        nullable(GlobalCredentialsProvider.class),
                        nullable(String.class)))
                        .thenReturn(mock(BitbucketScmHelper.class));
                return descriptor;
            }
        };
    }
}