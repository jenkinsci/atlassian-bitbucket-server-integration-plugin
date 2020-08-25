package it.com.atlassian.bitbucket.jenkins.internal.fixture;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.PrintWriter;

import static wiremock.com.google.common.base.Charsets.UTF_8;

public class GitHelper {

    private final BitbucketJenkinsRule bbJenkinsRule;
    private File checkoutDir;
    private Git gitRepo;
    private CredentialsProvider adminCredentials;

    public GitHelper(BitbucketJenkinsRule bbJenkinsRule) {
        this.bbJenkinsRule = bbJenkinsRule;
    }

    public void initialize(File checkoutDir, String cloneUrl) throws Exception {
        UsernamePasswordCredentials bbCredentials = bbJenkinsRule.getAdminToken();
        adminCredentials =
                new UsernamePasswordCredentialsProvider(bbCredentials.getUsername(), bbCredentials.getPassword().getPlainText());
        gitRepo = Git.cloneRepository()
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .setURI(cloneUrl)
                .setCredentialsProvider(adminCredentials)
                .setDirectory(checkoutDir)
                .setBranch("master")
                .call();
        this.checkoutDir = checkoutDir;
    }

    public String addFileToRepo(String branch, String fileName, String fileContent) throws Exception {
        File jenkinsFile = new File(checkoutDir, fileName);
        FileUtils.writeStringToFile(jenkinsFile, fileContent, UTF_8);
        gitRepo.checkout().setName(branch).call();
        gitRepo.add().addFilepattern(fileName).call();
        final RevCommit rev =
                gitRepo.commit().setMessage("Adding " + fileName).setAuthor("Admin", "admin@localhost").call();
        gitRepo.push().setCredentialsProvider(adminCredentials).call();
        return rev.getName();
    }

    public String getLatestCommit() throws Exception {
        return gitRepo.log().setMaxCount(1).call().iterator().next().getName();
    }

    public String pushEmptyCommit(String message) throws Exception {
        RevCommit rev = gitRepo.commit().setAllowEmpty(true).setMessage(message).call();
        gitRepo.push().setCredentialsProvider(adminCredentials).call();
        return rev.getName();
    }

    public Git getGitRepo() {
        return gitRepo;
    }

    public void cleanup() {
        gitRepo.close();
    }
}
