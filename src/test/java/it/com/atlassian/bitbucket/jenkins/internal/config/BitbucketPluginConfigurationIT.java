package it.com.atlassian.bitbucket.jenkins.internal.config;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.gargoylesoftware.htmlunit.html.*;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsWebClientRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.BITBUCKET_BASE_URL;
import static it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule.SERVER_NAME;
import static it.com.atlassian.bitbucket.jenkins.internal.util.HtmlUnitUtils.getDivByText;
import static it.com.atlassian.bitbucket.jenkins.internal.util.HtmlUnitUtils.getLinkByText;
import static it.com.atlassian.bitbucket.jenkins.internal.util.HtmlUnitUtils.waitTillItemIsRendered;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BitbucketPluginConfigurationIT {

    @Rule
    public BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();

    @Rule
    public RuleChain ruleChain = bbJenkinsRule.getRuleChain();

    private BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private BitbucketJenkinsWebClientRule bitbucketWebClient;
    private HtmlForm form;

    @Before
    public void setup() throws IOException, SAXException {
        bitbucketPluginConfiguration = bbJenkinsRule.getBitbucketPluginConfiguration();
        bitbucketWebClient = bbJenkinsRule.getWebClientRule();
        form = bitbucketWebClient.visit("configure").getFormByName("config");
    }

    @Test
    public void testAddBitbucketServer() throws Exception {
        //Remove existing Bitbucket plugin configuration
        List<BitbucketServerConfiguration> serverList = bitbucketPluginConfiguration.getServerList();
        bitbucketPluginConfiguration.setServerList(Collections.emptyList());
        bitbucketPluginConfiguration.save();

        //Add Bitbucket plugin configuration using UI
        HtmlButton addBitbucketButton = HtmlFormUtil.getButtonByCaption(form, "Add a Bitbucket instance");
        addBitbucketButton.click();

        HtmlAnchor addServerAnchor = getLinkByText(form, "Instance details");
        addServerAnchor.click();
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
        bitbucketPluginConfiguration.load();
        assertEquals(1, bitbucketPluginConfiguration.getServerList().size());
        BitbucketServerConfiguration configuration = bitbucketPluginConfiguration.getServerList().get(0);
        assertEquals(serverName, configuration.getServerName());
        assertEquals(serverUrl, configuration.getBaseUrl());
        assertEquals(bbJenkinsRule.getBitbucketServerConfiguration().getCredentialsId(), configuration.getCredentialsId());
        assertEquals(bbJenkinsRule.getBitbucketServerConfiguration().getAdminCredentialsId(), configuration.getAdminCredentialsId());

        //Revert Bitbucket plugin configuration
        bitbucketPluginConfiguration.setServerList(serverList);
        bitbucketPluginConfiguration.save();
    }

    @Test
    public void testBitbucketConnection() throws IOException {
        HtmlButton testConnectionButton = HtmlFormUtil.getButtonByCaption(form, "Test connection");

        testConnectionButton.click();

        bitbucketWebClient.waitForBackgroundJavaScript();
        assertNotNull(getDivByText(form, "Credentials work and it is a Bitbucket server"));
    }

    @Test
    public void testRequiredFields() throws IOException {
        HtmlSelect adminCredential = form.getSelectByName("_.adminCredentialsId");
        waitTillItemIsRendered(adminCredential::getOptions);

        adminCredential.getOption(0).click();

        bitbucketWebClient.waitForBackgroundJavaScript();
        assertNotNull(getDivByText(form, "An admin token must be selected"));
    }

    @Test
    public void testBitbucketServerFieldsShouldBePopulatedWithProperValues() throws IOException, SAXException {
        HtmlInput serverNameInput = form.getInputByName("_.serverName");
        assertEquals(SERVER_NAME, serverNameInput.getValueAttribute());

        HtmlInput baseUrlInput = form.getInputByName("_.baseUrl");
        assertEquals(BITBUCKET_BASE_URL, baseUrlInput.getValueAttribute());

        HtmlSelect adminCredential = form.getSelectByName("_.adminCredentialsId");
        waitTillItemIsRendered(adminCredential::getOptions);
        assertEquals(bbJenkinsRule.getBitbucketServerConfiguration().getAdminCredentialsId(),
                adminCredential.getSelectedOptions().get(0).getValueAttribute());
        adminCredential.getOption(1).click();
    }
}