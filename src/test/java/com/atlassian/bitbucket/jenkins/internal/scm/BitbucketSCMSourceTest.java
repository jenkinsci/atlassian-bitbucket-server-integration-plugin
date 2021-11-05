package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRefType;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRepositoryMetadataAction;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchTrigger;
import com.atlassian.bitbucket.jenkins.internal.trigger.RetryingWebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.PullRequestClosedWebhookEvent;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.PullRequestOpenedWebhookEvent;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.RefsChangedWebhookEvent;

import hudson.model.Action;
import hudson.model.Actionable;
import jenkins.branch.MultiBranchProject;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BitbucketSCMSourceTest {

    private static final String httpCloneLink = "http://localhost:7990/fake.git";
    private static final String sshCloneLink = "ssh://git@localhost:7990/fake.git";

    @Test
    public void testAfterSaveDoesNothingIfIsInvalid() {
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId));
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);
        BitbucketWebhookMultibranchTrigger.DescriptorImpl triggerDesc =
                mock(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class);

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
        doReturn(emptyMap()).when(owner).getTriggers();
        BitbucketSCMSource.DescriptorImpl descriptor = setupDescriptor(bitbucketSCMSource, serverId, baseUrl, owner);
        doReturn(true).when(bitbucketSCMSource).isValid();

        bitbucketSCMSource.afterSave();

        verify(descriptor.getRetryingWebhookHandler()).register(eq(baseUrl), any(), any(), eq(false), eq(false));
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
        doReturn(singletonMap(triggerDesc, trigger)).when(owner).getTriggers();
        BitbucketSCMSource.DescriptorImpl descriptor = setupDescriptor(bitbucketSCMSource, serverId, baseUrl, owner);
        doReturn(true).when(bitbucketSCMSource).isValid();

        bitbucketSCMSource.afterSave();

        verify(descriptor.getRetryingWebhookHandler()).register(eq(baseUrl), any(), any(), eq(true), eq(false));
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
        doReturn(singletonMap(triggerDesc, trigger)).when(owner).getTriggers();
        BitbucketSCMSource.DescriptorImpl descriptor = setupDescriptor(bitbucketSCMSource, serverId, baseUrl, owner);
        doReturn(true).when(bitbucketSCMSource).isValid();

        bitbucketSCMSource.afterSave();

        verify(descriptor.getRetryingWebhookHandler()).register(eq(baseUrl), any(), any(), eq(false), eq(true));
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
    public void testRetrieveApplicableEvent() {
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId));
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
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId));
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
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId));
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
        String credentialsId = "valid-credentials";
        BitbucketSCMSource bitbucketSCMsource = spy(createInstance(credentialsId));
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);

        assertThat(bitbucketSCMsource.isEventApplicable(null), equalTo(false));
    }
    
    @Test
    public void testRetrieveActionsSourceEvent() throws IOException, InterruptedException {
        String credentialsId = "valid-credentials";
        String serverId = "serverId1";
        String projectName = "proj1";
        
        BitbucketDefaultBranch branch = new BitbucketDefaultBranch("ref/head/master", 
                                                                    "master", 
                                                                    BitbucketRefType.BRANCH, 
                                                                    "1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0", 
                                                                    "1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0", 
                                                                    true);
        
        BitbucketSCMSource bitbucketSCMsource = createInstance(credentialsId, serverId, projectName);
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);
                
        SCMSourceEvent<RefsChangedWebhookEvent> mockEvent = mock(SCMSourceEvent.class);        
        List<Action> result = bitbucketSCMsource.retrieveActions(mockEvent, null);
        assertThat(result.size(), equalTo(1));
        
        Action action = result.get(0);
        assertThat(action.getClass(), equalTo(BitbucketRepositoryMetadataAction.class));
        
        BitbucketRepositoryMetadataAction metaAction = (BitbucketRepositoryMetadataAction) action;
        assertThat(metaAction.getBitbucketSCMRepository(), equalTo(bitbucketSCMsource.getBitbucketSCMRepository()));
        assertThat(metaAction.getBitbucketDefaultBranch(), equalTo(branch));
    }
    
    @Test
    public void testRetrieveActionsHeadEvent() throws IOException, InterruptedException {
        String credentialsId = "valid-credentials";
        String serverId = "serverId1";
        String projectName = "proj1";
        
        BitbucketDefaultBranch branch = new BitbucketDefaultBranch("ref/head/master", 
                                                                    "master", 
                                                                    BitbucketRefType.BRANCH, 
                                                                    "1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0", 
                                                                    "1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0", 
                                                                    true);
        
        BitbucketSCMSource bitbucketSCMsource = createInstance(credentialsId, serverId, projectName);
        MultiBranchProject<?, ?> owner = mock(MultiBranchProject.class);
        bitbucketSCMsource.setOwner(owner);
            
        SCMHead head = mock(SCMHead.class); 
        SCMHeadEvent<RefsChangedWebhookEvent> mockEvent = mock(SCMHeadEvent.class);        
        List<Action> result = bitbucketSCMsource.retrieveActions(head, mockEvent, null);
        
        assertThat(result.isEmpty(), equalTo(true));
        
        BitbucketRepositoryMetadataAction mockAction = new BitbucketRepositoryMetadataAction(bitbucketSCMsource.getBitbucketSCMRepository(), branch);        
        when(((Actionable) bitbucketSCMsource.getOwner()).getActions(BitbucketRepositoryMetadataAction.class)).thenReturn(Collections.singletonList(mockAction));
        when(head.getName()).thenReturn("master");
        
        result = bitbucketSCMsource.retrieveActions(head, mockEvent, null);
        assertThat(result.size(), equalTo(1));
        
        Action action = result.get(0);
        assertThat(action.getClass(), equalTo(PrimaryInstanceMetadataAction.class));
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
                emptyList(),
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
                    BitbucketServerConfiguration bitbucketServerConfiguration =
                            mock(BitbucketServerConfiguration.class);
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
                    when(descriptor.getRetryingWebhookHandler()).thenReturn(mock(RetryingWebhookHandler.class));
                    when(scmHelper.getRepository(nullable(String.class), nullable(String.class))).thenReturn(repository);
                    when(scmHelper.getDefaultBranch(nullable(String.class), nullable(String.class)))
                            .thenReturn(Optional.of(new BitbucketDefaultBranch("ref/head/master", 
                                                                    "master", 
                                                                    BitbucketRefType.BRANCH, 
                                                                    "1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0", 
                                                                    "1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0", 
                                                                    true)));
                    when(repository.getProject()).thenReturn(mock(BitbucketProject.class));
                    when(repository.getCloneUrls()).thenReturn(Arrays.asList(new BitbucketNamedLink("http", httpCloneLink), new BitbucketNamedLink("ssh", sshCloneLink)));
                    when(repository.getSelfLink()).thenReturn("");
                    doReturn(mock(GlobalCredentialsProvider.class))
                            .when(bitbucketServerConfiguration).getGlobalCredentialsProvider(any(String.class));
                }
                return descriptor;
            }
        };
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
}
