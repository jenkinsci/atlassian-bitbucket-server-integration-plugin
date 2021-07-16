package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BuildState;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState;
import com.atlassian.bitbucket.jenkins.internal.provider.InstanceKeyPairProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.util.TestUtils;
import okhttp3.Request;
import okio.Buffer;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.security.interfaces.RSAPrivateKey;

import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketRepositoryClientImplTest {

    private static final String PROJECT_KEY = "PROJECT_1";
    private static final String REPO_SLUG = "rep_1";
    private static final String REVISION = "bc891c29e289e373fbf8daff411480e8da6d5252";

    private final FakeRemoteHttpServer mockExecutor = new FakeRemoteHttpServer();
    @Mock
    private BitbucketSCMRepository bitbucketSCMRepo;
    @Mock
    private BitbucketCICapabilities ciCapabilities;
    private BitbucketRepositoryClientImpl repositoryClient;

    @Before
    public void setup() {
        BitbucketRequestExecutor executor = new BitbucketRequestExecutor(BITBUCKET_BASE_URL,
                new HttpRequestExecutorImpl(mockExecutor), OBJECT_MAPPER, BitbucketCredentials.ANONYMOUS_CREDENTIALS);
        repositoryClient = new BitbucketRepositoryClientImpl(executor, PROJECT_KEY, REPO_SLUG);
        when(ciCapabilities.supportsRichBuildStatus()).thenReturn(false);
        when(bitbucketSCMRepo.getProjectKey()).thenReturn(PROJECT_KEY);
        when(bitbucketSCMRepo.getRepositorySlug()).thenReturn(REPO_SLUG);
    }

    @Test
    public void testPostBuildStatus() throws IOException {
        String postURL = "http://localhost:8080/jenkins/job/Local%20BBS%20Project/15/display/redirect";
        BitbucketBuildStatus buildStatus = new BitbucketBuildStatus.Builder("15", BuildState.INPROGRESS, postURL)
                .setName("Local BBS Project")
                .setDescription("#15 in progress")
                .build();

        String url = BITBUCKET_BASE_URL + "/rest/build-status/1.0/commits/" + REVISION;
        String requestString = readFileToString("/build-status-request.json");
        mockExecutor.mapPostRequestToResult(url, requestString, "");
        Buffer b = new Buffer();

        BitbucketBuildStatusClient client = repositoryClient.getBuildStatusClient(REVISION, ciCapabilities);
        client.post(buildStatus);

        Request clientRequest = mockExecutor.getRequest(url);
        clientRequest.body().writeTo(b);
        assertEquals(StringUtils.deleteWhitespace(requestString), StringUtils.deleteWhitespace(new String(b.readByteArray())));
    }

    @Test
    public void testPostBuildStatusModernClient() throws IOException {
        String postURL = "http://localhost:8080/jenkins/job/Local%20BBS%20Project/15/display/redirect";

        InstanceKeyPairProvider keyPairProvider = mock(InstanceKeyPairProvider.class);
        when(keyPairProvider.getPrivate()).thenReturn((RSAPrivateKey) TestUtils.createTestKeyPair().getPrivate());
        DisplayURLProvider displayURLProvider = mock(DisplayURLProvider.class);
        when(displayURLProvider.getRoot()).thenReturn("http://localhost:8080/jenkins");

        when(ciCapabilities.supportsRichBuildStatus()).thenReturn(true);

        BitbucketBuildStatus buildStatus = new BitbucketBuildStatus.Builder("15", BuildState.INPROGRESS, postURL)
                .setName("Local BBS Project")
                .setDescription("#15 in progress")
                .build();

        String url = String.format("%s/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds", BITBUCKET_BASE_URL,
                PROJECT_KEY, REPO_SLUG, REVISION);
        String requestString = readFileToString("/build-status-request.json");
        mockExecutor.mapPostRequestToResult(url, requestString, "");
        Buffer b = new Buffer();

        BitbucketBuildStatusClient client =
                repositoryClient.getBuildStatusClient(REVISION, bitbucketSCMRepo, ciCapabilities, keyPairProvider, displayURLProvider);
        client.post(buildStatus);

        Request clientRequest = mockExecutor.getRequest(url);
        clientRequest.body().writeTo(b);
        assertEquals(StringUtils.deleteWhitespace(requestString), StringUtils.deleteWhitespace(new String(b.readByteArray())));
    }

    @Test
    public void testPostDeployment() throws IOException {
        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment.Builder("MY-ENV", "My-env")
                .type(BitbucketDeploymentEnvironmentType.DEVELOPMENT)
                .url("http://url.to.env")
                .build();
        BitbucketDeployment deployment = new BitbucketDeployment(42, "my-description", "my-display-name", environment,
                "my-key", DeploymentState.FAILED, "http://url.to.job");

        String url = String.format("%s/rest/api/1.0/projects/%s/repos/%s/commits/%s/deployments", BITBUCKET_BASE_URL,
                PROJECT_KEY, REPO_SLUG, REVISION);
        String requestString = readFileToString("/deployments/send_deployment_request.json");
        mockExecutor.mapPostRequestToResult(url, requestString, "");
        Buffer b = new Buffer();

        BitbucketDeploymentClient client = repositoryClient.getDeploymentClient(REVISION);
        client.post(deployment);

        Request clientRequest = mockExecutor.getRequest(url);
        clientRequest.body().writeTo(b);
        assertEquals(StringUtils.deleteWhitespace(requestString), StringUtils.deleteWhitespace(new String(b.readByteArray())));
    }
}