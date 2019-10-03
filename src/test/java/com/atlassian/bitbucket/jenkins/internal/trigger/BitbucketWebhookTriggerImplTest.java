package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketUser;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerImpl.BitbucketWebhookTriggerDescriptor;
import hudson.model.*;
import hudson.scm.SCM;
import hudson.util.SequentialExecutionQueue;
import jenkins.model.Jenkins;
import jenkins.triggers.SCMTriggerItem;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.PROJECT;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.REPO;
import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketWebhookTriggerImplTest {

    @Mock
    private SequentialExecutionQueue queue;
    @Mock
    private RetryingWebhookHandler webhookHandler;
    @Mock
    private JenkinsProvider jenkinsProvider;
    @Mock
    private Jenkins jenkins;

    private BitbucketWebhookTriggerDescriptor descriptor;

    @Before
    public void setup() {
        when(jenkinsProvider.get()).thenReturn(jenkins);
        this.descriptor =
                new BitbucketWebhookTriggerDescriptor(queue, webhookHandler, jenkinsProvider);
    }

    @Test
    public void testDescriptorIsApplicableForNonSCMTriggerItem() {
        assertFalse(descriptor.isApplicable(mock(Item.class)));
    }

    @Test
    public void testDescriptorIsApplicableForSCMTriggerItem() {
        assertTrue(descriptor.isApplicable(mock(Item.class, withSettings().extraInterfaces(SCMTriggerItem.class))));
    }

    @Test
    public void testDescriptorIsApplicableForSCMedItem() {
        assertTrue(descriptor.isApplicable(mock(Item.class, withSettings().extraInterfaces(SCMedItem.class))));
    }

    @Test
    public void testDescriptorSchedule() {
        Job job = mock(Job.class);
        SCMTriggerItem triggerItem = mock(SCMTriggerItem.class);
        BitbucketUser user = new BitbucketUser("me", "me@test.atlassian", "Me");
        BitbucketWebhookTriggerRequest request = BitbucketWebhookTriggerRequest.builder()
                .actor(user)
                .build();
        CauseAction causeAction = new CauseAction(new BitbucketWebhookTriggerCause(request));
        BitbucketTriggerWorker expectedValue = new BitbucketTriggerWorker(job, triggerItem,
                causeAction, request.getAdditionalActions());

        descriptor.schedule(job, triggerItem, request);
        verify(queue).execute(argThat((ArgumentMatcher<BitbucketTriggerWorker>) argument -> deepEqual(expectedValue, argument)));
    }

    @Test
    public void testDoNotSkipRegistrationForNewInstances() {
        BitbucketWebhookTriggerImpl t = new BitbucketWebhookTriggerImpl();
        FreeStyleProject proj = createFreeStyleProject();
        assertThat(t.skipWebhookRegistration(proj, true), is(false));
    }

    @Test
    public void testSkipRegistrationForOldInstanceAndNonWorkFlowJob() {
        BitbucketWebhookTriggerImpl t = new BitbucketWebhookTriggerImpl();
        FreeStyleProject proj = createFreeStyleProject();
        assertThat(t.skipWebhookRegistration(proj, false), is(true));
    }

    @Test
    public void testTrigger() {
        BitbucketWebhookTriggerDescriptor mockDescriptor =
                mock(BitbucketWebhookTriggerDescriptor.class);
        BitbucketWebhookTriggerImpl trigger = createInstance(mockDescriptor);
        FreeStyleProject project = createFreeStyleProject();
        BitbucketUser user = new BitbucketUser("me", "me@test.atlassian", "Me");
        BitbucketWebhookTriggerRequest request = BitbucketWebhookTriggerRequest.builder()
                .actor(user)
                .build();

        trigger.start(project, true);
        trigger.trigger(request);
        verify(mockDescriptor).schedule(eq(project), eq(project), eq(request));
    }

    @Test
    public void testWebhookRegisterOnStartForNewInstance() {
        BitbucketSCMRepository repo1 = createSCMRepo();
        BitbucketSCMRepository repo2 = createSCMRepo();
        FreeStyleProject project = createProjectWithSCM(createSCM(repo1, repo2));

        BitbucketWebhookTriggerImpl trigger = createInstance();

        trigger.start(project, true);
        verify(webhookHandler).register(repo1);
        verify(webhookHandler).register(repo2);
    }

    @Test
    public void testWebhookRegisterForExistingJobs() {
        BitbucketSCMRepository repo = createSCMRepo();
        BitbucketSCM scm = createSCM(repo);
        FreeStyleProject project = createProjectWithSCM(scm);
        mockExistingProjectWithSCMs(project, scm);

        BitbucketWebhookTriggerImpl trigger = createInstance(descriptor, false);

        trigger.start(project, true);

        verify(webhookHandler, never()).register(repo);
    }

    @Test
    public void testWebhookRegistrationForSameProjectRepoDifferentServerId() {
        BitbucketSCMRepository actualRepo = createSCMRepoWithServerId("serverID2");
        FreeStyleProject project = createProjectWithSCM(createSCM(actualRepo));
        mockExistingProjectWithSCMs(project, createSCM(createSCMRepoWithServerId("serverID1")));

        BitbucketWebhookTriggerImpl trigger = createInstance(descriptor, false);

        trigger.start(project, true);

        verify(webhookHandler).register(actualRepo);
    }

    @Test
    public void testWebhookRegistrationForDifferentMirrorConfiguration() {
        BitbucketSCMRepository actualRepo = createSCMRepoWithMirror("mirror1");
        FreeStyleProject project = createProjectWithSCM(createSCM(actualRepo));
        mockExistingProjectWithSCMs(project, createSCM(createSCMRepo()));

        BitbucketWebhookTriggerImpl trigger = createInstance(descriptor, false);

        trigger.start(project, true);

        verify(webhookHandler).register(actualRepo);
    }

    @Test
    public void testWorkflowJobAreWebhookEligible() {
        BitbucketWebhookTriggerImpl t = new BitbucketWebhookTriggerImpl();
        WorkflowJob wj = new WorkflowJob(null, "workflow");
        assertThat(t.skipWebhookRegistration(wj, false), is(false));
    }

    @Test
    public void testReregisterWebhook() {
        BitbucketSCMRepository repo = createSCMRepo();
        BitbucketSCM scm = createSCM(repo);
        FreeStyleProject project = createProjectWithSCM(scm);
        mockExistingProjectWithSCMs(project, false, scm);

        BitbucketWebhookTriggerImpl trigger = createInstance(descriptor, false);

        trigger.start(project, true);
        scm.setWebhookRegistered(false);
        trigger.start(project, true);

        verify(webhookHandler, times(2)).register(repo);
    }

    private FreeStyleProject createFreeStyleProject() {
        FreeStyleProject project = mock(FreeStyleProject.class);
        Hudson itemGroup = mock(Hudson.class);
        when(itemGroup.getFullName()).thenReturn("Item name");
        when(project.getParent()).thenReturn(itemGroup);
        when(project.getName()).thenReturn("Project name");
        return project;
    }

    private BitbucketWebhookTriggerImpl createInstance() {
        return createInstance(descriptor, false);
    }

    private BitbucketWebhookTriggerImpl createInstance(BitbucketWebhookTriggerDescriptor descriptor) {
        return createInstance(descriptor, false);
    }

    private BitbucketWebhookTriggerImpl createInstance(BitbucketWebhookTriggerDescriptor descriptor,
                                                       boolean skipRegistration) {
        return new BitbucketWebhookTriggerImpl() {

            /**
             * Jenkins is not available while running Unit test.
             * @return descriptor
             */
            @Override
            public BitbucketWebhookTriggerDescriptor getDescriptor() {
                return descriptor;
            }

            /**
             * {@link org.jenkinsci.plugins.workflow.job.WorkflowJob} is final and can't be
             * mocked. This is implemented to aid unit testing.
             * @param project the project
             * @param newInstance if its a newinstance
             * @return true or false based on if registration should be skipped.
             */
            @Override
            boolean skipWebhookRegistration(Job<?, ?> project, boolean newInstance) {
                return skipRegistration;
            }
        };
    }

    private FreeStyleProject createProjectWithSCM(BitbucketSCM... scms) {
        FreeStyleProject p = createFreeStyleProject();
        doReturn(scms(scms)).when(p).getSCMs();
        return p;
    }

    private BitbucketSCMRepository createSCMRepoWithServerId(String serverId) {
        return createSCMRepo(serverId, "");
    }

    private BitbucketSCMRepository createSCMRepoWithMirror(String mirrorName) {
        return createSCMRepo("serverID", mirrorName);
    }

    private BitbucketSCMRepository createSCMRepo() {
        return createSCMRepo("serverId", "");
    }

    private BitbucketSCM createSCM(BitbucketSCMRepository... scmRepositories) {
        BitbucketSCM scm = mock(BitbucketSCM.class);
        when(scm.getRepositories()).thenReturn(asList(scmRepositories));
        return scm;
    }

    private BitbucketSCMRepository createSCMRepo(String serverId, String mirrorName) {
        return new BitbucketSCMRepository(
                "credentialId",
                PROJECT,
                PROJECT,
                REPO,
                REPO,
                serverId,
                mirrorName);
    }

    /**
     * Since the BitbucketTriggerWorker has a specific requirement around equals that does not consider all the things in the
     * class this method is a substitute to compare everything we need for testing purposes.
     */
    private boolean deepEqual(BitbucketTriggerWorker expected, BitbucketTriggerWorker actual) {

        List<Action> expectedActions = expected.getActions();
        List<Action> actualActions = actual.getActions();
        if (expectedActions.size() != actualActions.size()) {
            return false;
        }
        //Cause does not have an equals so we need to manually compare the fields.
        for (int i = 0; i < expectedActions.size(); i++) {
            Action action = expectedActions.get(i);
            Action thatAction = actualActions.get(i);
            if (action.getClass() != thatAction.getClass()) {
                return false;
            }
            boolean equals = Objects.equals(action.getDisplayName(), thatAction.getDisplayName()) &&
                             Objects.equals(action.getIconFileName(), thatAction.getIconFileName()) &&
                             Objects.equals(action.getUrlName(), thatAction.getUrlName());
            if (!equals) {
                return false;
            }
            //Cause actions have more fields and do not necessarily differ in the fields tested above
            if (action instanceof CauseAction && thatAction instanceof CauseAction) {
                if (!((CauseAction) action).getCauses().equals(((CauseAction) thatAction).getCauses())) {
                    return false;
                }
            }
        }
        return Objects.equals(expected.getJob(), actual.getJob()) &&
               Objects.equals(expected.getTriggerItem(), actual.getTriggerItem());
    }

    private void mockExistingProjectWithSCMs(FreeStyleProject newProject, BitbucketSCM... scms) {
        this.mockExistingProjectWithSCMs(newProject, true, scms);
    }

    private void mockExistingProjectWithSCMs(FreeStyleProject newProject, boolean triggerPreviouslyAdded,
                                             BitbucketSCM... scms) {
        FreeStyleProject existingProject = createProjectWithSCM(scms);
        Arrays.asList(scms).stream().forEach(scm -> when(scm.isWebhookRegistered()).thenReturn(triggerPreviouslyAdded));
        BitbucketWebhookTriggerImpl t = new BitbucketWebhookTriggerImpl();
        when(existingProject.getTriggers()).thenReturn(Collections.singletonMap(descriptor, t));
        when(jenkins.getAllItems(any(Class.class))).thenReturn(asList(existingProject, newProject));
    }

    private Collection<? extends SCM> scms(BitbucketSCM... scms) {
        return asList(scms);
    }
}