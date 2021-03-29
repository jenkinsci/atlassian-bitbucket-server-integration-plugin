package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchTrigger;
import com.atlassian.bitbucket.jenkins.internal.trigger.RetryingWebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.PullRequestClosedWebhookEvent;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.PullRequestOpenedWebhookEvent;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.PullRequestStoreImpl;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import hudson.util.LogTaskListener;
import jenkins.branch.MultiBranchProject;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSourceDescriptor;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
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

    @Test
    public void testAfterSaveRegistersWebhookIfNotAlreadyRegisteredWithNoTrigger() {
        String credentialsId = "valid-credentials";
        String serverId = "server-id";
        String baseUrl = "http://example.com";
        BitbucketSCMSource bitbucketSCMSource = spy(createInstance(credentialsId, serverId));
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMSource.setOwner(owner);
        doReturn(Collections.emptyMap()).when(owner).getTriggers();
        BitbucketSCMSource.DescriptorImpl descriptor = setupDescriptor(bitbucketSCMSource, serverId, baseUrl, owner);
        doReturn(true).when(bitbucketSCMSource).isValid();

        bitbucketSCMSource.afterSave();

        verify(descriptor.getRetryingWebhookHandler()).register(eq(baseUrl), any(), any(), eq(false), eq(false));
    }

    @Test
    public void testAfterSaveWithPushTrigger() {
        String credentialsId = "valid-credentials";
        String serverId = "server-id";
        String baseUrl = "http://example.com";
        BitbucketSCMSource bitbucketSCMSource = spy(createInstance(credentialsId, serverId));
        BitbucketWebhookMultibranchTrigger.DescriptorImpl triggerDesc =
                mock(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class);
        BitbucketWebhookMultibranchTrigger trigger =
                mock(BitbucketWebhookMultibranchTrigger.class);
        doReturn(true).when(trigger).isRefTrigger();
        doReturn(false).when(trigger).isPullRequestTrigger();
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMSource.setOwner(owner);
        doReturn(Collections.singletonMap(triggerDesc, trigger)).when(owner).getTriggers();
        BitbucketSCMSource.DescriptorImpl descriptor = setupDescriptor(bitbucketSCMSource, serverId, baseUrl, owner);
        doReturn(true).when(bitbucketSCMSource).isValid();

        bitbucketSCMSource.afterSave();

        verify(descriptor.getRetryingWebhookHandler()).register(eq(baseUrl), any(), any(), eq(false), eq(true));
    }

    @Test
    public void testAfterSaveWithPRTrigger() {
        String credentialsId = "valid-credentials";
        String serverId = "server-id";
        String baseUrl = "http://example.com";
        BitbucketSCMSource bitbucketSCMSource = spy(createInstance(credentialsId, serverId));
        BitbucketWebhookMultibranchTrigger.DescriptorImpl triggerDesc =
                mock(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class);
        BitbucketWebhookMultibranchTrigger trigger =
                mock(BitbucketWebhookMultibranchTrigger.class);
        doReturn(false).when(trigger).isRefTrigger();
        doReturn(true).when(trigger).isPullRequestTrigger();
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMSource.setOwner(owner);
        doReturn(Collections.singletonMap(triggerDesc, trigger)).when(owner).getTriggers();
        BitbucketSCMSource.DescriptorImpl descriptor = setupDescriptor(bitbucketSCMSource, serverId, baseUrl, owner);
        doReturn(true).when(bitbucketSCMSource).isValid();

        bitbucketSCMSource.afterSave();

        verify(descriptor.getRetryingWebhookHandler()).register(eq(baseUrl), any(), any(), eq(true), eq(false));
    }

    @Test
    public void testHandleRefreshingPRStore() {
        String credentialsId = "valid-credentials";
        String serverId = "server-id";
        String baseUrl = "http://example.com";
        String project = "project";
        String repo = "repo";
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        BitbucketSCMSource bitbucketSCMSource = spy(createInstance(credentialsId, "", serverId, project, repo));
        Logger LOGGER = Logger.getLogger(BitbucketSCMSource.class.getName());
        TaskListener listener = new LogTaskListener(LOGGER, Level.FINE);
        CustomGitSCMSource gitSCMSource = mock(CustomGitSCMSource.class);
        SelectBranchTrait trait = new SelectBranchTrait();

        doReturn(project).when(bitbucketSCMSource).getProjectKey();
        doReturn(repo).when(bitbucketSCMSource).getRepositorySlug();
        doReturn(gitSCMSource).when(bitbucketSCMSource).getGitSCMSource();
        doReturn(Collections.singletonList(trait)).when(gitSCMSource).getTraits();
        BitbucketSCMSource.DescriptorImpl descriptor = setupDescriptor(bitbucketSCMSource, serverId, baseUrl, owner);

        bitbucketSCMSource.handleRefreshingPRStore(null, listener);

        verify(descriptor.getPullRequestStore()).refreshStore(eq(project), eq(repo), eq(serverId), any(Stream.class));
    }

    @Test
    public void testRetrieveApplicableEvent() throws IOException, InterruptedException {
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId));
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);

        CustomGitSCMSource mockScmSource = mock(CustomGitSCMSource.class);
        SCMHeadEvent<PullRequestOpenedWebhookEvent> mockEvent = mock(SCMHeadEvent.class);
        SCMHeadObserver mockHeadObserver = mock(SCMHeadObserver.class);
        PullRequestOpenedWebhookEvent payload = mock(PullRequestOpenedWebhookEvent.class);
        TaskListener mockTaskListener = mock(TaskListener.class);
        when(bitbucketSCMsource.getGitSCMSource()).thenReturn(mockScmSource);
        when(mockEvent.getPayload()).thenReturn(payload);
        when(owner.getTriggers()).thenReturn(singletonMap(new BitbucketWebhookMultibranchTrigger.DescriptorImpl(),
                new BitbucketWebhookMultibranchTrigger(true, false)));

        bitbucketSCMsource.retrieve(null, mockHeadObserver, mockEvent, mockTaskListener);

        verify(mockScmSource).accessibleRetrieve(isNull(), eq(mockHeadObserver), eq(mockEvent), eq(mockTaskListener));
    }

    @Test
    public void testRetrieveNotApplicableEvent() throws IOException, InterruptedException {
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId));
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);

        CustomGitSCMSource mockScmSource = mock(CustomGitSCMSource.class);
        SCMHeadEvent<PullRequestClosedWebhookEvent> mockEvent = mock(SCMHeadEvent.class);
        SCMHeadObserver mockHeadObserver = mock(SCMHeadObserver.class);
        PullRequestClosedWebhookEvent payload = mock(PullRequestClosedWebhookEvent.class);
        TaskListener mockTaskListener = mock(TaskListener.class);
        when(bitbucketSCMsource.getGitSCMSource()).thenReturn(mockScmSource);
        when(mockEvent.getPayload()).thenReturn(payload);
        when(owner.getTriggers()).thenReturn(singletonMap(new BitbucketWebhookMultibranchTrigger.DescriptorImpl(),
                new BitbucketWebhookMultibranchTrigger(true, false)));

        bitbucketSCMsource.retrieve(null, mockHeadObserver, mockEvent, mockTaskListener);

        verify(mockScmSource, never()).accessibleRetrieve(isNull(), eq(mockHeadObserver), eq(mockEvent), eq(mockTaskListener));
    }

    @Test
    public void testRetrieveNullBbSPayload() throws IOException, InterruptedException {
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId));
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);

        CustomGitSCMSource mockScmSource = mock(CustomGitSCMSource.class);
        SCMHeadEvent<String> mockEvent = mock(SCMHeadEvent.class);
        SCMHeadObserver mockHeadObserver = mock(SCMHeadObserver.class);
        TaskListener mockTaskListener = mock(TaskListener.class);

        when(bitbucketSCMsource.getGitSCMSource()).thenReturn(mockScmSource);
        when(mockEvent.getPayload()).thenReturn("This is not a Bitbucket Server event");
        when(owner.getTriggers()).thenReturn(singletonMap(new BitbucketWebhookMultibranchTrigger.DescriptorImpl(),
                new BitbucketWebhookMultibranchTrigger(true, false)));

        bitbucketSCMsource.retrieve(null, mockHeadObserver, mockEvent, mockTaskListener);

        verify(mockScmSource).accessibleRetrieve(isNull(), eq(mockHeadObserver), eq(mockEvent), eq(mockTaskListener));
    }

    @Test
    public void testRetrieveNullEvent() throws IOException, InterruptedException {
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId));
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);

        CustomGitSCMSource mockScmSource = mock(CustomGitSCMSource.class);
        SCMHeadObserver mockHeadObserver = mock(SCMHeadObserver.class);
        TaskListener mockTaskListener = mock(TaskListener.class);
        when(bitbucketSCMsource.getGitSCMSource()).thenReturn(mockScmSource);
        when(owner.getTriggers()).thenReturn(singletonMap(new BitbucketWebhookMultibranchTrigger.DescriptorImpl(),
                new BitbucketWebhookMultibranchTrigger(true, false)));

        bitbucketSCMsource.retrieve(null, mockHeadObserver, null, mockTaskListener);

        verify(mockScmSource).accessibleRetrieve(isNull(), eq(mockHeadObserver), isNull(), eq(mockTaskListener));
    }

    private BitbucketSCMSource.DescriptorImpl setupDescriptor(BitbucketSCMSource bitbucketSCMSource,
                                                              String serverId, String baseUrl, MultiBranchProject<?, ?> owner) {
        BitbucketSCMSource.DescriptorImpl descriptor = (BitbucketSCMSource.DescriptorImpl) bitbucketSCMSource.getDescriptor();
        BitbucketServerConfiguration bbsConfig = mock(BitbucketServerConfiguration.class);
        GlobalCredentialsProvider credentialsProvider = mock(GlobalCredentialsProvider.class);

        when(descriptor.getConfiguration(serverId)).thenReturn(Optional.of(bbsConfig));
        when(bbsConfig.getBaseUrl()).thenReturn(baseUrl);
        when(bbsConfig.getGlobalCredentialsProvider(owner)).thenReturn(credentialsProvider);

        return descriptor;
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

            BitbucketSCMSource.DescriptorImpl descriptor = null;

            @Override
            public SCMSourceDescriptor getDescriptor() {
                //if descriptor doesn't exist, create a new one, otherwise return existing descriptor
                if (descriptor == null) {
                    descriptor = mock(DescriptorImpl.class);
                    BitbucketScmHelper scmHelper = mock(BitbucketScmHelper.class);
                    BitbucketServerConfiguration bitbucketServerConfiguration = mock(BitbucketServerConfiguration.class);
                    BitbucketRepository repository = mock(BitbucketRepository.class);

                    doReturn(mock(GlobalCredentialsProvider.class))
                            .when(bitbucketServerConfiguration).getGlobalCredentialsProvider(any(String.class));
                    when(descriptor.getConfiguration(argThat(serverId -> !isBlank(serverId))))
                            .thenReturn(Optional.of(bitbucketServerConfiguration));
                    when(descriptor.getConfiguration(argThat(StringUtils::isBlank)))
                            .thenReturn(Optional.empty());
                    when(descriptor.getBitbucketScmHelper(
                            nullable(String.class),
                            nullable(BitbucketTokenCredentials.class)))
                            .thenReturn(scmHelper);
                    when(descriptor.getPullRequestStore()).thenReturn(mock(PullRequestStoreImpl.class));
                    when(descriptor.getRetryingWebhookHandler()).thenReturn(mock(RetryingWebhookHandler.class));
                    when(scmHelper.getRepository(nullable(String.class), nullable(String.class))).thenReturn(repository);
                    when(repository.getProject()).thenReturn(mock(BitbucketProject.class));
                    when(repository.getCloneUrls()).thenReturn(Arrays.asList(new BitbucketNamedLink("http", httpCloneLink), new BitbucketNamedLink("ssh", sshCloneLink)));
                    doReturn(mock(GlobalCredentialsProvider.class))
                            .when(bitbucketServerConfiguration).getGlobalCredentialsProvider(any(String.class));
                }
                return descriptor;
            }
        };
    }
}
