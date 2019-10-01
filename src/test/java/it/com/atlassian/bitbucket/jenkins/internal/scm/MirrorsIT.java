package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketMirrorHandler;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketRepoFetcher;
import com.atlassian.bitbucket.jenkins.internal.scm.EnrichedBitbucketMirroredRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.MirrorFetchRequest;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import hudson.util.ListBoxModel.Option;
import org.apache.http.HttpHeaders;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.*;

import static com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentialsImpl.getBearerCredentials;
import static com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryStatus.AVAILABLE;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.PROJECT_KEY;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.REPO_SLUG;
import static java.lang.String.format;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MirrorsIT {

    private static final String CREDENTIAL_ID = "jobCredentials";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int REPO_ID = 1;
    private static final String SERVER_ID = "serverId";

    private BitbucketCredentials adminCredentials;
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @Before
    public void setup() throws Exception {
        wireMockRule.start();
        stubProjectAndRepository(REPO_ID);
        adminCredentials = getBearerCredentials(CREDENTIAL_ID);
    }

    @After
    public void teardown() {
        wireMockRule.shutdown();
    }

    @Test
    public void testMirroredRepositoryFetchedCorrectly() throws Exception {
        stubMirrors(REPO_ID, mirror("Mirror"));
        BitbucketMirrorHandler instance = createInstance();
        EnrichedBitbucketMirroredRepository mirroredRepository =
                instance.fetchRepository(new MirrorFetchRequest(SERVER_ID, CREDENTIAL_ID, PROJECT_KEY, REPO_SLUG, "Mirror"));

        assertThat(mirroredRepository.getRepository().getName(), is(equalTo(REPO_SLUG)));
    }

    @Test
    public void testMirrorsShouldShowInList() throws Exception {
        stubMirrors(REPO_ID, mirror("Mirror1"), mirror("Mirror2"));
        BitbucketMirrorHandler instance = createInstance();
        StandardListBoxModel options =
                instance.fetchAsListBox(new MirrorFetchRequest(SERVER_ID, CREDENTIAL_ID, PROJECT_KEY, REPO_SLUG, ""));

        assertThat(options, is(iterableWithSize(3)));
        assertThat(options.stream().map(Option::toString).collect(toList()),
                hasItems("Primary Server=[selected]", "Mirror1=Mirror1", "Mirror2=Mirror2"));
    }

    @Test
    public void testSelectionShouldBeMarkedInList() throws Exception {
        stubMirrors(REPO_ID, mirror("Mirror1"), mirror("Mirror2"));
        BitbucketMirrorHandler instance = createInstance();
        StandardListBoxModel options =
                instance.fetchAsListBox(new MirrorFetchRequest(SERVER_ID, CREDENTIAL_ID, PROJECT_KEY, REPO_SLUG, "Mirror1"));

        assertThat(options, is(iterableWithSize(3)));
        assertThat(options.stream().map(Option::toString).collect(toList()), hasItems("Primary Server=", "Mirror1=Mirror1[selected]", "Mirror2=Mirror2"));
    }

    @Test
    public void testUnAvailableRepositoryOnlyHavePrimaryServerSelected() throws Exception {
        stubGetRepositoryReturnsError("TEST", "test", 404);
        BitbucketMirrorHandler instance = createInstance();
        StandardListBoxModel options =
                instance.fetchAsListBox(new MirrorFetchRequest(SERVER_ID, CREDENTIAL_ID, "TEST", "test", ""));

        assertThat(options, is(iterableWithSize(1)));
        assertThat(options.stream().map(Option::toString).collect(toList()), hasItems("Primary Server=[selected]"));
    }

    private BitbucketMirrorHandler createInstance() {
        BitbucketPluginConfiguration pluginConfiguration = mock(BitbucketPluginConfiguration.class);
        BitbucketServerConfiguration bitbucketServerConfiguration = mock(BitbucketServerConfiguration.class);
        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.of(bitbucketServerConfiguration));
        when(bitbucketServerConfiguration.getBaseUrl()).thenReturn(wireMockRule.baseUrl());

        BitbucketClientFactoryProvider clientFactoryProvider =
                new BitbucketClientFactoryProvider(new HttpRequestExecutorImpl());
        BitbucketRepoFetcher fetcher =
                (client, project, repository) -> BitbucketSearchHelper.getRepositoryByNameOrSlug(project, repository, client);
        JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials = mock(JenkinsToBitbucketCredentials.class);
        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(CREDENTIAL_ID, bitbucketServerConfiguration)).thenReturn(adminCredentials);

        return new BitbucketMirrorHandler(pluginConfiguration, clientFactoryProvider, jenkinsToBitbucketCredentials, fetcher);
    }

    private BitbucketMirroredRepository createMirrorReppsitory(int repoId, String mirrorName) {
        return new BitbucketMirroredRepository(true, new HashMap<>(), mirrorName, repoId, AVAILABLE);
    }

    private BitbucketMirror mirror(String mirrorName, boolean enabled) {
        return new BitbucketMirror(wireMockRule.baseUrl(), enabled, mirrorName);
    }

    private BitbucketMirror mirror(String mirrorName) {
        return mirror(mirrorName, true);
    }

    private void stubGetRepositoryReturnsError(String project, String repo, int httpErrorCode) throws Exception {
        BitbucketProject p = new BitbucketProject(project, Collections.emptyMap(), project);
        stubFor(get(
                urlEqualTo(format("/rest/api/1.0/projects?name=%s", project))).
                withHeader(HttpHeaders.AUTHORIZATION, WireMock.equalTo("Bearer " + CREDENTIAL_ID))
                .willReturn(aResponse().withBody(objectMapper.writeValueAsString(project))));
        stubFor(get(
                urlEqualTo(format("/rest/api/1.0/repos?projectname=%s&name=%s", project, repo))).
                withHeader(HttpHeaders.AUTHORIZATION, WireMock.equalTo("Bearer " + CREDENTIAL_ID))
                .willReturn(aResponse().withStatus(httpErrorCode)));
    }

    private void stubMirrors(int repositoryId, BitbucketMirror... mirrors) throws Exception {
        BitbucketPage<BitbucketMirroredRepositoryDescriptor> p = new BitbucketPage<>();
        List<BitbucketMirroredRepositoryDescriptor> descriptors = new ArrayList<>();

        for (int i = 0; i < mirrors.length; i++) {
            BitbucketMirror mirror = mirrors[i];
            String snippetUrl = format("mirror/latest/%s/upstreamServer/", mirror.getName());
            String mirrorUrl = format("%s/%s", wireMockRule.baseUrl(), snippetUrl);
            BitbucketNamedLink namedLink = new BitbucketNamedLink("self", mirrorUrl);
            descriptors.add(new BitbucketMirroredRepositoryDescriptor(singletonMap("self", asList(namedLink)), mirror));
            stubFor(get(urlPathMatching("/" + snippetUrl)).
                    withHeader(HttpHeaders.AUTHORIZATION, WireMock.equalTo("Bearer " + CREDENTIAL_ID))
                    .willReturn(aResponse()
                            .withBody(objectMapper.writeValueAsString(createMirrorReppsitory(repositoryId, mirror.getName())))));
        }
        p.setValues(descriptors);
        String body = objectMapper.writeValueAsString(p);
        stubFor(get(format("/rest/mirroring/1.0/repos/%d/mirrors", repositoryId)).
                withHeader(HttpHeaders.AUTHORIZATION, WireMock.equalTo("Bearer " + CREDENTIAL_ID))
                .willReturn(aResponse().withBody(body)));
    }

    private void stubProjectAndRepository(int repoId) throws Exception {
        BitbucketProject project = new BitbucketProject(PROJECT_KEY, Collections.emptyMap(), PROJECT_KEY);
        stubFor(get(
                urlEqualTo(format("/rest/api/1.0/projects?name=%s", PROJECT_KEY))).
                withHeader(HttpHeaders.AUTHORIZATION, WireMock.equalTo("Bearer " + CREDENTIAL_ID))
                .willReturn(aResponse().withBody(objectMapper.writeValueAsString(project))));
        stubRepository(repoId, project);
    }

    private void stubRepository(int repoId, BitbucketProject project) throws Exception {
        BitbucketPage<BitbucketRepository> br = new BitbucketPage<>();
        br.setValues(asList(
                new BitbucketRepository(
                        repoId,
                        REPO_SLUG,
                        project,
                        REPO_SLUG,
                        RepositoryState.AVAILABLE,
                        Collections.emptyList(),
                        "")));
        stubFor(get(
                urlEqualTo(format("/rest/api/1.0/repos?projectname=%s&name=%s", PROJECT_KEY, REPO_SLUG))).
                withHeader(HttpHeaders.AUTHORIZATION, WireMock.equalTo("Bearer " + CREDENTIAL_ID))
                .willReturn(aResponse().withBody(objectMapper.writeValueAsString(br))));
    }
}
