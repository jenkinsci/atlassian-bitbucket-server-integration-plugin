package com.atlassian.bitbucket.jenkins.internal;

import com.atlassian.bitbucket.jenkins.internal.annotations.UpgradeHandled;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import upgrade.com.atlassian.bitbucket.jenkins.internal.FailureCollector;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class StaleAnnotationTest {

    @Rule
    public FailureCollector errorCollector = new FailureCollector();
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    /**
     * If this test fails, the listed annotations are stale and should be removed. This prevents a field from being
     * forever marked as safe. The upgrade is safe for one version only, the upgrade *handling* cannot be removed,
     * but the annotation can.
     */
    @Test
    public void testAnnotationsAreAllStillRelevant() {

        Reflections r = new Reflections(ConfigurationBuilder.build().setScanners(new TypeAnnotationsScanner(), new FieldAnnotationsScanner()));
        Set<Field> annotatedFields = r.getFieldsAnnotatedWith(UpgradeHandled.class);
        //this is the current version of the pom.xml including the "-SNAPSHOT"
        //since this is the SNAPSHOT version we strip off the "-SNAPSHOT" and pretend it is released
        String unreleasedVersion = jenkins.jenkins.getPlugin("atlassian-bitbucket-server-integration")
                .getWrapper()
                .getVersion().replaceAll("-SNAPSHOT.*", "");

        annotatedFields.forEach(field -> {
            //the annotation should be present as we only got fields that were annotated with this annotation.
            UpgradeHandled annotation = field.getAnnotation(UpgradeHandled.class);
            if (equalOrBefore(unreleasedVersion, annotation.removeAnnotationInVersion())) {
                errorCollector.addAnnotationRemovalFailure(unreleasedVersion, field);
            }
        });
    }

    /**
     * Compare the version, and returns true if the version to check is the same or before the unreleasedVersion
     *
     * @param unreleasedVersion version to compare against
     * @param versionToCheck    version to check.
     * @return true if the version to check is the same or before (lower) than the unreleasedVersion
     */
    private boolean equalOrBefore(String unreleasedVersion, String versionToCheck) {
        List<String> unrelasedParts = Arrays.asList(unreleasedVersion.split("\\."));
        List<String> toCheckParts = Arrays.asList(versionToCheck.split("\\."));
        //ensure they are of equal size. Just append "0" until they are equal in size
        if (unrelasedParts.size() > toCheckParts.size()) {
            toCheckParts = new ArrayList<>(toCheckParts);
            while (unrelasedParts.size() > toCheckParts.size()) {
                toCheckParts.add("0");
            }
        }
        if (toCheckParts.size() > unrelasedParts.size()) {
            unrelasedParts = new ArrayList<>(unrelasedParts);
            while (toCheckParts.size() > unrelasedParts.size()) {
                unrelasedParts.add("0");
            }
        }
        for (int i = 0; i < toCheckParts.size(); i++) {
            int toCheck = Integer.parseInt(toCheckParts.get(i));
            int unreleased = Integer.parseInt(unrelasedParts.get(i));
            if (toCheck > unreleased) {
                return false;
            }
        }
        //if we got here, all fields are equal
        return true;
    }
}
