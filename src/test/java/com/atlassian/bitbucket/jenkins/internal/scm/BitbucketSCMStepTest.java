package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.TestSCMStep;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketSCMStepTest {
    
    private static final String SERVER_NAME = "serverName";
    private static final String SERVER_ID = "random-uuid-1fjiosf9r";
    
    @Mock
    private BitbucketSCMStep.DescriptorImpl descriptor;
    @Mock
    private BitbucketServerConfiguration serverConfiguration;
    
    @Before
    public void setup() {
        doReturn(SERVER_ID).when(serverConfiguration).getId();
    }
    
    @Test
    public void testCreateSCMServerId() {
        TestSCMStep scmStep = new TestSCMStep.Builder("id", "project", "repository")
                .descriptor(descriptor)
                .serverId(SERVER_ID)
                .build();
        BitbucketSCMRepository result = scmStep.createSCM().getBitbucketSCMRepository();
        
        assertThat(result.getServerId(), equalTo(SERVER_ID));
    }

    @Test
    public void testCreateSCMServerName() {
        TestSCMStep scmStep = new TestSCMStep.Builder("id", "project", "repository")
                .descriptor(descriptor)
                .serverName(SERVER_NAME)
                .build();
        doReturn(Collections.singletonList(serverConfiguration)).when(descriptor).getConfigurationByName(SERVER_NAME);
        BitbucketSCMRepository result = scmStep.createSCM().getBitbucketSCMRepository();

        assertThat(result.getServerId(), equalTo(SERVER_ID));
    }

    @Test
    public void testCreateSCMServerNoMatchingName() {
        TestSCMStep scmStep = new TestSCMStep.Builder("id", "project", "repository")
                .descriptor(descriptor)
                .serverName(SERVER_NAME)
                .build();
        doReturn(Collections.emptyList()).when(descriptor).getConfigurationByName(SERVER_NAME);

        Exception thrown = assertThrows(BitbucketSCMException.class, scmStep::createSCM);
        assertThat(thrown.getMessage(), equalTo("Error creating Bitbucket SCM: No server configuration matches provided name"));
    }
    
    @Test
    public void testCreateSCMServerMultipleMatchingNames() {
        TestSCMStep scmStep = new TestSCMStep.Builder("id", "project", "repository")
                .descriptor(descriptor)
                .serverName(SERVER_NAME)
                .build();
        doReturn(Arrays.asList(serverConfiguration, mock(BitbucketServerConfiguration.class)))
                .when(descriptor).getConfigurationByName(SERVER_NAME);

        Exception thrown = assertThrows(BitbucketSCMException.class, scmStep::createSCM);
        assertThat(thrown.getMessage(), equalTo("Error creating Bitbucket SCM: Multiple server configurations match " +
                                                "provided service name. Use serverId to disambiguate"));
    }
    
    @Test
    public void testCreateSCMServerNoServerIndentifierProvided() {
        TestSCMStep scmStep = new TestSCMStep.Builder("id", "project", "repository")
                .descriptor(descriptor)
                .build();

        Exception thrown = assertThrows(BitbucketSCMException.class, scmStep::createSCM);
        assertThat(thrown.getMessage(), equalTo("Error creating Bitbucket SCM: No server name or ID provided"));
    }
    
    @Test
    public void testCreateSCMServerConflictingIdAndNameProvided() {
        // In the event we receive a serverName AND a serverId, we ignore the server name as the id is more specific
        // There's a potential for the name to not match an id (GIGO). We don't need special handling for it
        TestSCMStep scmStep = new TestSCMStep.Builder("id", "project", "repository")
                .descriptor(descriptor)
                .serverId(SERVER_ID)
                .serverName("UnrelatedName")
                .build();
        BitbucketSCMRepository result = scmStep.createSCM().getBitbucketSCMRepository();

        assertThat(result.getServerId(), equalTo(SERVER_ID));
        verifyZeroInteractions(descriptor);
    }
}