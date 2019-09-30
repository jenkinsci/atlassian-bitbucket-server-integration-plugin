package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketMirrorHandler;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketRepoFetcher;
import com.atlassian.bitbucket.jenkins.internal.scm.EnrichedBitbucketMirroredRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.MirrorFetchRequest;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.util.ListBoxModel.Option;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentialsImpl.getBearerCredentials;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MirrorsIT {

    private static final String SERVER_ID = "serverId";
    private static final String CREDENTIAL_ID = "jobCredentials";
    private static final String PROJECT = "help";
    private static final String REPO_SLUG = "help";

    private PersonalToken adminToken;
    private BitbucketCredentials adminCredentials;

    @Before
    public void setup() {
        adminToken = createPersonalToken(BitbucketUtils.REPO_ADMIN_PERMISSION);
        adminCredentials = getBearerCredentials(adminToken.getSecret());
    }

    @Test
    public void testMirrorsShouldShowInList() {
        BitbucketMirrorHandler instance = createInstance();
        StandardListBoxModel options =
                instance.fetchAsListBox(new MirrorFetchRequest(SERVER_ID, CREDENTIAL_ID, PROJECT, REPO_SLUG, ""));

        assertThat(options, is(iterableWithSize(2)));
        assertThat(options.stream().map(Option::toString).collect(toList()), hasItems("Primary Server=[selected]", "Mirror=Mirror"));
    }

    @Test
    public void testSelectionShouldBeMarkedInList() {
        BitbucketMirrorHandler instance = createInstance();
        StandardListBoxModel options =
                instance.fetchAsListBox(new MirrorFetchRequest(SERVER_ID, CREDENTIAL_ID, PROJECT, REPO_SLUG, "Mirror"));

        assertThat(options, is(iterableWithSize(2)));
        assertThat(options.stream().map(Option::toString).collect(toList()), hasItems("Primary Server=", "Mirror=Mirror[selected]"));
    }

    @Test
    public void testMirroredRepositoryFetchedCorrectly() {
        BitbucketMirrorHandler instance = createInstance();
        EnrichedBitbucketMirroredRepository mirroredRepository =
                instance.fetchRepository(new MirrorFetchRequest(SERVER_ID, CREDENTIAL_ID, PROJECT, REPO_SLUG, "Mirror"));

        assertThat(mirroredRepository.getRepository().getName(), is(equalTo(REPO_SLUG)));
    }

    @Test
    public void testUnAvailableRepositoryOnlyHavePrimaryServerSelected() {
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
        when(bitbucketServerConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);

        BitbucketClientFactoryProvider clientFactoryProvider =
                new BitbucketClientFactoryProvider(new HttpRequestExecutorImpl());
        BitbucketRepoFetcher fetcher =
                (client, project, repository) -> BitbucketSearchHelper.getRepositoryByNameOrSlug(project, repository, client);
        JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials = mock(JenkinsToBitbucketCredentials.class);
        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(CREDENTIAL_ID, bitbucketServerConfiguration)).thenReturn(adminCredentials);

        return new BitbucketMirrorHandler(pluginConfiguration, clientFactoryProvider, jenkinsToBitbucketCredentials, fetcher);
    }
}
