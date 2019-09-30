package it.com.atlassian.bitbucket.jenkins.internal.fixture;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class BitbucketJenkinsWebClientRule extends TestWatcher {

    private static final Logger LOGGER = Logger.getLogger(BitbucketJenkinsWebClientRule.class.getName());
    private final JenkinsRule.WebClient webClient;
    private HtmlPage currentPage;

    public BitbucketJenkinsWebClientRule(JenkinsRule.WebClient webClient) {
        this.webClient = webClient;
    }

    public HtmlPage visit(String relativePath) throws IOException, SAXException {
        HtmlPage htmlPage = webClient.goTo(relativePath);
        currentPage = htmlPage;
        return htmlPage;
    }

    public void waitForBackgroundJavaScript() {
        webClient.waitForBackgroundJavaScript(2000);
    }

    @Override
    protected void failed(Throwable e, Description description) {
        if (currentPage != null) {
            String html = currentPage.asXml();
            try {
                Path path = Paths.get("target", "artifacts", description.getClassName(), description.getMethodName(), "currentPage.html");
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
}
