package upgrade.com.atlassian.bitbucket.jenkins.internal;

/**
 * This classloader rejects any classes that starts with "com.atlassian". This ensures that classes that has moved
 * cannot be found by this classloader, this is a core part of the upgrade testing.
 */
class RejectingParentClassLoader extends ClassLoader {

    public RejectingParentClassLoader(ClassLoader parent) {
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
