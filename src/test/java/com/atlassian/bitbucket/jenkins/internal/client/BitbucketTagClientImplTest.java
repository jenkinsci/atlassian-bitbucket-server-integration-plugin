package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketTag;
import hudson.model.TaskListener;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;
import static okhttp3.HttpUrl.parse;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketTagClientImplTest {

    private static final String PROJECT_KEY = "PROJECT_1";
    private static final String REPO_SLUG = "rep_1";
    private static final String TAGS_URL = "%s/rest/api/1.0/projects/%s/repos/%s/tags?limit=1000&orderBy=modification";
    private final FakeRemoteHttpServer fakeRemoteHttpServer = new FakeRemoteHttpServer();
    private final HttpRequestExecutor requestExecutor = new HttpRequestExecutorImpl(fakeRemoteHttpServer);
    private final BitbucketRequestExecutor bitbucketRequestExecutor = new BitbucketRequestExecutor(BITBUCKET_BASE_URL,
            requestExecutor, OBJECT_MAPPER, ANONYMOUS_CREDENTIALS);
    @Mock
    private BitbucketCapabilitiesClient capabilitiesClient;
    private BitbucketRepositoryClientImpl client;
    @Mock
    private TaskListener taskListener;

    @Before
    public void setup() {
        client = new BitbucketRepositoryClientImpl(bitbucketRequestExecutor,
                capabilitiesClient, PROJECT_KEY, REPO_SLUG);
    }

    @Test
    public void testGetRemoteTags() {
        String response = readFileToString("/tags.json");
        String url = format(TAGS_URL, BITBUCKET_BASE_URL, PROJECT_KEY, REPO_SLUG);
        fakeRemoteHttpServer.mapUrlToResult(url, response);

        BitbucketTagClient tagClient = client.getBitbucketTagClient(taskListener);
        List<BitbucketTag> tagList = tagClient.getRemoteTags().collect(Collectors.toList());

        assertEquals(1, tagList.size());
        assertEquals("release/tag_1", tagList.get(0).getDisplayId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLastPageDoesNotHaveNext() {
        BitbucketBranchClientImpl.NextPageFetcherImpl fetcher =
                new BitbucketBranchClientImpl.NextPageFetcherImpl(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor,
                        1, taskListener);

        BitbucketPage<BitbucketDefaultBranch> page = new BitbucketPage<>();
        page.setLastPage(true);

        fetcher.next(page);
    }

    @Test
    public void testNextPageFetching() {
        BitbucketTagClientImpl.NextPageFetcherImpl fetcher =
                new BitbucketTagClientImpl.NextPageFetcherImpl(parse(BITBUCKET_BASE_URL), bitbucketRequestExecutor,
                        5, taskListener);
        int nextPageStart = 2;
        fakeRemoteHttpServer.mapUrlToResult(
                BITBUCKET_BASE_URL + "?start=" + nextPageStart,
                readFileToString("/tags-last-page.json"));
        BitbucketPage<BitbucketTag> firstPage = new BitbucketPage<>();
        firstPage.setNextPageStart(nextPageStart);

        BitbucketPage<BitbucketTag> next = fetcher.next(firstPage);
        List<BitbucketTag> values = next.getValues();
        assertEquals(next.getSize(), values.size());
        assertTrue(next.getSize() > 0);

        MatcherAssert.assertThat(values.stream().map(BitbucketTag::getId).collect(toSet()),
                hasItems("refs/tags/tag_2", "refs/tags/tag_1"));
        MatcherAssert.assertThat(next.isLastPage(), is(true));
    }
}
