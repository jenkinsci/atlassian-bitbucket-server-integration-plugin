package upgrade.com.atlassian.bitbucket.jenkins.internal;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

/**
 * This allows us to gather up errors and report all in one go rather than stop on the first error.
 * It is *heavily* inspired by org.junit.runners.model.MultipleFailureException but that supports only exceptions.
 * The way this is used makes stack traces worthless (they do not say anything about what actually is problematic),
 * so this class simply gathers up errors and reports them at the end without a stacktrace. It has convince methods
 * to make generating error messages easier.
 */
public class FailureCollector implements TestRule {

    private List<String> errors = new ArrayList<>();

    public void addAnnotationRemovalFailure(String version, Field field) {
        errors.add(format("UpgradeHandled annotaton on '%s' in '%s' should be removed in or before current version (%s)",
                field.getName(), field.getDeclaringClass().getName(), version));
    }

    public void addClassNotFound(ClassNotFoundException e) {
        errors.add(format("Could not load '%s' this will cause an upgrade error", e.getMessage()));
    }

    public void addStaleSafeListEntry(String staleEntry) {
        errors.add(format("safeRemovedClass.txt contains a stale entry that needs to be removed: '%s'", staleEntry));
    }

    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
                verify();
            }
        };
    }

    public void newFieldAdded(Field field) {
        errors.add(format("Field '%s' in '%s' was added, this can cause issues with null or default values unless handled properly outside of constructor",
                field.getName(), field.getDeclaringClass().getName()));
    }

    public void oldFieldModified(Field field) {
        errors.add(format("Field '%s' in '%s' was changed in a way that is not upgrade safe",
                field.getName(), field.getDeclaringClass().getName()));
    }

    protected void verify() {
        if (errors.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (errors.size() == 1) {
            sb.append("An upgrade problem found:\n")
                    .append(errors.get(0))
                    .append('\n');
        } else {

            sb.append(format("%d upgrade errors found\n", errors.size()));
            int count = 1;
            for (String error : errors) {
                sb.append("\t ")
                        .append(count++)
                        .append(". ")
                        .append(error)
                        .append('\n');
            }
        }
        throw new TestFailureException(sb.toString());
    }

    /**
     * This class exists to suppress printing stacktraces as the stacktrace is utterly irrrelevant
     * but we need to use an exception to fail the test and get the appropriate error messages
     */
    private static class TestFailureException extends RuntimeException {

        public TestFailureException(String message) {
            super(message, null, true, false);
        }
    }
}
