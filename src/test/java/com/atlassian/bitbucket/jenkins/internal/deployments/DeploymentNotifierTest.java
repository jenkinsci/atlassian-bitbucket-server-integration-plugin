package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DeploymentNotifierTest {

    private static final String ENV_KEY = "ENV_KEY";
    private static final String ENV_NAME = "ENV_NAME";
    private static final String ENV_TYPE = "PRODUCTION";
    private static final String ENV_URL = "http://my-url";
    private static final FormValidation FORM_VALIDATION_OK = FormValidation.ok();

    private static final BitbucketDeploymentEnvironment ENVIRONMENT = new BitbucketDeploymentEnvironment(ENV_KEY,
            ENV_NAME, BitbucketDeploymentEnvironmentType.valueOf(ENV_TYPE), URI.create(ENV_URL));

    @Mock
    private BitbucketDeploymentFactory bitbucketDeploymentFactory;
    @Mock
    private DeploymentPoster deploymentPoster;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private TaskListener listener;
    @InjectMocks
    private DeploymentNotifier.DescriptorImpl descriptor;
    @Mock
    private DeploymentStepDescriptorHelper descriptorHelper;

    @Test
    public void testCreateStepAllowsCustomEnvironmentKey() {
        DeploymentNotifier step = createStep();
        assertThat(step.getEnvironmentKey(), equalTo(ENV_KEY));
        assertThat(step.getEnvironmentName(), equalTo(ENV_NAME));
        assertThat(step.getEnvironmentType(), equalTo(ENV_TYPE));
        assertThat(step.getEnvironmentUrl(), equalTo(ENV_URL));
    }

    @Test
    public void testCreateStepGeneratesEnvironmentKeyWhenNotSet() {
        // We don't call createStep here because it will call setEnvironmentKey
        DeploymentNotifier step = new DeploymentNotifier(ENV_NAME) {
            @Override
            public DescriptorImpl descriptor() {
                DescriptorImpl descriptor = mock(DescriptorImpl.class);
                when(descriptor.getBitbucketDeploymentFactory()).thenReturn(bitbucketDeploymentFactory);
                when(descriptor.getDeploymentPoster()).thenReturn(deploymentPoster);
                return descriptor;
            }
        };
        UUID.fromString(step.getEnvironmentKey()); // This will throw if it's not a UUID
        assertThat(step.getEnvironmentName(), equalTo(ENV_NAME));
    }

    @Test
    public void testCreateStepGeneratesEnvironmentKeyWhenBlank() {
        DeploymentNotifier step = createStep(" ", ENV_NAME, ENV_TYPE, ENV_URL);
        UUID.fromString(step.getEnvironmentKey()); // This will throw if it's not a UUID
        assertThat(step.getEnvironmentName(), equalTo(ENV_NAME));
        assertThat(step.getEnvironmentType(), equalTo(ENV_TYPE));
        assertThat(step.getEnvironmentUrl(), equalTo(ENV_URL));
    }

    @Test
    public void testCreateStepGeneratesEnvironmentKeyWhenNull() {
        DeploymentNotifier step = createStep(null, ENV_NAME, ENV_TYPE, ENV_URL);
        UUID.fromString(step.getEnvironmentKey()); // This will throw if it's not a UUID
        assertThat(step.getEnvironmentName(), equalTo(ENV_NAME));
        assertThat(step.getEnvironmentType(), equalTo(ENV_TYPE));
        assertThat(step.getEnvironmentUrl(), equalTo(ENV_URL));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentKey() {
        Item context = mock(Item.class);
        String environmentKey = "my key";
        when(descriptorHelper.doCheckEnvironmentKey(context, environmentKey)).thenReturn(FORM_VALIDATION_OK);

        FormValidation formValidation = descriptor.doCheckEnvironmentKey(context, environmentKey);

        verify(descriptorHelper).doCheckEnvironmentKey(context, environmentKey);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentName() {
        Item context = mock(Item.class);
        String environmentName = "my env";
        when(descriptorHelper.doCheckEnvironmentName(context, environmentName)).thenReturn(FORM_VALIDATION_OK);

        FormValidation formValidation = descriptor.doCheckEnvironmentName(context, environmentName);

        verify(descriptorHelper).doCheckEnvironmentName(context, environmentName);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentType() {
        Item context = mock(Item.class);
        String environmentType = "PRODUCTION";
        when(descriptorHelper.doCheckEnvironmentType(context, environmentType)).thenReturn(FORM_VALIDATION_OK);

        FormValidation formValidation = descriptor.doCheckEnvironmentType(context, environmentType);

        verify(descriptorHelper).doCheckEnvironmentType(context, environmentType);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentUrl() {
        Item context = mock(Item.class);
        String environmentUrl = "http://my-env";
        when(descriptorHelper.doCheckEnvironmentUrl(context, environmentUrl)).thenReturn(FORM_VALIDATION_OK);

        FormValidation formValidation = descriptor.doCheckEnvironmentUrl(context, environmentUrl);

        verify(descriptorHelper).doCheckEnvironmentUrl(context, environmentUrl);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDoFillEnvironmentTypeItems() {
        Item context = mock(Item.class);
        ListBoxModel listBoxModel = mock(ListBoxModel.class);
        when(descriptorHelper.doFillEnvironmentTypeItems(context)).thenReturn(listBoxModel);

        ListBoxModel options = descriptor.doFillEnvironmentTypeItems(context);

        verify(descriptorHelper).doFillEnvironmentTypeItems(context);
        assertThat(options, equalTo(listBoxModel));
    }

    @Test
    public void testPerformCallsDeploymentPoster() throws IOException, InterruptedException {
        DeploymentNotifier step = createStep();
        BitbucketDeployment deployment = createDeployment();
        Run<?, ?> run = mock(Run.class);
        BitbucketRevisionAction revisionAction = mock(BitbucketRevisionAction.class);
        BitbucketSCMRepository repo = mock(BitbucketSCMRepository.class);
        when(revisionAction.getBitbucketSCMRepo()).thenReturn(repo);
        String commit = "myCommit";
        when(revisionAction.getRevisionSha1()).thenReturn(commit);
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(revisionAction);

        when(bitbucketDeploymentFactory.createDeployment(run, ENVIRONMENT)).thenReturn(deployment);

        step.perform(run, null, null, listener);

        verifyZeroInteractions(listener);
        verify(bitbucketDeploymentFactory).createDeployment(run, ENVIRONMENT);
        verify(deploymentPoster).postDeployment(repo, commit, deployment, run, listener);
    }

    @Test
    public void testPerformDefaultsNullEnvNameToEnvType() throws IOException, InterruptedException {
        DeploymentNotifier step = createStep(ENV_KEY, null, ENV_TYPE, ENV_URL);
        BitbucketDeploymentEnvironmentType type = BitbucketDeploymentEnvironmentType.valueOf(ENV_TYPE);
        BitbucketDeploymentEnvironment expectedEnvironment = new BitbucketDeploymentEnvironment(ENV_KEY,
                type.getDisplayName(), type, URI.create(ENV_URL));
        BitbucketDeployment deployment = createDeployment();
        Run<?, ?> run = mock(Run.class);
        BitbucketRevisionAction revisionAction = mock(BitbucketRevisionAction.class);
        BitbucketSCMRepository repo = mock(BitbucketSCMRepository.class);
        when(revisionAction.getBitbucketSCMRepo()).thenReturn(repo);
        String commit = "myCommit";
        when(revisionAction.getRevisionSha1()).thenReturn(commit);
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(revisionAction);

        when(bitbucketDeploymentFactory.createDeployment(run, expectedEnvironment)).thenReturn(deployment);

        step.perform(run, null, null, listener);

        verify(listener.getLogger()).println("Using 'Production' as the environment name since it was not correctly configured. Please configure an environment name.");
        verify(bitbucketDeploymentFactory).createDeployment(run, expectedEnvironment);
        verify(deploymentPoster).postDeployment(repo, commit, deployment, run, listener);
    }

    @Test
    public void testPerformDefaultsNullEnvNameToJobName() throws IOException, InterruptedException {
        DeploymentNotifier step = createStep(ENV_KEY, null, null, ENV_URL);
        String parentName = "Deploy to production";
        BitbucketDeploymentEnvironment expectedEnvironment = new BitbucketDeploymentEnvironment(ENV_KEY,
                parentName, null, URI.create(ENV_URL));
        BitbucketDeployment deployment = createDeployment();
        Run<?, ?> run = mock(Run.class, Answers.RETURNS_DEEP_STUBS);
        when(run.getParent().getDisplayName()).thenReturn(parentName);
        BitbucketSCMRepository repo = mock(BitbucketSCMRepository.class);
        String commit = "myCommit";
        when(run.getAction(argThat(actionClass -> actionClass.equals(BitbucketRevisionAction.class))))
                .thenReturn(new BitbucketRevisionAction(repo, "my-branch", commit));

        when(bitbucketDeploymentFactory.createDeployment(run, expectedEnvironment)).thenReturn(deployment);

        step.perform(run, null, null, listener);

        verify(listener.getLogger()).println("Using 'Deploy to production' as the environment name since it was not correctly configured. Please configure an environment name.");
        verify(bitbucketDeploymentFactory).createDeployment(run, expectedEnvironment);
        verify(deploymentPoster).postDeployment(repo, commit, deployment, run, listener);
    }

    @Test
    public void testPerformWhenInvalidEnvType() throws IOException, InterruptedException {
        DeploymentNotifier step = createStep(ENV_KEY, ENV_NAME, "NOT_A_VALID_TYPE", ENV_URL);
        BitbucketDeploymentEnvironment expectedEnvironment = new BitbucketDeploymentEnvironment(ENV_KEY,
                ENV_NAME, null, URI.create(ENV_URL));
        BitbucketDeployment deployment = createDeployment();
        Run<?, ?> run = mock(Run.class);
        BitbucketRevisionAction revisionAction = mock(BitbucketRevisionAction.class);
        BitbucketSCMRepository repo = mock(BitbucketSCMRepository.class);
        when(revisionAction.getBitbucketSCMRepo()).thenReturn(repo);
        String commit = "myCommit";
        when(revisionAction.getRevisionSha1()).thenReturn(commit);
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(revisionAction);

        when(bitbucketDeploymentFactory.createDeployment(run, expectedEnvironment)).thenReturn(deployment);

        step.perform(run, null, null, listener);

        verify(bitbucketDeploymentFactory).createDeployment(run, expectedEnvironment);
        verify(deploymentPoster).postDeployment(repo, commit, deployment, run, listener);
    }

    @Test
    public void testPerformLogsWhenInvalidEnvUrl() throws IOException, InterruptedException {
        DeploymentNotifier step = createStep(ENV_KEY, ENV_NAME, ENV_TYPE, "Not a URL!");
        BitbucketDeploymentEnvironment expectedEnvironment = new BitbucketDeploymentEnvironment(ENV_KEY,
                ENV_NAME, BitbucketDeploymentEnvironmentType.valueOf(ENV_TYPE), null);
        BitbucketDeployment deployment = createDeployment();
        Run<?, ?> run = mock(Run.class);
        BitbucketRevisionAction revisionAction = mock(BitbucketRevisionAction.class);
        BitbucketSCMRepository repo = mock(BitbucketSCMRepository.class);
        when(revisionAction.getBitbucketSCMRepo()).thenReturn(repo);
        String commit = "myCommit";
        when(revisionAction.getRevisionSha1()).thenReturn(commit);
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(revisionAction);
        when(bitbucketDeploymentFactory.createDeployment(run, expectedEnvironment)).thenReturn(deployment);

        step.perform(run, null, null, listener);

        verify(listener.getLogger()).println("Invalid environment URL 'Not a URL!'.");
        verify(bitbucketDeploymentFactory).createDeployment(run, expectedEnvironment);
        verify(deploymentPoster).postDeployment(repo, commit, deployment, run, listener);
    }

    @Test
    public void testPerformWhenExceptionDoesNotThrow() throws IOException, InterruptedException {
        DeploymentNotifier step = createStep();
        Run<?, ?> run = mock(Run.class);
        BitbucketRevisionAction revisionAction = mock(BitbucketRevisionAction.class);
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(revisionAction);

        when(bitbucketDeploymentFactory.createDeployment(run, ENVIRONMENT))
                .thenThrow(new RuntimeException("Some exception"));

        step.perform(run, null, null, listener);

        verify(listener).error("An error occurred when trying to post the deployment to Bitbucket Server: Some exception");
        verifyNoMoreInteractions(listener);
        verify(bitbucketDeploymentFactory).createDeployment(run, ENVIRONMENT);
        verifyZeroInteractions(deploymentPoster);
    }

    @Test
    public void testPerformWhenNoBitbucketRevisionAction() throws IOException, InterruptedException {
        DeploymentNotifier step = createStep();
        Run<?, ?> run = mock(Run.class);

        step.perform(run, null, null, listener);

        verify(listener).error("Could not send deployment notification: DeploymentNotifier only works when using the Bitbucket SCM for checkout.");
        verifyNoMoreInteractions(listener);
        verifyZeroInteractions(bitbucketDeploymentFactory);
        verifyZeroInteractions(deploymentPoster);
    }

    @Test
    public void testPerformWithBlankEnvironmentUrlCallsDeploymentPoster() throws IOException, InterruptedException {
        DeploymentNotifier step = createStep(ENV_KEY, ENV_NAME, ENV_TYPE, null);
        BitbucketDeploymentEnvironment expectedEnvironment = new BitbucketDeploymentEnvironment(ENV_KEY,
                ENV_NAME, BitbucketDeploymentEnvironmentType.valueOf(ENV_TYPE), null);
        BitbucketDeployment deployment = createDeployment();
        Run<?, ?> run = mock(Run.class);
        BitbucketRevisionAction revisionAction = mock(BitbucketRevisionAction.class);
        BitbucketSCMRepository repo = mock(BitbucketSCMRepository.class);
        when(revisionAction.getBitbucketSCMRepo()).thenReturn(repo);
        String commit = "myCommit";
        when(revisionAction.getRevisionSha1()).thenReturn(commit);
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(revisionAction);

        when(bitbucketDeploymentFactory.createDeployment(run, expectedEnvironment)).thenReturn(deployment);

        step.perform(run, null, null, listener);

        verifyZeroInteractions(listener);
        verify(bitbucketDeploymentFactory).createDeployment(run, expectedEnvironment);
        verify(deploymentPoster).postDeployment(repo, commit, deployment, run, listener);
    }

    private BitbucketDeployment createDeployment() {
        return createDeployment(ENVIRONMENT);
    }

    private BitbucketDeployment createDeployment(BitbucketDeploymentEnvironment environment) {
        return new BitbucketDeployment(1, "desc", "name", environment, "key", DeploymentState.FAILED, "url");
    }

    private DeploymentNotifier createStep() {
        return createStep(ENV_KEY, ENV_NAME, ENV_TYPE, ENV_URL);
    }

    private DeploymentNotifier createStep(String environmentKey, String environmentName,
                                          @CheckForNull String environmentType,
                                          @CheckForNull String environmentUrl) {
        DeploymentNotifier notifier = new DeploymentNotifier(environmentName) {
            @Override
            public DescriptorImpl descriptor() {
                DescriptorImpl descriptor = mock(DescriptorImpl.class);
                when(descriptor.getBitbucketDeploymentFactory()).thenReturn(bitbucketDeploymentFactory);
                when(descriptor.getDeploymentPoster()).thenReturn(deploymentPoster);
                return descriptor;
            }
        };
        notifier.setEnvironmentKey(environmentKey);
        notifier.setEnvironmentType(environmentType);
        notifier.setEnvironmentUrl(environmentUrl);
        return notifier;
    }
}