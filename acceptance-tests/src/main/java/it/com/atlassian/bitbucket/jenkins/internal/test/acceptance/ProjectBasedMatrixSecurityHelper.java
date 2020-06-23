package it.com.atlassian.bitbucket.jenkins.internal.test.acceptance;

import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.pageobjects.LoginPage;
import org.jenkinsci.test.acceptance.controller.ExistingJenkinsController;
import org.jenkinsci.test.acceptance.controller.JenkinsController;
import org.jenkinsci.test.acceptance.controller.WinstoneController;
import org.jenkinsci.test.acceptance.guice.TestCleaner;
import org.jenkinsci.test.acceptance.guice.TestScope;
import org.jenkinsci.test.acceptance.plugins.matrix_auth.MatrixRow;
import org.jenkinsci.test.acceptance.plugins.matrix_auth.ProjectBasedMatrixAuthorizationStrategy;
import org.jenkinsci.test.acceptance.plugins.matrix_auth.ProjectMatrixProperty;
import org.jenkinsci.test.acceptance.po.*;
import org.jenkinsci.test.acceptance.utils.pluginTests.SecurityDisabler;
import org.openqa.selenium.NoSuchElementException;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.pageobjects.LoginPage.isSuccessfulLogin;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test helper class for setting up project-based matrix security in the Jenkins instance under test.
 * <p>
 * Should be {@link Inject injected} into test classes and used to set up security, create users, add permissions, etc.:
 * <p>
 * <pre>{@code
 * public class MyAcceptanceTest {
 *
 *     @Inject
 *     private ProjectBasedMatrixSecurityHelper security;
 *
 *     @Before
 *     public void setUp() {
 *         User testUser = security.newUser();
 *         ...
 *     }
 * }
 * }</pre>
 * <p>
 * If an external/existing Jenkins instance is used for testing (as opposed to a
 * {@link WinstoneController per-test instance} started up by the test framework), then any {@link User user} created
 * via this configurer will be deleted, and security will be disabled after the test is run. For more info on how the
 * clean-up works, see {@link TestCleaner the test cleaner}.
 * <p>
 * Automatic cleanup is skipped if an external/pre-launched Jenkins instance isn't used, to cut down test run time. If
 * you <i>have to</i> clean up for some reason, then you should call {@link #forceCleanUp()} in your test class's
 * {@code @After} method.
 */
@TestScope
public class ProjectBasedMatrixSecurityHelper {

    public static final String ADMIN_USER = "admin";

    private final Jenkins jenkins;
    private final JenkinsController jenkinsController;
    private final Set<User> users = new HashSet<>();

    private User admin;
    private JenkinsDatabaseSecurityRealm securityRealm;

    @Inject
    public ProjectBasedMatrixSecurityHelper(Jenkins jenkins, TestCleaner testCleaner,
                                            JenkinsController jenkinsController) {
        this.jenkins = jenkins;
        this.jenkinsController = jenkinsController;
        testCleaner.addTask((Runnable) this::cleanUp);
    }

    public User newUser() {
        loginAsAdmin();
        String username = "user" + randomNumeric(12);
        User user = securityRealm.signup(username);
        users.add(user);
        return user;
    }

    public void addGlobalPermissions(Map<User, Consumer<MatrixRow>> userPermissions) {
        loginAsAdmin();
        enableProjectBasedMatrixSecurity(projectBasedMatrixSecurity ->
                userPermissions.forEach((user, perms) ->
                        perms.accept(getOrAddUserRow(projectBasedMatrixSecurity, user))));
    }

    public void addProjectPermissions(Job job, Map<User, Consumer<MatrixRow>> userPermissions) {
        loginAsAdmin();
        enableProjectBasedMatrixSecurity();
        // Project/job-level matrix security is configured in the job itself
        job.configure();
        ProjectMatrixProperty pmp = new ProjectMatrixProperty(job);
        pmp.enable.check();
        userPermissions.forEach((user, perms) ->
                perms.accept(getOrAddUserRow(pmp, user)));
        job.save();
    }

    public void loginAsAdmin() {
        enableSecurity();
        login(admin);
    }

    public void login(User user) {
        if (Objects.equals(user, jenkins.getCurrentUser())) {
            return;
        }
        assertThat(new LoginPage(jenkins).load().login(user), isSuccessfulLogin());
    }

    public void cleanUp() {
        // We don't need to clean up if we're not testing against an 'existing' (pre-launched) Jenkins instance.
        // Per-test Jenkins instances (e.g. those started up by the WinstoneController, are discarded after each test
        // and a fresh instance is started up for the next test, so we can save some time (e.g. in CI environments) by
        // skipping the clean-up.
        if (jenkinsController instanceof ExistingJenkinsController) {
            forceCleanUp();
        }
    }

    public void forceCleanUp() {
        loginAsAdmin();
        // Disable security and delete test users
        new SecurityDisabler(jenkins).stopUsingSecurityAndSave();
        users.forEach(user -> {
            try {
                user.delete();
            } catch (NoSuchElementException te) {
                // user doesn't exist anymore (e.g. it may have already been deleted in the test)
            }
        });
    }

    private void enableSecurity() {
        if (securityRealm != null) {
            return;
        }
        GlobalSecurityConfig securityConfig = new GlobalSecurityConfig(jenkins);
        securityConfig.configure();
        securityRealm = securityConfig.useRealm(JenkinsDatabaseSecurityRealm.class);
        securityRealm.allowUsersToSignUp(true);
        securityConfig.save();
        // create global admin user
        admin = securityRealm.signup(ADMIN_USER);
    }

    private void enableProjectBasedMatrixSecurity() {
        enableProjectBasedMatrixSecurity(projectBasedMatrixSecurity -> {
        });
    }

    private void enableProjectBasedMatrixSecurity(
            Consumer<ProjectBasedMatrixAuthorizationStrategy> configureMatrixSecurity) {
        GlobalSecurityConfig securityConfig = new GlobalSecurityConfig(jenkins);
        securityConfig.configure();
        configureMatrixSecurity.accept(
                securityConfig.useAuthorizationStrategy(ProjectBasedMatrixAuthorizationStrategy.class));
        securityConfig.save();
    }

    private MatrixRow getOrAddUserRow(ProjectBasedMatrixAuthorizationStrategy pbms, User user) {
        String username = user.fullName();
        return getOrAddUserRow(() -> pbms.getUser(username), () -> pbms.addUser(username));
    }

    private MatrixRow getOrAddUserRow(ProjectMatrixProperty pmp, User user) {
        String username = user.fullName();
        return getOrAddUserRow(() -> pmp.getUser(username), () -> pmp.addUser(username));
    }

    private MatrixRow getOrAddUserRow(Supplier<MatrixRow> getUserRow, Supplier<MatrixRow> addUserRow) {
        MatrixRow userRow = getUserRow.get();
        try {
            userRow.self();
        } catch (NoSuchElementException e) {
            // User row isn't present, let's add it
            userRow = addUserRow.get();
        }
        return userRow;
    }
}
