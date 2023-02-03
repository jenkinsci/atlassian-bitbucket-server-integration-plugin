package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRepositoryMetadataAction;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchTrigger;
import com.atlassian.bitbucket.jenkins.internal.trigger.RetryingWebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.PullRequestClosedWebhookEvent;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.PullRequestOpenedWebhookEvent;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.RefsChangedWebhookEvent;

import com.cloudbees.plugins.credentials.Credentials;
import hudson.model.Action;
import hudson.model.Actionable;
import jenkins.branch.MultiBranchProject;
import jenkins.scm.api.*;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.*;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BitbucketSCMSourceTest {

    private static final String BASE_URL = "http://example.com";
    private static final String CREDENTIAL_ID = "valid-credentials";
    private static final BitbucketDefaultBranch DEFAULT_BRANCH = new BitbucketDefaultBranch("ref/head/master",
            "master",
            BitbucketRefType.BRANCH,
            "1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0",
            "1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0",
            true);
    private static final String HTTP_CLONE_LINK = "http://localhost:7990/fake.git";
    private static final String HTTP_MIRROR_CLONE_LINK = "http://localhost:8990/mirror/fake.git";
    private static final String MIRROR_NAME = "mirror-name";
    private static final String PROJECT_NAME = "PROJECT_1";
    private static final String REPOSITORY_NAME = "rep_1";
    private static final String SERVER_ID = "server-id";
    private static final String SSH_CLONE_LINK = "ssh://git@localhost:7990/fake.git";
    private static final String SSH_CREDENTIAL_ID = "valid-ssh-credentials";
    private static final String SSH_MIRROR_CLONE_LINK = "ssh://git@localhost:8990/mirror/fake.git";

    @Test
    public void testAfterSaveDoesNothingIfIsInvalid() {
        BitbucketSCMSource bitbucketSCMsource = spy(new SCMSourceBuilder(CREDENTIAL_ID).build());
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);
        BitbucketWebhookMultibranchTrigger.DescriptorImpl triggerDesc =
                mock(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class);

        bitbucketSCMsource.afterSave();

        verifyZeroInteractions(triggerDesc);
    }

    @Test
    public void testAfterSaveDoesNothingIfWebhookAlreadyRegistered() {
        BitbucketSCMSource bitbucketSCMsource = spy(new SCMSourceBuilder(CREDENTIAL_ID).build());
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);
        bitbucketSCMsource.setWebhookRegistered(true);
        doReturn(true).when(bitbucketSCMsource).isValid();
        BitbucketWebhookMultibranchTrigger.DescriptorImpl triggerDesc =
                mock(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class);

        bitbucketSCMsource.afterSave();

        verifyZeroInteractions(triggerDesc);
    }

    @Test
    public void testAfterSaveRegistersWebhookIfNotAlreadyRegisteredWithNoTrigger() {
        BitbucketSCMSource bitbucketSCMSource = spy(new SCMSourceBuilder(CREDENTIAL_ID)
                .serverId(SERVER_ID)
                .build());
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMSource.setOwner(owner);
        doReturn(emptyMap()).when(owner).getTriggers();
        BitbucketSCMSource.DescriptorImpl descriptor = setupDescriptor(bitbucketSCMSource, SERVER_ID, BASE_URL, owner);
        doReturn(true).when(bitbucketSCMSource).isValid();
        doNothing().when(bitbucketSCMSource).initializeGitScmSource();

        bitbucketSCMSource.afterSave();

        verify(descriptor.getRetryingWebhookHandler()).register(eq(BASE_URL), any(), any(), eq(owner), eq(false), eq(false));
    }

    @Test
    public void testAfterSaveWithPRTrigger() {
        BitbucketSCMSource bitbucketSCMSource = spy(new SCMSourceBuilder(CREDENTIAL_ID)
                .serverId(SERVER_ID)
                .build());
        BitbucketWebhookMultibranchTrigger.DescriptorImpl triggerDesc =
                mock(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class);
        BitbucketWebhookMultibranchTrigger trigger =
                mock(BitbucketWebhookMultibranchTrigger.class);
        doReturn(false).when(trigger).isRefTrigger();
        doReturn(true).when(trigger).isPullRequestTrigger();
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMSource.setOwner(owner);
        doReturn(singletonMap(triggerDesc, trigger)).when(owner).getTriggers();
        BitbucketSCMSource.DescriptorImpl descriptor = setupDescriptor(bitbucketSCMSource, SERVER_ID, BASE_URL, owner);
        doReturn(true).when(bitbucketSCMSource).isValid();
        doNothing().when(bitbucketSCMSource).initializeGitScmSource();

        bitbucketSCMSource.afterSave();

        verify(descriptor.getRetryingWebhookHandler()).register(eq(BASE_URL), any(), any(), eq(owner), eq(true), eq(false));
    }

    @Test
    public void testAfterSaveWithPushTrigger() {
        BitbucketSCMSource bitbucketSCMSource = spy(new SCMSourceBuilder(CREDENTIAL_ID)
                .serverId(SERVER_ID)
                .build());
        BitbucketWebhookMultibranchTrigger.DescriptorImpl triggerDesc =
                mock(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class);
        BitbucketWebhookMultibranchTrigger trigger =
                mock(BitbucketWebhookMultibranchTrigger.class);
        doReturn(true).when(trigger).isRefTrigger();
        doReturn(false).when(trigger).isPullRequestTrigger();
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMSource.setOwner(owner);
        doReturn(singletonMap(triggerDesc, trigger)).when(owner).getTriggers();
        BitbucketSCMSource.DescriptorImpl descriptor = setupDescriptor(bitbucketSCMSource, SERVER_ID, BASE_URL, owner);
        doReturn(true).when(bitbucketSCMSource).isValid();
        doNothing().when(bitbucketSCMSource).initializeGitScmSource();

        bitbucketSCMSource.afterSave();

        verify(descriptor.getRetryingWebhookHandler()).register(eq(BASE_URL), any(), any(), eq(owner), eq(false), eq(true));
    }

    @Test
    public void testCredentialAndServerIdSaved() {
        BitbucketSCMSource bitbucketSCMsource = new SCMSourceBuilder(CREDENTIAL_ID)
                .serverId(SERVER_ID)
                .build();

        assertThat(bitbucketSCMsource.getCredentialsId(), is(equalTo(CREDENTIAL_ID)));
        assertThat(bitbucketSCMsource.getServerId(), is(equalTo(SERVER_ID)));
    }

    @Test
    public void testCredentialServerProjectSaved() {
        BitbucketSCMSource bitbucketSCMsource = new SCMSourceBuilder(CREDENTIAL_ID)
                .serverId(SERVER_ID)
                .projectName(PROJECT_NAME)
                .build();

        assertThat(bitbucketSCMsource.getCredentialsId(), is(equalTo(CREDENTIAL_ID)));
        assertThat(bitbucketSCMsource.getServerId(), is(equalTo(SERVER_ID)));
        assertThat(bitbucketSCMsource.getProjectName(), is(equalTo(PROJECT_NAME)));
    }

    @Test
    public void testCredentialsIdAreSavedIfServerIdNotSelected() {
        BitbucketSCMSource bitbucketSCMsource = new SCMSourceBuilder(CREDENTIAL_ID).build();

        assertThat(bitbucketSCMsource.getCredentialsId(), is(equalTo(CREDENTIAL_ID)));
    }

    @Test
    public void testInitializeSCMSourceForMirrorHTTP() {
        BitbucketSCMSource source = new SCMSourceBuilder("valid-credentials")
                .serverId(SERVER_ID)
                .projectName(PROJECT_NAME)
                .repositoryName(REPOSITORY_NAME)
                .mirrorName(MIRROR_NAME)
                .build();

        CustomGitSCMSource gitSource = source.getFullyInitializedGitSCMSource();
        assertThat(gitSource.getRemote(), equalTo(HTTP_MIRROR_CLONE_LINK));
    }

    @Test
    public void testInitializeSCMSourceForMirrorSSH() {
        BitbucketSCMSource source = new SCMSourceBuilder(CREDENTIAL_ID)
                .sshCredentialId(SSH_CREDENTIAL_ID)
                .serverId(SERVER_ID)
                .projectName(PROJECT_NAME)
                .repositoryName(REPOSITORY_NAME)
                .mirrorName(MIRROR_NAME)
                .build();

        CustomGitSCMSource gitSource = source.getFullyInitializedGitSCMSource();
        assertThat(gitSource.getRemote(), equalTo(SSH_MIRROR_CLONE_LINK));
    }

    @Test
    public void testInitializeSCMSourceHTTP() {
        BitbucketSCMSource source = new SCMSourceBuilder(CREDENTIAL_ID)
                .serverId(SERVER_ID)
                .projectName(PROJECT_NAME)
                .repositoryName(REPOSITORY_NAME)
                .build();

        CustomGitSCMSource gitSource = source.getFullyInitializedGitSCMSource();
        assertThat(gitSource.getRemote(), equalTo(HTTP_CLONE_LINK));
    }
    
    @Test
    public void testGetAndInitializeGitSCMSourceSetsOwnerIfNull() {
        BitbucketSCMSource source = new SCMSourceBuilder(CREDENTIAL_ID)
                .serverId(SERVER_ID)
                .projectName(PROJECT_NAME)
                .repositoryName(REPOSITORY_NAME)
                .build();
        CustomGitSCMSource gitSource = source.getFullyInitializedGitSCMSource(); // Initializes with no owner
        assertThat(gitSource.getOwner(), equalTo(null));

        SCMSourceOwner owner = mock(SCMSourceOwner.class);
        source.setOwner(owner); // This is handled by Jenkins during typical execution
        
        gitSource = source.getFullyInitializedGitSCMSource();
        assertThat(gitSource.getOwner(), equalTo(owner));
    }

    @Test
    public void testInitializeSCMSourceSSH() {
        BitbucketSCMSource source = new SCMSourceBuilder(CREDENTIAL_ID)
                .sshCredentialId(SSH_CREDENTIAL_ID)
                .serverId(SERVER_ID)
                .projectName(PROJECT_NAME)
                .repositoryName(REPOSITORY_NAME)
                .build();

        CustomGitSCMSource gitSource = source.getFullyInitializedGitSCMSource();
        assertThat(gitSource.getRemote(), equalTo(SSH_CLONE_LINK));
    }

    @Test
    public void testRetrieveActionsHeadEvent() throws IOException, InterruptedException {
        BitbucketDefaultBranch branch = new BitbucketDefaultBranch("ref/head/master",
                "master",
                BitbucketRefType.BRANCH,
                "1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0",
                "1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0",
                true);

        BitbucketSCMSource bitbucketSCMsource = new SCMSourceBuilder(CREDENTIAL_ID)
                .serverId(SERVER_ID)
                .projectName(PROJECT_NAME)
                .build();
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);

        SCMHead head = mock(SCMHead.class);
        SCMHeadEvent<RefsChangedWebhookEvent> mockEvent = mock(SCMHeadEvent.class);
        List<Action> result = bitbucketSCMsource.retrieveActions(head, mockEvent, null);

        assertThat(result.isEmpty(), equalTo(true));

        BitbucketRepositoryMetadataAction mockAction =
                new BitbucketRepositoryMetadataAction(bitbucketSCMsource.getBitbucketSCMRepository(), branch);
        when(((Actionable) bitbucketSCMsource.getOwner()).getActions(BitbucketRepositoryMetadataAction.class)).thenReturn(Collections.singletonList(mockAction));
        when(head.getName()).thenReturn("master");

        result = bitbucketSCMsource.retrieveActions(head, mockEvent, null);
        assertThat(result.size(), equalTo(1));

        Action action = result.get(0);
        assertThat(action.getClass(), equalTo(PrimaryInstanceMetadataAction.class));
    }

    @Test
    public void testRetrieveActionsSourceEvent() throws IOException, InterruptedException {
        BitbucketSCMSource bitbucketSCMsource = new SCMSourceBuilder(CREDENTIAL_ID)
                .serverId(SERVER_ID)
                .projectName(PROJECT_NAME)
                .build();
        bitbucketSCMsource.setOwner(mock(MultiBranchProject.class));

        SCMSourceEvent<RefsChangedWebhookEvent> mockEvent = mock(SCMSourceEvent.class);
        List<Action> result = bitbucketSCMsource.retrieveActions(mockEvent, null);
        assertThat(result.size(), equalTo(1));

        Action action = result.get(0);
        assertThat(action.getClass(), equalTo(BitbucketRepositoryMetadataAction.class));

        BitbucketRepositoryMetadataAction metaAction = (BitbucketRepositoryMetadataAction) action;
        assertThat(metaAction.getBitbucketSCMRepository(), equalTo(bitbucketSCMsource.getBitbucketSCMRepository()));
        assertThat(metaAction.getBitbucketDefaultBranch(), equalTo(DEFAULT_BRANCH));
    }

    @Test
    public void testRetrieveApplicableEvent() {
        BitbucketSCMSource bitbucketSCMsource = spy(new SCMSourceBuilder(CREDENTIAL_ID).build());
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);

        SCMHeadEvent<PullRequestOpenedWebhookEvent> mockEvent = mock(SCMHeadEvent.class);
        PullRequestOpenedWebhookEvent payload = mock(PullRequestOpenedWebhookEvent.class);
        when(mockEvent.getPayload()).thenReturn(payload);
        when(owner.getTriggers()).thenReturn(singletonMap(new BitbucketWebhookMultibranchTrigger.DescriptorImpl(),
                new BitbucketWebhookMultibranchTrigger(true, false)));

        assertThat(bitbucketSCMsource.isEventApplicable(mockEvent), equalTo(true));
    }

    @Test
    public void testRetrieveNotApplicableEvent() {
        BitbucketSCMSource bitbucketSCMsource = spy(new SCMSourceBuilder(CREDENTIAL_ID).build());
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);

        SCMHeadEvent<PullRequestClosedWebhookEvent> mockEvent = mock(SCMHeadEvent.class);
        PullRequestClosedWebhookEvent payload = mock(PullRequestClosedWebhookEvent.class);
        when(mockEvent.getPayload()).thenReturn(payload);
        when(owner.getTriggers()).thenReturn(singletonMap(new BitbucketWebhookMultibranchTrigger.DescriptorImpl(),
                new BitbucketWebhookMultibranchTrigger(true, false)));

        assertThat(bitbucketSCMsource.isEventApplicable(mockEvent), equalTo(false));
    }

    @Test
    public void testRetrieveNullBbSPayload() {
        BitbucketSCMSource bitbucketSCMsource = spy(new SCMSourceBuilder(CREDENTIAL_ID).build());
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);

        SCMHeadEvent<String> mockEvent = mock(SCMHeadEvent.class);

        when(mockEvent.getPayload()).thenReturn("This is not a Bitbucket Server event");
        when(owner.getTriggers()).thenReturn(singletonMap(new BitbucketWebhookMultibranchTrigger.DescriptorImpl(),
                new BitbucketWebhookMultibranchTrigger(true, false)));

        assertThat(bitbucketSCMsource.isEventApplicable(mockEvent), equalTo(false));
    }

    @Test
    public void testRetrieveNullEvent() throws IOException, InterruptedException {
        BitbucketSCMSource bitbucketSCMsource = spy(new SCMSourceBuilder(CREDENTIAL_ID).build());
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);

        assertThat(bitbucketSCMsource.isEventApplicable(null), equalTo(false));
    }

    private BitbucketSCMSource.DescriptorImpl setupDescriptor(BitbucketSCMSource bitbucketSCMSource,
                                                              String serverId, String baseUrl,
                                                              MultiBranchProject<?, ?> owner) {
        BitbucketSCMSource.DescriptorImpl descriptor =
                (BitbucketSCMSource.DescriptorImpl) bitbucketSCMSource.getDescriptor();
        BitbucketServerConfiguration bbsConfig = mock(BitbucketServerConfiguration.class);
        GlobalCredentialsProvider credentialsProvider = mock(GlobalCredentialsProvider.class);

        when(descriptor.getConfiguration(serverId)).thenReturn(Optional.of(bbsConfig));
        when(bbsConfig.getBaseUrl()).thenReturn(baseUrl);
        when(bbsConfig.getGlobalCredentialsProvider(owner)).thenReturn(credentialsProvider);

        return descriptor;
    }

    private static class SCMSourceBuilder {

        private final String credentialId;
        private String mirrorName;
        private String projectName;
        private String repositoryName;
        private String serverId;
        private String sshCredentialId;

        public SCMSourceBuilder(String credentialId) {
            this.credentialId = requireNonNull(credentialId, "credentialId");
        }

        public BitbucketSCMSource build() {
            return new BitbucketSCMSource(
                    "1",
                    credentialId,
                    sshCredentialId,
                    emptyList(),
                    projectName,
                    repositoryName,
                    serverId,
                    mirrorName) {

                BitbucketSCMSource.DescriptorImpl descriptor = null;

                @Override
                public Optional<Credentials> getCredentials() {
                    return Optional.of(mock(Credentials.class));
                }

                @Override
                public SCMSourceDescriptor getDescriptor() {
                    //if descriptor doesn't exist, create a new one, otherwise return existing descriptor
                    if (descriptor == null) {
                        descriptor = mock(DescriptorImpl.class);
                        BitbucketScmHelper scmHelper = mock(BitbucketScmHelper.class);
                        BitbucketServerConfiguration bitbucketServerConfiguration =
                                mock(BitbucketServerConfiguration.class);
                        BitbucketRepository repository = mock(BitbucketRepository.class);
                        BitbucketNamedLink httpLink = new BitbucketNamedLink("http", HTTP_CLONE_LINK);
                        BitbucketNamedLink sshLink = new BitbucketNamedLink("ssh", SSH_CLONE_LINK);

                        doReturn(mock(GlobalCredentialsProvider.class))
                                .when(bitbucketServerConfiguration).getGlobalCredentialsProvider(any(String.class));
                        when(descriptor.getConfiguration(argThat(serverId -> !isBlank(serverId))))
                                .thenReturn(Optional.of(bitbucketServerConfiguration));
                        when(descriptor.getConfiguration(argThat(StringUtils::isBlank)))
                                .thenReturn(Optional.empty());
                        when(descriptor.getBitbucketScmHelper(
                                nullable(String.class),
                                nullable(Credentials.class)))
                                .thenReturn(scmHelper);
                        when(descriptor.getRetryingWebhookHandler()).thenReturn(mock(RetryingWebhookHandler.class));
                        when(scmHelper.getRepository(nullable(String.class), nullable(String.class))).thenReturn(repository);
                        when(scmHelper.getDefaultBranch(nullable(String.class), nullable(String.class)))
                                .thenReturn(Optional.of(DEFAULT_BRANCH));
                        when(repository.getProject()).thenReturn(mock(BitbucketProject.class));
                        when(repository.getCloneUrls()).thenReturn(Arrays.asList(httpLink, sshLink));
                        when(repository.getSelfLink()).thenReturn("");
                        doReturn(mock(GlobalCredentialsProvider.class))
                                .when(bitbucketServerConfiguration).getGlobalCredentialsProvider(any(String.class));
                        doReturn(Optional.of(httpLink)).when(repository).getCloneUrl(CloneProtocol.HTTP);
                        doReturn(Optional.of(sshLink)).when(repository).getCloneUrl(CloneProtocol.SSH);

                        BitbucketMirrorHandler mirrorHandler = mock(BitbucketMirrorHandler.class);
                        BitbucketMirroredRepository mirroredRepository = mock(BitbucketMirroredRepository.class);
                        EnrichedBitbucketMirroredRepository enrichedRepository =
                                mock(EnrichedBitbucketMirroredRepository.class);
                        BitbucketNamedLink httpMirrorLink = new BitbucketNamedLink("http", HTTP_MIRROR_CLONE_LINK);
                        BitbucketNamedLink sshMirrorLink = new BitbucketNamedLink("ssh", SSH_MIRROR_CLONE_LINK);

                        doReturn(mirrorHandler).when(descriptor).createMirrorHandler(scmHelper);
                        doReturn(enrichedRepository).when(mirrorHandler).fetchRepository(any(MirrorFetchRequest.class));
                        doReturn(repository).when(enrichedRepository).getRepository();
                        doReturn(mirroredRepository).when(enrichedRepository).getMirroringDetails();
                        doReturn(Optional.of(httpMirrorLink)).when(mirroredRepository).getCloneUrl(CloneProtocol.HTTP);
                        doReturn(Optional.of(sshMirrorLink)).when(mirroredRepository).getCloneUrl(CloneProtocol.SSH);
                        doReturn(mirrorName).when(mirroredRepository).getMirrorName();
                    }
                    return descriptor;
                }
            };
        }

        public SCMSourceBuilder mirrorName(String mirrorName) {
            this.mirrorName = requireNonNull(mirrorName, "mirrorName");
            return this;
        }

        public SCMSourceBuilder projectName(String projectName) {
            this.projectName = requireNonNull(projectName, "projectName");
            return this;
        }

        public SCMSourceBuilder repositoryName(String repositoryName) {
            this.repositoryName = requireNonNull(repositoryName, "repositoryName");
            return this;
        }

        public SCMSourceBuilder serverId(String serverId) {
            this.serverId = requireNonNull(serverId, "serverId");
            return this;
        }

        public SCMSourceBuilder sshCredentialId(String sshCredentialId) {
            this.sshCredentialId = requireNonNull(sshCredentialId, "sshCredentialId");
            return this;
        }
    }
}
