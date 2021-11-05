package upgrade.com.atlassian.bitbucket.jenkins.internal;

import com.atlassian.bitbucket.jenkins.internal.annotations.NotUpgradeSensitive;
import com.atlassian.bitbucket.jenkins.internal.annotations.UpgradeHandled;
import com.cloudbees.plugins.credentials.BaseCredentials;
import hudson.model.AbstractDescribableImpl;
import hudson.model.UpdateSite;
import hudson.scm.SCM;
import hudson.triggers.Trigger;
import jenkins.model.GlobalConfiguration;
import jenkins.scm.api.SCMSource;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.junit.MockitoJUnitRunner;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.reflections.ReflectionUtils.getAllFields;

@RunWith(MockitoJUnitRunner.class)
public class UpgradeTest {

    @Rule
    public final JenkinsRule jenkins = new JenkinsRule();
    @Rule
    public FailureCollector errorCollector = new FailureCollector();
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * This is a rather complicated test with lots of supporting methods. The gist of it is
     * 1. Download the already released version of the plugin
     * 2. Compare classes that are deemed risky for upgrade
     *
     * Annotations and text files are used to provide flexibility in making risky changes. They exist mostly to force
     * you to stop and think about the upgrade scenario.
     *
     * If this test failed, you must perform a manual upgrade test from an already released version. Check both FreeStyle
     * and MultibranchPipeline jobs, as well as pipeline jobs. Ensure that triggers still work, ensure that the builds
     * can run successfully. Once that is done add annotations (or a line to the text file if a class was removed) explaining
     * how the upgrade is handled, as well as what version the annotation should be removed in.
     */
    @Test
    public void staticUpgradeAnalysis() throws ExecutionException, InterruptedException, IOException, ClassNotFoundException {
        //download, if needed, the HPI and extract it and return all jar files
        List<URL> files = fetchHpiAndExtract();
        Map<String, String> safelyRemovedClasses = SafeRemovedClassList.loadSafeList();
        //scan the downloaded files for later use
        Reflections reflections = setupReflectionsLibrary(files);
        //add all classes we know are upgrade sensitive right away, such as SCM files and Triggers and the like
        Set<Class<?>> releaseSensitiveClasses = gatherUpgradeSensitiveClasses(reflections);

        int classCount = releaseSensitiveClasses.size();
        System.out.println("Found " + classCount + " classes that were deemed interesting");

        releaseSensitiveClasses = filterAnnotatedClasses(releaseSensitiveClasses);
        System.out.println("Filtered out " + (classCount - releaseSensitiveClasses.size()) + " that were annotated as safe");

        classCount = releaseSensitiveClasses.size();
        expandToVariableTypes(reflections, releaseSensitiveClasses);
        System.out.println("Added " + (releaseSensitiveClasses.size() - classCount) + " classes from fields used");

        //load all classes from the current checked out version
        Map<String, Class<?>> unreleasedClasses = loadUnreleasedClasses(releaseSensitiveClasses, safelyRemovedClasses);

        //compare the fields found
        compareFields(releaseSensitiveClasses, unreleasedClasses);

        safelyRemovedClasses.keySet().forEach(staleEntry -> errorCollector.addStaleSafeListEntry(staleEntry));
    }

    /**
     * Iterate over all released classes and compare all the fields with the fields in the same unreleased class
     */
    private void compareFields(Set<Class<?>> releasedClasses, Map<String, Class<?>> unreleasedClasses) {
        for (Class<?> releasedClass : releasedClasses) {
            if (!unreleasedClasses.containsKey(releasedClass.getName())) {
                //while this is a breaking change, it was detected when we loaded classes, so we can skip it now
                //in the knowledge it has already been reported.
                continue;
            }
            Map<String, Field> releasedFields = getAllFieldsForType(releasedClass);
            Map<String, Field> unreleasedFields = getAllFieldsForType(unreleasedClasses.get(releasedClass.getName()));

            //check the fields to see if they are changed, we remove them as they pass the test, so we can't use streams
            // we could use removeIf(..) but using iterators was easier to read
            for (Iterator<Map.Entry<String, Field>> i = releasedFields.entrySet().iterator(); i.hasNext();) {
                Map.Entry<String, Field> entry = i.next();
                Field releasedField = entry.getValue();
                Field unreleasedField = unreleasedFields.get(entry.getKey());
                if (unreleasedField == null) {
                    //field was removed, this should be ok
                    continue;
                }

                if (unreleasedField.getAnnotation(UpgradeHandled.class) != null) {
                    //annotated, as safe so we ignore any further processing and remove from the maps
                    i.remove();
                    unreleasedFields.remove(entry.getKey());
                    continue;
                }
                //now compare the fields, see doc on method for more detailed explanation
                if (isFieldsEqual(releasedField, unreleasedField)) {
                    i.remove();
                    unreleasedFields.remove(entry.getKey());
                }
            }
            unreleasedFields.entrySet().stream()
                    .filter(entry -> entry.getValue().getAnnotation(UpgradeHandled.class) == null)
                    .forEach(entry -> {
                        //if the released fields contains the same key, it will be reported after this with a better
                        //message
                        if (!releasedFields.containsKey(entry.getKey())) {
                            errorCollector.newFieldAdded(entry.getValue());
                        }
                    });
            releasedFields.values().forEach(field -> errorCollector.oldFieldModified(field));
        }
    }

