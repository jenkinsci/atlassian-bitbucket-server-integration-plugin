package it.com.atlassian.bitbucket.jenkins.internal.util;

import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.logging.Logger;

public class BitbucketJenkinsWebClientRule extends TestWatcher {

    private static final Logger LOGGER = Logger.getLogger(BitbucketJenkinsWebClientRule.class.getName());
    private final JenkinsRule.WebClient webClient;
    private HtmlPage currentPage;

    public BitbucketJenkinsWebClientRule(BitbucketJenkinsRule bitbucketJenkinsRule) {
        webClient = bitbucketJenkinsRule.createWebClient();
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
            LOGGER.severe("Page state at the time of failure:");
            LOGGER.severe(currentPage.asXml());
        } else {
            LOGGER.severe("No current page was set");
        }
    }
}
