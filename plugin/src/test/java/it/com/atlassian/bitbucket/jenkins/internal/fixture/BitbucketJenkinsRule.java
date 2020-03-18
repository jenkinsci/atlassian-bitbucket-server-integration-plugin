package it.com.atlassian.bitbucket.jenkins.internal.fixture;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentialsImpl;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.ExtensionList;
import hudson.util.SecretFactory;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;

public final class BitbucketJenkinsRule extends JenkinsRule {

    public static final String SERVER_NAME = "Bitbucket server";

    private static final Logger LOGGER = Logger.getLogger("");
    private static final AtomicReference<PersonalToken> ADMIN_PERSONAL_TOKEN = new AtomicReference<>();
    private static final AtomicReference<PersonalToken> READ_PERSONAL_TOKEN = new AtomicReference<>();
    private BitbucketServerConfiguration bitbucketServerConfiguration;
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private HtmlPage currentPage;
    private FileHandler handler;
    private WebClient webClient;

    public BitbucketJenkinsRule() {
        webClient = createWebClient();
        LOGGER.setLevel(Level.INFO);
    }

    public void addBitbucketServer(BitbucketServerConfiguration bitbucketServer) {
        ExtensionList<BitbucketPluginConfiguration> configExtensions =
                jenkins.getExtensionList(BitbucketPluginConfiguration.class);
        bitbucketPluginConfiguration = configExtensions.get(0);
        bitbucketPluginConfiguration.getServerList().add(bitbucketServer);
        bitbucketPluginConfiguration.save();
    }

    @Override
    public void after() throws Exception {
        super.after();

        // Copy over the log into the artifact directory
        try {
            Path destination = Paths.get("target", "artifacts", testDescription.getClassName(),
                    testDescription.getMethodName());
            Files.createDirectories(destination.getParent());
            FileUtils.moveDirectory(Paths.get("target", "logs", testDescription.getClassName(),
                    testDescription.getMethodName()).toFile(), destination.toFile());
        } catch (IOException e) {
            LOGGER.severe("Error moving jenkins log into artifacts directory: " + e.getMessage());
        }

        // Copy over the current page html to the artifacts directory
        if (currentPage != null) {
            String html = currentPage.asXml();
            try {
                Path path = Paths.get("target", "artifacts", testDescription.getClassName(),
                        testDescription.getMethodName(), "currentPage.html");
                Files.createDirectories(path.getParent());
                Files.write(path, html.getBytes(StandardCharsets.UTF_8));
            } catch (IOException artifactException) {
                LOGGER.severe("Failed to write page HTML to file: " + artifactException.getMessage());
                LOGGER.severe("HTML of page at the time of failure:");
                LOGGER.severe(html);
            }
        } else {
            LOGGER.severe("No current page was set");
        }
    }

    @Override
    public void before() throws Throwable {
        super.before();

        // Set up log handling
        try {
            if (handler != null) {
                // clean up old filehandler
                LOGGER.removeHandler(handler);
            }
            Path logPath = Paths.get("target", "logs", testDescription.getClassName(), testDescription.getMethodName(),
                    "jenkins.log");
            Files.createDirectories(logPath.getParent());
            handler = new FileHandler(logPath.toString());
            handler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(handler);
        } catch (IOException e) {
            LOGGER.severe("Error setting up log artifacts: " + e.getMessage());
        }

        if (ADMIN_PERSONAL_TOKEN.get() == null) {
            ADMIN_PERSONAL_TOKEN.set(createPersonalToken(REPO_ADMIN_PERMISSION));
            Runtime.getRuntime().addShutdownHook(new BitbucketTokenCleanUpThread(ADMIN_PERSONAL_TOKEN.get().getId()));
        }
        String adminCredentialsId = UUID.randomUUID().toString();
        Credentials adminCredentials = new BitbucketTokenCredentialsImpl(adminCredentialsId, "",
                SecretFactory.getSecret(ADMIN_PERSONAL_TOKEN.get().getSecret()));
        addCredentials(adminCredentials);

        if (READ_PERSONAL_TOKEN.get() == null) {
            READ_PERSONAL_TOKEN.set(createPersonalToken(PROJECT_READ_PERMISSION));
            Runtime.getRuntime().addShutdownHook(new BitbucketTokenCleanUpThread(READ_PERSONAL_TOKEN.get().getId()));
        }
        String readCredentialsId = UUID.randomUUID().toString();
        Credentials readCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, readCredentialsId,
                "", BITBUCKET_ADMIN_USERNAME, ADMIN_PERSONAL_TOKEN.get().getSecret());
        addCredentials(readCredentials);

        bitbucketServerConfiguration =
                new BitbucketServerConfiguration(adminCredentialsId, BITBUCKET_BASE_URL, readCredentialsId, null);
        bitbucketServerConfiguration.setServerName(SERVER_NAME);
        addBitbucketServer(bitbucketServerConfiguration);
    }

    public HtmlPage visit(String relativePath) throws IOException, SAXException {
        HtmlPage htmlPage = webClient.goTo(relativePath);
        currentPage = htmlPage;
        return htmlPage;
    }

    public void waitForBackgroundJavaScript() {
        webClient.waitForBackgroundJavaScript(2000);
    }

    public BitbucketServerConfiguration getBitbucketServerConfiguration() {
        return bitbucketServerConfiguration;
    }

    public UsernamePasswordCredentials getAdminToken() {
        return new UsernamePasswordCredentialsImpl(null, null, null, BITBUCKET_ADMIN_USERNAME, ADMIN_PERSONAL_TOKEN.get().getSecret());
    }

    public BitbucketPluginConfiguration getBitbucketPluginConfiguration() {
        return bitbucketPluginConfiguration;
    }

    private void addCredentials(Credentials credentials) throws IOException {
        CredentialsStore store = CredentialsProvider.lookupStores(jenkins).iterator().next();
        Domain domain = Domain.global();
        store.addCredentials(domain, credentials);
    }

    private static final class BitbucketTokenCleanUpThread extends Thread {

        private final String tokenId;

        private BitbucketTokenCleanUpThread(String tokenId) {
            this.tokenId = tokenId;
        }

        @Override
        public void run() {
            BitbucketUtils.deletePersonalToken(tokenId);
        }
    }
}
