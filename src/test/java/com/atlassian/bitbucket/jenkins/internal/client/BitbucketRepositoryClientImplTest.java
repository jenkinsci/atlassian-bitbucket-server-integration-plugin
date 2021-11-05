package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState;
import com.atlassian.bitbucket.jenkins.internal.provider.InstanceKeyPairProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import okhttp3.HttpUrl;
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
import java.net.URI;
import java.security.interfaces.RSAPrivateKey;
import java.util.List;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static okhttp3.HttpUrl.parse;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketRepositoryClientImplTest {

    private static final String DEFAULT_BRANCH_URL = "%s/rest/api/1.0/projects/%s/repos/%s/default-branch";
    private static final String PROJECT_KEY = "PROJECT_1";
    private static final String REPO_SLUG = "rep_1";
    private static final String REVISION = "bc891c29e289e373fbf8daff411480e8da6d5252";
    private static final String WEBHOOK_URL = "%s/rest/api/1.0/projects/%s/repos/%s/pull-requests?withAttributes=false&withProperties=false&state=OPEN";

    private final FakeRemoteHttpServer fakeRemoteHttpServer = new FakeRemoteHttpServer();
    private final HttpRequestExecutor requestExecutor = new HttpRequestExecutorImpl(fakeRemoteHttpServer);
    private final BitbucketRequestExecutor bitbucketRequestExecutor = new BitbucketRequestExecutor(BITBUCKET_BASE_URL,
            requestExecutor, OBJECT_MAPPER, ANONYMOUS_CREDENTIALS);
    private final BitbucketRepositoryClientImpl client = new BitbucketRepositoryClientImpl(bitbucketRequestExecutor,
            PROJECT_KEY, REPO_SLUG);

    @Mock
    private BitbucketSCMRepository bitbucketSCMRepo;
    @Mock
    private BitbucketCICapabilities ciCapabilities;

    @Before
    public void setup() {
        when(ciCapabilities.supportsRichBuildStatus()).thenReturn(false);
        when(bitbucketSCMRepo.getProjectKey()).thenReturn(PROJECT_KEY);
        when(bitbucketSCMRepo.getRepositorySlug()).thenReturn(REPO_SLUG);
    }

    @Test
    public void testFetchingOfExistingOpenPullRequests() {
        String response = readFileToString("/open-pull-requests.json");
        String url = format(WEBHOOK_URL, BITBUCKET_BASE_URL, PROJECT_KEY, REPO_SLUG);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        List<BitbucketPullRequest> pullRequests = client.getPullRequests(BitbucketPullRequestState.OPEN).collect(toList());

        assertThat(pullRequests.size(), is(equalTo(2)));
        assertThat(pullRequests.stream().map(BitbucketPullRequest::getId).collect(toSet()), hasItems(new Long(96), new Long(97)));
        assertThat(pullRequests.stream().map(BitbucketPullRequest::getState).collect(toSet()), hasItems(BitbucketPullRequestState.OPEN));
    }

    @Test
    public void testFetchingOfExistingPullRequests() {
        String response = readFileToString("/open-pull-requests.json");
        String webhookUrl = "%s/rest/api/1.0/projects/%s/repos/%s/pull-requests?withAttributes=false&withProperties=false&state=ALL";
        String url = format(webhookUrl, BITBUCKET_BASE_URL, PROJECT_KEY, REPO_SLUG);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        List<BitbucketPullRequest> pullRequests = client.getPullRequests().collect(toList());

        assertThat(pullRequests.size(), is(equalTo(2)));
        assertThat(pullRequests.stream().map(BitbucketPullRequest::getId).collect(toSet()), hasItems(new Long(96), new Long(97)));
    }

    @Test
    public void testNextPageFetching() {
        BitbucketRepositoryClientImpl.NextPageFetcherImpl fetcher = new BitbucketRepositoryClientImpl.NextPageFetcherImpl(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor);
        int nextPageStart = 2;
        fakeRemoteHttpServer.mapUrlToResult(
                BITBUCKET_BASE_URL + "?start=" + nextPageStart,
                readFileToString("/open-pull-requests-last-page.json"));
        BitbucketPage<BitbucketPullRequest> firstPage = new BitbucketPage<>();
        firstPage.setNextPageStart(nextPageStart);

        BitbucketPage<BitbucketPullRequest> next = fetcher.next(firstPage);
        List<BitbucketPullRequest> values = next.getValues();
        assertEquals(next.getSize(), values.size());
        assertTrue(next.getSize() > 0);

        assertThat(values.stream().map(BitbucketPullRequest::getId).collect(toSet()), hasItems(new Long(96), new Long(97)));
        assertThat(next.isLastPage(), is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLastPageDoesNotHaveNext() {
        BitbucketRepositoryClientImpl.NextPageFetcherImpl fetcher = new BitbucketRepositoryClientImpl.NextPageFetcherImpl(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor);
        BitbucketPage<BitbucketPullRequest> page = new BitbucketPage<>();
        page.setLastPage(true);

        fetcher.next(page);
    }
    
    @Test
    public void testFetchDefaultBranch() {
        String response = readFileToString("/default-branch.json");
        String url = format(DEFAULT_BRANCH_URL, BITBUCKET_BASE_URL, PROJECT_KEY, REPO_SLUG);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        BitbucketDefaultBranch defaultBranch = client.getDefaultBranch();

        assertNotNull(defaultBranch);
    }
  
    @Test
    public void testPostBuildStatus() throws IOException {
        String postURL = "http://localhost:8080/jenkins/job/Local%20BBS%20Project/15/display/redirect";
        BitbucketBuildStatus buildStatus = new BitbucketBuildStatus.Builder("15", BuildState.INPROGRESS, postURL)
                .setName("Local BBS Project")
                .setDescription("#15 in progress")
                .build();

        String url = HttpUrl.get(BITBUCKET_BASE_URL)
                .newBuilder()
                .addPathSegment("rest")
                .addPathSegment("build-status")
                .addPathSegment("1.0")
                .addPathSegment("commits")
                .addPathSegment(REVISION)
                .build()
                .toString();
        String requestString = readFileToString("/build-status-request.json");
        fakeRemoteHttpServer.mapPostRequestToResult(url, requestString, "");
        Buffer b = new Buffer();

        BitbucketBuildStatusClient client = this.client.getBuildStatusClient(REVISION, ciCapabilities);
        client.post(buildStatus);

        Request clientRequest = fakeRemoteHttpServer.getRequest(url);
        clientRequest.body().writeTo(b);
        assertEquals(StringUtils.deleteWhitespace(requestString), StringUtils.deleteWhitespace(b.readString(UTF_8)));
    }

    @Test
    public void testPostBuildStatusModernClient() throws IOException {
        String postURL = "http://localhost:8080/jenkins/job/Local%20BBS%20Project/15/display/redirect";

        InstanceKeyPairProvider keyPairProvider = mock(InstanceKeyPairProvider.class);
        when(keyPairProvider.getPrivate()).thenReturn((RSAPrivateKey) createTestKeyPair().getPrivate());
        DisplayURLProvider displayURLProvider = mock(DisplayURLProvider.class);
        when(displayURLProvider.getRoot()).thenReturn("http://localhost:8080/jenkins");

        when(ciCapabilities.supportsRichBuildStatus()).thenReturn(true);

        BitbucketBuildStatus buildStatus = new BitbucketBuildStatus.Builder("15", BuildState.INPROGRESS, postURL)
                .setName("Local BBS Project")
                .setDescription("#15 in progress")
                .build();

        String url = getCommitUrl()
                .addPathSegment("builds")
                .build()
                .toString();
        String requestString = readFileToString("/build-status-request.json");
        fakeRemoteHttpServer.mapPostRequestToResult(url, requestString, "");
        Buffer b = new Buffer();

        BitbucketBuildStatusClient client =
                this.client.getBuildStatusClient(REVISION, bitbucketSCMRepo, ciCapabilities, keyPairProvider, displayURLProvider);
        client.post(buildStatus);

        Request clientRequest = fakeRemoteHttpServer.getRequest(url);
        clientRequest.body().writeTo(b);
        assertEquals(StringUtils.deleteWhitespace(requestString), StringUtils.deleteWhitespace(b.readString(UTF_8)));
    }

    @Test
    public void testPostDeployment() throws IOException {
        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment("MY-ENV", "My-env",
                BitbucketDeploymentEnvironmentType.DEVELOPMENT, URI.create("http://url.to.env"));
        BitbucketDeployment deployment = new BitbucketDeployment(42, "my-description", "my-display-name", environment,
                "my-key", DeploymentState.FAILED, "http://url.to.job");

        String url = getCommitUrl()
                .addPathSegment("deployments")
                .build()
                .toString();
        String requestString = readFileToString("/deployments/send_deployment_request.json");
        fakeRemoteHttpServer.mapPostRequestToResult(url, requestString, "");
        Buffer b = new Buffer();

        BitbucketDeploymentClient client = this.client.getDeploymentClient(REVISION);
        client.post(deployment);

        Request clientRequest = fakeRemoteHttpServer.getRequest(url);
        clientRequest.body().writeTo(b);
        assertEquals(StringUtils.deleteWhitespace(requestString), StringUtils.deleteWhitespace(b.readString(UTF_8)));
    }

    private HttpUrl.Builder getCommitUrl() {
        return HttpUrl.get(BITBUCKET_BASE_URL)
                .newBuilder()
                .addPathSegment("rest")
                .addPathSegment("api")
                .addPathSegment("1.0")
                .addPathSegment("projects")
                .addPathSegment(PROJECT_KEY)
                .addPathSegment("repos")
                .addPathSegment(REPO_SLUG)
                .addPathSegment("commits")
                .addPathSegment(REVISION);
    }
}
