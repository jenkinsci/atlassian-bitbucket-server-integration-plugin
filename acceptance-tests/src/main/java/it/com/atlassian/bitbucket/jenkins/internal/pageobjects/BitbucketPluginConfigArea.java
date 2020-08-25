package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.JenkinsConfig;
import org.jenkinsci.test.acceptance.po.PageArea;
import org.jenkinsci.test.acceptance.po.PageAreaImpl;
import org.openqa.selenium.support.ui.Select;

/**
 * Represents the Bitbucket Server configuration {@link PageAreaImpl area} in the Jenkins
 * {@link JenkinsConfig System Config page}
 */
public class BitbucketPluginConfigArea extends PageAreaImpl {

    private final Control addButton = control("hetero-list-add[serverList]");

    public BitbucketPluginConfigArea(JenkinsConfig parent) {
        super(parent, "/com-atlassian-bitbucket-jenkins-internal-config-BitbucketPluginConfiguration");
    }

    public void addBitbucketServerConfig(String serverName, String baseUrl, String bbsTokenId, String adminCredsId) {
        String path = createPageArea("serverList", () -> addButton.selectDropdownMenu("Instance details"));
        BitbucketServerConfigArea bbsConfig =
                new BitbucketServerConfigArea(this, path, serverName, baseUrl, bbsTokenId, adminCredsId);
        bbsConfig.create();
    }

    /**
     * Represents the {@link PageAreaImpl area} in the {@link JenkinsConfig Jenkins System Config page} for adding a
     * new Bitbucket Server instance to Jenkins (e.g. to be used as the SCM source for CI jobs)
     */
    public static class BitbucketServerConfigArea extends PageAreaImpl {

        private final Control instanceName = control("serverName");
        private final Control instanceUrl = control("baseUrl");
        private final Control adminCredentials = control("adminCredentialsId");
        private final Control buildAuthCredentials = control("credentialsId");

        private final String serverName;
        private final String baseUrl;
        private final String bbsTokenId;
        private final String bbsCredsId;

        protected BitbucketServerConfigArea(PageArea area, String path, String serverName, String baseUrl,
                                            String bbsTokenId, String bbsCredsId) {
            super(area, path);

            this.serverName = serverName;
            this.baseUrl = baseUrl;
            this.bbsTokenId = bbsTokenId;
            this.bbsCredsId = bbsCredsId;
        }

        public void create() {
            instanceName.set(serverName);
            instanceUrl.set(baseUrl);
            new Select(adminCredentials.resolve()).selectByVisibleText(bbsTokenId + " - Bitbucket admin token");
            new Select(buildAuthCredentials.resolve()).selectByValue(bbsCredsId);
        }
    }
}
