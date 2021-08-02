package com.atlassian.bitbucket.jenkins.internal;

import hudson.model.UpdateSite;
import hudson.scm.SCM;
import hudson.triggers.Trigger;
import org.apache.commons.io.FileUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.junit.MockitoJUnitRunner;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RunWith(MockitoJUnitRunner.class)
public class UpgradeTest {

    @ClassRule
    public static final JenkinsRule jenkins = new JenkinsRule();

    //@Ignore("This test is experimental and should really not be run right now unless you're working on it")
    @Test
    public void testName() throws ExecutionException, InterruptedException, IOException, ClassNotFoundException {
        //TODO this test will be quite slow to run, so it should have some logging (System.out is probably enough)
        //to give indication of progress, doesn't have to be fine grained ("downloading hpi" is probably enough, no need to
        //do progress while it is downloading.

        //hardcoded path for now, we must fix this, stick it in target or something
        //we can use the "surefire.test.class.path" sys prop and then use a temp dir rule
        File destination = new File("/tmp/jenkins/lastReleased.hpi");
        //when working locally no need to go and get the file every time
        if (!destination.exists()) {
            System.out.println("Updating the update center");
            //update the update center, so we know of latest versions
            jenkins.jenkins.getUpdateCenter().updateAllSites();
            //get our plugin, this is the latest released version (compatible withe the version of Jenkins we run in the test
            UpdateSite.Plugin plugin = jenkins.jenkins.getUpdateCenter().getPlugin("atlassian-bitbucket-server-integration");
            System.out.println(plugin.url);
            System.out.println("Downloading hpi file");
            //use Commons.io to download the HPI file, wait 10s for connection and 10s for data transfer to start
            FileUtils.copyURLToFile(new URL(plugin.url), destination, 10_000, 10_1000);
            //the HPI is just a war file, so we need to unzip it.
            //this unzip code is stolen from the internet, and is unsafe, it is good enough for a proof of concept
            System.out.println("Unzip hpi file");
            UnzipFile.unzip(destination, new File("/tmp/jenkins/allFiles"));
        }

        //convert the files in the WEB-INF/lib directory to a List of URLs for later use
        List<URL> files = Files.list(Paths.get("/tmp/jenkins/allFiles/WEB-INF/lib"))
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
        //create a new Classloader, using the core classloader as parent. This means we can load all our classes from the hpi file,
        //but it is unaware of changed files in the version checked out.

        ClassLoader releasedPluginClassloader = new URLClassLoader(files.toArray(new URL[files.size()]), new RejectingParent(this.getClass().getClassLoader()));
        //this config of the reflections lib, does not find BitbucketSCM which is confusing, and also breaking the test
        Reflections reflections = new Reflections(new ConfigurationBuilder().addClassLoader(releasedPluginClassloader)
                .addUrls(files).setScanners(new SubTypesScanner(false), new TypeAnnotationsScanner()));
        Set<Class> interestingClasses = loadAtlassianClasses(releasedPluginClassloader, reflections);
        Set<Class> directlyUpgradeAffectedClasses = findDirectlyUpgradeClasses(interestingClasses);

        if (true) {
            return;
        }

        //some dummy playing around
        try {
            releasedPluginClassloader.loadClass("com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerImpl");
            //this class was created to show the concept, it does not exist in the downloaded one,
            //it must be created locally to prove this concept.
            releasedPluginClassloader.loadClass("com.atlassian.bitbucket.jenkins.internal.client.Dummy");
            //
        } catch (ClassNotFoundException e) {
            System.out.println("Not found: " + e);
        }
        //see note above, create this class as a test to ensure that the classloader releasedPluginClassloader above is
        //not polluted
        getClass().getClassLoader().loadClass("com.atlassian.bitbucket.jenkins.internal.client.Dummy");
    }

    private Set<Class> findDirectlyUpgradeClasses(Set<Class> allAtlassianClasses) {
        //this is overly simple at the moment but it gets us going

        return allAtlassianClasses.stream().filter(type ->
                SCM.class.isAssignableFrom(type) || Trigger.class.isAssignableFrom(type))
                .collect(Collectors.toSet());
    }

    private Set<Class> loadAtlassianClasses(ClassLoader releasedPluginClassloader, Reflections reflections) {
        return reflections.getAllTypes().stream().filter(type -> type.startsWith("com.atlassian")).filter(type -> !type.endsWith("package-info")).map(type -> {
            try {
                return releasedPluginClassloader.loadClass(type);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());
    }

    public static class UnzipFile {

        //this is unsafe, we need to fix that for the final version of this test
        //https://snyk.io/research/zip-slip-vulnerability
        public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
            File destFile = new File(destinationDir, zipEntry.getName());

            String destDirPath = destinationDir.getCanonicalPath();
            String destFilePath = destFile.getCanonicalPath();

            if (!destFilePath.startsWith(destDirPath + File.separator)) {
                throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
            }

            return destFile;
        }

        public static void unzip(File fileZip, File destDir) throws IOException {

            final byte[] buffer = new byte[1024];
            final ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                final File newFile = newFile(destDir, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    final FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
        }
    }

    private static class RejectingParent extends ClassLoader {

        public RejectingParent(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            //this means that the URLClassloader we create will load all com.atlassian classes,
            //but all dependent classes, such as hudson.trigger or indeed java.lang.Object comes from
            //our parent.
            if (name.startsWith("com.atlassian")) {
                throw new ClassNotFoundException("Cannot load com.atlassian classes through this loader");
            }
            return super.loadClass(name, resolve);
        }
    }
}