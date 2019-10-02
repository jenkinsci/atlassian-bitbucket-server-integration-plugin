package it.com.atlassian.bitbucket.jenkins.internal.fixture;

import org.apache.commons.io.FileUtils;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class BitbucketJenkinsLoggerRule extends TestWatcher {

    private static final Logger LOGGER = Logger.getLogger(BitbucketJenkinsLoggerRule.class.getName());
    private final Logger logger;
    private FileHandler handler;

    public BitbucketJenkinsLoggerRule() {
        logger = Logger.getLogger("");
        logger.setLevel(Level.INFO);
    }

    @Override
    protected void failed(Throwable e, Description description) {
        try {
            // Copy over the log into the artifact directory for failing tests
            Path destination = Paths.get("target", "artifacts", description.getClassName(),
                    description.getMethodName());
            Files.createDirectories(destination.getParent());
            FileUtils.deleteDirectory(destination.toFile());
            FileUtils.moveDirectory(Paths.get("target", "logs", description.getClassName(),
                    description.getMethodName()).toFile(), destination.toFile());
        } catch (IOException ex) {
            throw new RuntimeException("Failed to move jenkins log into artifacts directory", ex);
        }
    }

    @Override
    protected void starting(Description description) {
        try {
            logger.removeHandler(handler);
            Path logPath = Paths.get("target", "logs", description.getClassName(), description.getMethodName(), "jenkins.log");
            Files.createDirectories(logPath.getParent());
            handler = new FileHandler(logPath.toString());
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
