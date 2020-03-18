package it.com.atlassian.bitbucket.jenkins.internal.util;

import jenkins.model.Jenkins;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class SingleExecutorRule implements TestRule {

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                int initialExecutors = Jenkins.get().getNumExecutors();
                Jenkins.get().setNumExecutors(1);

                try {
                    base.evaluate();
                } finally {
                    Jenkins.get().setNumExecutors(initialExecutors);
                }
            }
        };
    }
}
