package it.com.atlassian.bitbucket.jenkins.internal.config;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.fingerprints.ItemCredentialsFingerprintFacet;
import com.gargoylesoftware.htmlunit.html.*;
import hudson.model.Fingerprint;
import hudson.model.FreeStyleProject;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.BITBUCKET_BASE_URL;
import static it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule.SERVER_NAME;
import static it.com.atlassian.bitbucket.jenkins.internal.util.AsyncTestUtils.waitFor;
import static it.com.atlassian.bitbucket.jenkins.internal.util.HtmlUnitUtils.*;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

public class BitbucketPluginConfigurationIT {

    @Rule
    public BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();
    List<BitbucketServerConfiguration> initialServerList;
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private HtmlForm form;

    @After
    public void cleanup() {
        //Revert Bitbucket plugin configuration
        bitbucketPluginConfiguration.setServerList(initialServerList);
        bitbucketPluginConfiguration.save();
    }

    @Before
    public void setup() throws IOException, SAXException {
        bitbucketPluginConfiguration = bbJenkinsRule.getBitbucketPluginConfiguration();
        form = bbJenkinsRule.visit("configure").getFormByName("config");
        initialServerList = bitbucketPluginConfiguration.getServerList();
    }

    @Test
    public void testAddBitbucketServer() throws Exception {
        //Remove existing Bitbucket plugin configuration
        bitbucketPluginConfiguration.setServerList(Collections.emptyList());
        bitbucketPluginConfiguration.save();

        //Add Bitbucket plugin configuration using UI
        HtmlButton addBitbucketButton = HtmlFormUtil.getButtonByCaption(form, "Add a Bitbucket Server instance");
        addBitbucketButton.click();

        HtmlAnchor addServerAnchor = getLinkByText(form, "Instance details");
        waitTillItemIsRendered(() -> form.getInputsByName("_.serverName"));

        //Set required fields in the config form
        HtmlInput serverNameInput = form.getInputByName("_.serverName");
        String serverName = "New Bitbucket";
        serverNameInput.setValueAttribute(serverName);

        HtmlInput baseUrlInput = form.getInputByName("_.baseUrl");
        String serverUrl = "http://bitbucket.example.com";
        baseUrlInput.setValueAttribute(serverUrl);

        HtmlSelect adminCredential = form.getSelectByName("_.adminCredentialsId");
        waitTillItemIsRendered(adminCredential::getOptions);
        adminCredential.getOption(1).click();

        HtmlSelect credential = form.getSelectByName("_.credentialsId");
        waitTillItemIsRendered(credential::getOptions);
        credential.getOption(1).click();

        bbJenkinsRule.submit(form);

        //verify Bitbucket configuration has been saved
        waitFor(() -> {
            bitbucketPluginConfiguration.load();
            List<BitbucketServerConfiguration> serverList = bitbucketPluginConfiguration.getServerList();
            int serverCount = serverList.size();
            if (serverCount != 1) {
                return Optional.of("Expected 1 server in list, found: " + serverCount);
            }
            return Optional.empty();
        }, 5000);
        BitbucketServerConfiguration configuration = bitbucketPluginConfiguration.getServerList().get(0);
        assertEquals(serverName, configuration.getServerName());
        assertEquals(serverUrl, configuration.getBaseUrl());
        assertEquals(bbJenkinsRule.getBitbucketServerConfiguration().getCredentialsId(), configuration.getCredentialsId());
        assertEquals(bbJenkinsRule.getBitbucketServerConfiguration().getAdminCredentialsId(), configuration.getAdminCredentialsId());
    }

    @Test
    public void testBitbucketConnection() throws IOException {
        HtmlButton testConnectionButton = HtmlFormUtil.getButtonByCaption(form, "Test connection");

        testConnectionButton.click();

        bbJenkinsRule.waitForBackgroundJavaScript();
        assertNotNull(getDivByText(form, "Jenkins can connect with Bitbucket Server."));
    }

    @Test
    public void testBitbucketServerFieldsShouldBePopulatedWithProperValues() throws IOException {
        HtmlInput serverNameInput = form.getInputByName("_.serverName");
        assertEquals(SERVER_NAME, serverNameInput.getValueAttribute());

        HtmlInput baseUrlInput = form.getInputByName("_.baseUrl");
        assertEquals(BITBUCKET_BASE_URL, baseUrlInput.getValueAttribute());

        HtmlSelect adminCredential = form.getSelectByName("_.adminCredentialsId");
        waitTillItemIsRendered(adminCredential::getOptions);

        FreeStyleProject item = bbJenkinsRule.createFreeStyleProject("test");
        GlobalCredentialsProvider globalCredentialsProvider =
                bbJenkinsRule.getBitbucketServerConfiguration().getGlobalCredentialsProvider(item);
        Optional<BitbucketTokenCredentials> globalAdminCredentials =
                globalCredentialsProvider.getGlobalAdminCredentials();
        assertTrue(globalAdminCredentials.isPresent());

        assertTrue(CredentialsMatchers.withId(adminCredential.getSelectedOptions().get(0).getValueAttribute()).matches(globalAdminCredentials.get()));
        adminCredential.getOption(1).click();
    }

    @Test
    public void testCredentialsAreTracked() throws Exception {
        String itemName = "testTracking";
        FreeStyleProject item = bbJenkinsRule.createFreeStyleProject(itemName);
        GlobalCredentialsProvider globalCredentialsProvider =
                bbJenkinsRule.getBitbucketServerConfiguration().getGlobalCredentialsProvider(item);

        assertCredentialsAreTracked(globalCredentialsProvider.getGlobalAdminCredentials().get(), itemName);
        assertCredentialsAreTracked(globalCredentialsProvider.getGlobalCredentials().get(), itemName);
    }

    @Test
    public void testRequiredFields() throws IOException {
        HtmlSelect adminCredential = form.getSelectByName("_.adminCredentialsId");
        waitTillItemIsRendered(adminCredential::getOptions);

        adminCredential.getOption(0).click();

        bbJenkinsRule.waitForBackgroundJavaScript();
        assertNotNull(getDivByText(form, "Choose a personal access token"));
    }

    private void assertCredentialsAreTracked(Credentials credentials, String expectedItemName) throws IOException {
        Fingerprint fingerprint =
                CredentialsProvider.getFingerprintOf(credentials);

        assertThat(fingerprint.getFacets(), iterableWithSize(1));
        ItemCredentialsFingerprintFacet facet =
                (ItemCredentialsFingerprintFacet) fingerprint.getFacets().iterator().next();
        assertThat(facet.getItemFullName(), is(equalTo(expectedItemName)));
    }
}