    /**
     * Iterate over all the given classes and add any classes used in a declared variable to the set
     */
    private void expandToVariableTypes(Reflections reflections, Set<Class<?>> classes) {
        //must use an empty set or the recursion breaker will kick in and not expand the type
        Set<Class<?>> expandedVariableTypes = new HashSet<>();
        classes.forEach(type -> expandType(reflections, type, expandedVariableTypes));
        classes.addAll(expandedVariableTypes);
    }

    /**
     * Find all declared variables and expand each type and add to the list
     */
    private void expandType(Reflections reflections, Class<?> typeToExpand, Set<Class<?>> classes) {
        if (classes.contains(typeToExpand)) {
            return;
        }
        //must be added before iterating variables to not give infinite recursion for self references
        classes.add(typeToExpand);

        //if it is an interface we must expand the interface into implementing classes
        if (typeToExpand.isInterface()) {
            reflections.getSubTypesOf(typeToExpand).forEach(type -> expandType(reflections, type, classes));
        }
        //find all
        Arrays.stream(typeToExpand.getDeclaredFields())
                .map(Field::getType)
                .filter(fieldType -> fieldType.getName().startsWith("com.atlassian"))
                //We should *not* filter out by annotation here, if the class ends up here it is part of an
                //upgrade sensitive class, so we must consider it.
                .forEach(atlCls -> expandType(reflections, atlCls, classes));
    }

