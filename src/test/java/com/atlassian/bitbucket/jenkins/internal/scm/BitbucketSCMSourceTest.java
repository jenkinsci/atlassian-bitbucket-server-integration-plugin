package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

public class BitbucketSCMSourceTest {

    private static final String httpCloneLink = "http://localhost:7990/fake.git";
    private static final String sshCloneLink = "ssh://git@localhost:7990/fake.git";

    @Test
    public void testBuildHttp() {
        BitbucketSCMSource scmSource = createInstance("credentialsId", "serverId", "project", "repo");
        SCMHead scmHead = mock(SCMHead.class);
        when(scmHead.getName()).thenReturn("myBranch");
        SCM scm = scmSource.build(scmHead, null);
        assertTrue(scm instanceof GitSCM);
        GitSCM gitSCM = (GitSCM) scm;
        List<UserRemoteConfig> userRemoteConfigs = gitSCM.getUserRemoteConfigs();
        assertEquals(1, userRemoteConfigs.size());
        assertEquals(httpCloneLink, userRemoteConfigs.get(0).getUrl());
    }

    @Test
    public void testBuildSsh() {
        BitbucketSCMSource scmSource =
                createInstance("credentialsId", "sshCredentialsId", "serverId", "project", "repo");
        SCMHead scmHead = mock(SCMHead.class);
        when(scmHead.getName()).thenReturn("myBranch");
        SCM scm = scmSource.build(scmHead, null);
        assertTrue(scm instanceof GitSCM);
        GitSCM gitSCM = (GitSCM) scm;
        List<UserRemoteConfig> userRemoteConfigs = gitSCM.getUserRemoteConfigs();
        assertEquals(1, userRemoteConfigs.size());
        assertEquals(sshCloneLink, userRemoteConfigs.get(0).getUrl());
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

    // TODO: Replace with trigger test
//    @Test
//    public void testAfterSaveRegistersWebhookIfNotAlreadyRegistered() {
//        String credentialsId = "valid-credentials";
//        String serverId = "server-id";
//        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId, serverId));
//        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
//        bitbucketSCMsource.setOwner(owner);
//        doReturn(true).when(bitbucketSCMsource).isValid();
//        BitbucketWebhookMultibranchTrigger.DescriptorImpl triggerDesc =
//                mock(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class);
//        doReturn(singletonList(triggerDesc)).when(bitbucketSCMsource).getTriggers(any());
//
//        bitbucketSCMsource.afterSave();
//
////        verify(triggerDesc).addTrigger(any(), same(bitbucketSCMsource));
//    }
//
//    @Test void testAfterSavePushTrigger() {
//
//    }
//
//    @Test void testAfterSavePRTrigger() {
//
//    }
//
//    @Test void testAfterSaveNoTrigger() {
//
//    }
    // TODO: Move test to BitbucketSCMSource test
//    public void testAddWebhook() {
//        String serverId = "myServerId";
//        MultiBranchProject project = new WorkflowMultiBranchProject(jenkins.jenkins, "name");
//        String baseUrl = "http://example.com";
//        BitbucketSCMSource scmSource = mock(BitbucketSCMSource.class);
//        GlobalCredentialsProvider credentialsProvider = mock(GlobalCredentialsProvider.class);
//        BitbucketSCMRepository bbsRepo = mock(BitbucketSCMRepository.class);
//
//        when(scmSource.getBitbucketSCMRepository()).thenReturn(bbsRepo);
//        when(pluginConfig.getServerById(serverId)).thenReturn(of(bbsConfig));
//        when(bbsRepo.getServerId()).thenReturn(serverId);
//        when(bbsConfig.getBaseUrl()).thenReturn(baseUrl);
//        when(bbsConfig.getGlobalCredentialsProvider(project)).thenReturn(credentialsProvider);
//
//        boolean result = scmSource.afterSave();
//
//        assertThat(result, is(true));
//        verify(webhookHandler).register(baseUrl, credentialsProvider, bbsRepo, containsPushTrigger, containsPRTrigger);
//    }

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
        return createInstance(credentialsId, "", serverId, projectName, repo);
    }

    private BitbucketSCMSource createInstance(String credentialsId, String sshCredentialId, @Nullable String serverId,
                                              @Nullable String projectName, @Nullable String repo) {
        return new BitbucketSCMSource(
                "1",
                credentialsId,
                sshCredentialId,
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
                        nullable(String.class)))
                        .thenReturn(scmHelper);
                when(descriptor.getRetryingWebhookHandler()).thenReturn(mock(RetryingWebhookHandler.class));
                when(scmHelper.getRepository(nullable(String.class), nullable(String.class))).thenReturn(repository);
                when(repository.getProject()).thenReturn(mock(BitbucketProject.class));
                when(repository.getCloneUrls()).thenReturn(Arrays.asList(new BitbucketNamedLink("http", httpCloneLink), new BitbucketNamedLink("ssh", sshCloneLink)));

                return descriptor;
            }
        };
    }
}
