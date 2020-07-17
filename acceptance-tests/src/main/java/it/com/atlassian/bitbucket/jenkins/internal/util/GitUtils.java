package it.com.atlassian.bitbucket.jenkins.internal.util;

import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.BitbucketRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.BITBUCKET_ADMIN_PASSWORD;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.BITBUCKET_ADMIN_USERNAME;

public final class GitUtils {

    public static final CredentialsProvider ADMIN_CREDENTIALS_PROVIDER =
            new UsernamePasswordCredentialsProvider(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD);

    private GitUtils() {
    }

    public static Git cloneRepo(CredentialsProvider cr, File checkoutDir,
                                BitbucketRepository repo) throws GitAPIException {
        return cloneRepo(cr, checkoutDir, repo, "master");
    }

    public static Git cloneRepo(CredentialsProvider cr, File checkoutDir,
                                BitbucketRepository forkRepo, String branch) throws GitAPIException {
        return Git.cloneRepository()
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .setURI(forkRepo.getHttpCloneUrl())
                .setCredentialsProvider(cr)
                .setDirectory(checkoutDir)
                .setBranch(branch)
                .call();
    }

    public static RevCommit commitAndPushFile(Git gitRepo, CredentialsProvider credentialsProvider, String branchName,
                                              File checkoutDir, String fileName,
                                              byte[] fileContents) throws GitAPIException, IOException {
        gitRepo.checkout().setName(branchName).call();
        Path filePath = new File(checkoutDir, fileName).toPath();
        Files.write(filePath, fileContents);
        gitRepo.add().addFilepattern(fileName).call();
        RevCommit commit =
                gitRepo.commit().setMessage("Adding " + fileName).setAuthor("Admin", "admin@localhost").call();
        gitRepo.push().setCredentialsProvider(credentialsProvider).call();
        return commit;
    }
}