    /**
     * Download the hpi file and extract it. When running locally it will expand to /tmp/jenkins/allFiles (creating directories as needed)
     * If the hpi file already exists in that directory structure it will <b>not</b> attempt to download the file again.
     * When run through maven and surefire the file is downloaded to a temporary directory as provided by the rule.
     * @see UpgradeTest#getTempDir() for details on how the decision is made
     */
    private List<URL> fetchHpiAndExtract() throws IOException, InterruptedException, ExecutionException {
        File tempDir = getTempDir();
        File releasedHPIFile = getDestinationFile(tempDir);
        //when working locally no need to go and get the file every time
        if (!releasedHPIFile.exists()) {
            System.out.println("Updating the update center");
            //update the update center, so we know of latest versions
            jenkins.jenkins.getUpdateCenter().updateAllSites();
            //get our plugin, this is the latest released version (compatible withe the version of Jenkins we run in the test
            UpdateSite.Plugin plugin = jenkins.jenkins.getUpdateCenter().getPlugin("atlassian-bitbucket-server-integration");
            System.out.println("Will download: " + plugin.url);
            System.out.println("Downloading hpi file");
            //use Commons.io to download the HPI file, wait 10s for connection and 10s for data transfer to start
            FileUtils.copyURLToFile(new URL(plugin.url), releasedHPIFile, 10_000, 10_1000);
            //the HPI is just a war file, so we need to unzip it.
            //this unzip code is stolen from the internet, and is unsafe, it is good enough for a proof of concept
            System.out.println("Unzip hpi file");
            new ZipFile(releasedHPIFile).extractAll(new File(tempDir, "/allFiles").getCanonicalPath());
        }

        //convert the files in the WEB-INF/lib directory to a List of URLs for later use
        List<URL> files = Files.list(Paths.get(new File(tempDir, "/allFiles/WEB-INF/lib").toURI()))
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .map(file -> {
                    try {
                        return file.toURI().toURL();
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
        return files;
    }

    /**
     * Filter out all classes that has the NotUpgradeSensitive annotation as they are already evaluated as safe so
     * no need to check them.
     */
    private Set<Class<?>> filterAnnotatedClasses(Set<Class<?>> interestingClasses) {
        return interestingClasses.stream()
                .filter(type -> type.getAnnotation(NotUpgradeSensitive.class) == null)
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> gatherUpgradeSensitiveClasses(Reflections reflections) {
        Set<Class<?>> sensitiveClasses = new HashSet<>();
        //Gather interesting classes up. There is a distinct possibility that one is a subtype of another
        //but for clarify of this test they are added explicitly such that we can be clear about why they are added
        sensitiveClasses.addAll(getAtlassianSubtypesOf(reflections, SCM.class));
        sensitiveClasses.addAll(getAtlassianSubtypesOf(reflections, SCMSource.class));
        sensitiveClasses.addAll(getAtlassianSubtypesOf(reflections, Trigger.class));
        sensitiveClasses.addAll(getAtlassianSubtypesOf(reflections, BaseCredentials.class));
        sensitiveClasses.addAll(getAtlassianSubtypesOf(reflections, Step.class));
        sensitiveClasses.addAll(getAtlassianSubtypesOf(reflections, GlobalConfiguration.class));
        sensitiveClasses.addAll(getAtlassianSubtypesOf(reflections, AbstractDescribableImpl.class));
        sensitiveClasses.addAll(getAtlassianSubtypesOf(reflections, BodyExecutionCallback.class));
        sensitiveClasses.addAll(getAtlassianSubtypesOf(reflections, StepExecution.class));
        return sensitiveClasses;
    }

    /**
     * Get all the fields for the given class, the key is the declaring class (which may be a super type), and the
     * name of the field. The declaring class is part of the key to make sure that variables are captured even if they
     * happen to share the name with a private field in a super class.
     */
    private Map<String, Field> getAllFieldsForType(Class<?> cls) {

        return getAllFields(cls, field -> field.getDeclaringClass().getName().startsWith("com.atlassian") && !field.isSynthetic()).stream()
                .collect(toMap(field -> field.getDeclaringClass().toString() + "-" + field.getName(), Function.identity()));
    }

    /**
     * Get all the subtypes of the given class and filter out any classes not in the "com.atlassian" package
     */
    private Set<Class<? extends Object>> getAtlassianSubtypesOf(Reflections reflections, Class<? extends Object> cls) {
        return reflections.getSubTypesOf(cls).stream()
                .filter(type -> type.getName().startsWith("com.atlassian"))
                .collect(Collectors.toSet());
    }

    private File getDestinationFile(File directory) {
        return new File(directory, "latestReleasedVersion.hpi");
    }

    /**
     * Attempts to decide if running through an IDE or via surefire/maven. It is quite simple in the detection,
     * it just looks at the property "surefire.test.class.path" which is set by surefire and if it is present
     * assumes it is run via surefire and uses the tempFolder rule to get a temporary directory. If it is not present
     * downloads to /tmp/jenkins and creates directories as needed.
     */
    private File getTempDir() throws IOException {
        File tempDir;
        if (System.getProperty("surefire.test.class.path") != null) {
            //we're running through maven, so we don't use cached data and we clean up after the test is done
            tempDir = tempFolder.newFolder();
        } else {
            //we're run through an IDE so we use a predictable location and do not clean up after us so we don't
            //need to download the file every time
            tempDir = new File("/tmp/jenkins/");
        }
        //create the directory structure for all
        if (!new File(tempDir, "allFiles").mkdirs()) {
            System.out.println("Could not create temp directory structure, test is likely to fail");
        }
        return tempDir;
    }

    /**
     * Pretty much a re-implementation of the equals in the Field class, but with the important difference that
     * class comparison is by <b>name</b> not by type. As the the classes come from different classloaders they are
     * different according to java semantics, but equal for our purposes.
     */
    private boolean isFieldsEqual(Field releasedField, Field unreleasedField) {
        //must compare declaring class by name, not by actual type as they come from different classloaders.
        return releasedField.getDeclaringClass().getName().equals(unreleasedField.getDeclaringClass().getName()) &&
                releasedField.getType().getName().equals(unreleasedField.getType().getName()) &&
                releasedField.getName().equals(unreleasedField.getName());
    }

    private Map<String, Class<?>> loadUnreleasedClasses(Set<Class<?>> interestingClasses, Map<String, String> safelyRemovedClasses) {
        ClassLoader loader = UpgradeTest.class.getClassLoader();

        //Do not remove newly annotated classes, as the set of classes we've got here contains
        //field types which needs to be checked regardless of annotations.
        return interestingClasses.stream()
                .map(oldClass -> {
                    String name = oldClass.getName();
                    try {
                        return loader.loadClass(name);
                    } catch (ClassNotFoundException e) {
                        if (safelyRemovedClasses.containsKey(name)) {
                            safelyRemovedClasses.remove(name);
                        } else {
                            errorCollector.addClassNotFound(e);
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(toMap(Class::getName, Function.identity()));
    }

    private Reflections setupReflectionsLibrary(List<URL> files) {
        ClassLoader releasedPluginClassloader = new URLClassLoader(files.toArray(new URL[0]),
                new RejectingParentClassLoader(this.getClass().getClassLoader()));
        System.out.println("Scanning the downloaded jar files for classes and resources");
        return new Reflections(new ConfigurationBuilder().addClassLoader(releasedPluginClassloader)
                .addUrls(files).setScanners(new SubTypesScanner(false), new TypeAnnotationsScanner(), new FieldAnnotationsScanner()));
    }
}