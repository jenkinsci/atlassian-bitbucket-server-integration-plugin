package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.scm.filesystem.BitbucketSCMFile;
import jenkins.scm.api.SCMFile;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.lang.String.format;
import static okhttp3.HttpUrl.parse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class BitbucketFilePathClientImplTest {

    private static final String FILE_PATH = "Jenkinsfile";
    private static final String PROJECT_KEY = "PROJECT_1";
    private static final String REF = "refs/heads/master";
    private static final String REPO_SLUG = "rep_1";
    private static final String WEBHOOK_URL = "%s/rest/api/1.0/projects/%s/repos/%s/browse/%s?at=%s";

    private final FakeRemoteHttpServer fakeRemoteHttpServer = new FakeRemoteHttpServer();
    private final HttpRequestExecutor requestExecutor = new HttpRequestExecutorImpl(fakeRemoteHttpServer);
    private final BitbucketRequestExecutor bitbucketRequestExecutor = new BitbucketRequestExecutor(BITBUCKET_BASE_URL,
            requestExecutor, OBJECT_MAPPER, ANONYMOUS_CREDENTIALS);
    private BitbucketFilePathClient client =
            new BitbucketFilePathClientImpl(bitbucketRequestExecutor, PROJECT_KEY, REPO_SLUG);

    @Test
    public void testFetchingFile() throws UnsupportedEncodingException {
        String response = readFileToString("/file-contents.json");
        String url = format(WEBHOOK_URL, BITBUCKET_BASE_URL, PROJECT_KEY, REPO_SLUG, FILE_PATH,
                URLEncoder.encode(REF, StandardCharsets.UTF_8.toString()));
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        String jenkinsFileContent = readFileToString("/sampleJenkinsfile").trim();

        BitbucketSCMFile rootFile = new BitbucketSCMFile(client, REF);
        BitbucketSCMFile jenkinsFile = new BitbucketSCMFile(rootFile, FILE_PATH, SCMFile.Type.REGULAR_FILE);

        String remoteContent = client.getFileContent(jenkinsFile);
        assertThat(remoteContent, equalTo(jenkinsFileContent));
    }

    @Test
    public void testFetchingFileContentPaged() throws UnsupportedEncodingException {
        String firstPageResponse = readFileToString("/file-contents-first-page.json");
        String firstPageUrl = format(WEBHOOK_URL, BITBUCKET_BASE_URL, PROJECT_KEY, REPO_SLUG, FILE_PATH,
                URLEncoder.encode(REF, StandardCharsets.UTF_8.toString()));
        fakeRemoteHttpServer.mapUrlToResult(firstPageUrl, firstPageResponse);

        String lastPageResponse = readFileToString("/file-contents-last-page.json");
        String lastPageUrl = format(WEBHOOK_URL + "&start=500", BITBUCKET_BASE_URL, PROJECT_KEY, REPO_SLUG, FILE_PATH,
                URLEncoder.encode(REF, StandardCharsets.UTF_8.toString()));
        fakeRemoteHttpServer.mapUrlToResult(lastPageUrl, lastPageResponse);

        String jenkinsFileContent = readFileToString("/sampleJenkinsfileLong").trim();

        BitbucketSCMFile rootFile = new BitbucketSCMFile(client, REF);
        BitbucketSCMFile jenkinsFile = new BitbucketSCMFile(rootFile, FILE_PATH, SCMFile.Type.REGULAR_FILE);

        String remoteContent = client.getFileContent(jenkinsFile);
        assertThat(remoteContent, equalTo(jenkinsFileContent));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLastPageDoesNotHaveNext() {
        BitbucketFilePathClientImpl.FileNextPageFetcher fetcher = new BitbucketFilePathClientImpl.FileNextPageFetcher(
                parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor);
        BitbucketPage<String> page = new BitbucketPage<>();
        page.setLastPage(true);

        fetcher.next(page);
    }
}
