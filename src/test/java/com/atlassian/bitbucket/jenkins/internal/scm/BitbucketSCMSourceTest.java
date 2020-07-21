package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchTrigger;
import com.atlassian.bitbucket.jenkins.internal.trigger.RetryingWebhookHandler;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import jenkins.branch.MultiBranchProject;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSourceDescriptor;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

public class BitbucketSCMSourceTest {

    private static final String cloneLink = "http://localhost:7990/fake.git";

    @Test
    public void testBuild() {
        BitbucketSCMSource scmSource = createInstance("credentialsId", "serverId", "project", "repo");
        SCMHead scmHead = mock(SCMHead.class);
        when(scmHead.getName()).thenReturn("myBranch");
        SCM scm = scmSource.build(scmHead, null);
        assertTrue(scm instanceof GitSCM);
        GitSCM gitSCM = (GitSCM) scm;
        List<UserRemoteConfig> userRemoteConfigs = gitSCM.getUserRemoteConfigs();
        assertEquals(1, userRemoteConfigs.size());
        assertEquals(cloneLink, userRemoteConfigs.get(0).getUrl());
    }

    @Test
    public void testCredentialAndServerIdSaved() {
        String credentialsId = "valid-credentials";
        String serverId = "serverId1";

        BitbucketSCMSource bitbucketSCMsource = createInstance(credentialsId, serverId);

        assertThat(bitbucketSCMsource.getCredentialsId(), is(equalTo(credentialsId)));
        assertThat(bitbucketSCMsource.getServerId(), is(equalTo(serverId)));
    }

    @Test
    public void testCredentialServerProjectSaved() {
        String credentialsId = "valid-credentials";
        String serverId = "serverId1";
        String projectName = "proj1";

        BitbucketSCMSource bitbucketSCMsource = createInstance(credentialsId, serverId, projectName);

        assertThat(bitbucketSCMsource.getCredentialsId(), is(equalTo(credentialsId)));
        assertThat(bitbucketSCMsource.getServerId(), is(equalTo(serverId)));
        assertThat(bitbucketSCMsource.getProjectName(), is(equalTo(projectName)));
    }

    @Test
    public void testCredentialsIdAreSavedIfServerIdNotSelected() {
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = createInstance(credentialsId);

        assertThat(bitbucketSCMsource.getCredentialsId(), is(equalTo(credentialsId)));
    }

    @Test
    public void testAfterSaveDoesNothingIfIsInvalid() {
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId));
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);
        BitbucketWebhookMultibranchTrigger.DescriptorImpl triggerDesc =
                mock(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class);
        doReturn(singletonList(triggerDesc)).when(bitbucketSCMsource).getTriggers(any());

        bitbucketSCMsource.afterSave();

        verifyZeroInteractions(triggerDesc);
    }

    @Test
    public void testAfterSaveDoesNothingIfWebhookAlreadyRegistered() {
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId));
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);
        bitbucketSCMsource.setWebhookRegistered(true);
        doReturn(true).when(bitbucketSCMsource).isValid();
        BitbucketWebhookMultibranchTrigger.DescriptorImpl triggerDesc =
                mock(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class);
        doReturn(singletonList(triggerDesc)).when(bitbucketSCMsource).getTriggers(any());

        bitbucketSCMsource.afterSave();

        verifyZeroInteractions(triggerDesc);
    }

    @Test
    public void testAfterSaveRegistersWebhookIfNotAlreadyRegistered() {
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId));
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);
        doReturn(true).when(bitbucketSCMsource).isValid();
        BitbucketWebhookMultibranchTrigger.DescriptorImpl triggerDesc =
                mock(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class);
        doReturn(singletonList(triggerDesc)).when(bitbucketSCMsource).getTriggers(any());

        bitbucketSCMsource.afterSave();

        verify(triggerDesc).addTrigger(any(), same(bitbucketSCMsource));
    }

    private BitbucketSCMSource createInstance(String credentialId) {
        return createInstance(credentialId, null);
    }

    private BitbucketSCMSource createInstance(String credentialId, @Nullable String serverId) {
        return createInstance(credentialId, serverId, null);
    }

    private BitbucketSCMSource createInstance(String credentialId, @Nullable String serverId,
                                              @Nullable String projectName) {
        return createInstance(credentialId, serverId, projectName, null);
    }

    @SuppressWarnings("Duplicates")
    private BitbucketSCMSource createInstance(String credentialsId, @Nullable String serverId,
                                              @Nullable String projectName, @Nullable String repo) {
        return new BitbucketSCMSource(
                "1",
                credentialsId,
                Collections.emptyList(),
                projectName,
                repo,
                serverId,
                null) {
            @Override
            public SCMSourceDescriptor getDescriptor() {
                DescriptorImpl descriptor = mock(DescriptorImpl.class);
                BitbucketScmHelper scmHelper = mock(BitbucketScmHelper.class);
                BitbucketServerConfiguration bitbucketServerConfiguration = mock(BitbucketServerConfiguration.class);
                BitbucketRepository repository = mock(BitbucketRepository.class);

                when(descriptor.getConfiguration(argThat(serverId -> !isBlank(serverId))))
                        .thenReturn(Optional.of(bitbucketServerConfiguration));
                when(descriptor.getConfiguration(argThat(StringUtils::isBlank)))
                        .thenReturn(Optional.empty());
                when(descriptor.getBitbucketScmHelper(
                        nullable(String.class),
                        nullable(GlobalCredentialsProvider.class),
                        nullable(String.class)))
                        .thenReturn(scmHelper);
                when(descriptor.getRetryingWebhookHandler()).thenReturn(mock(RetryingWebhookHandler.class));
                when(scmHelper.getRepository(nullable(String.class), nullable(String.class))).thenReturn(repository);
                when(repository.getProject()).thenReturn(mock(BitbucketProject.class));
                when(repository.getCloneUrls()).thenReturn(singletonList(new BitbucketNamedLink("http", cloneLink)));

                return descriptor;
            }
        };
    }
}
