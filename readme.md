# Bitbucket Server integration
[![Build Status](https://ci.jenkins.io/job/Plugins/job/atlassian-bitbucket-server-integration-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/atlassian-bitbucket-server-integration-plugin/job/master/)

---
We're collecting feedback at [issues.jenkins-ci.org](https://issues.jenkins-ci.org/browse/JENKINS-59578?jql=project%20%3D%20JENKINS%20AND%20component%20%3D%20atlassian-bitbucket-server-integration-plugin). Head there to see what issues have already been created, or create a new issue using the component _atlassian-bitbucket-server-integration-plugin_.

---

The Bitbucket Server integration plugin is the easiest way to connect [Jenkins](http://jenkins.io/) to [Bitbucket Server](https://www.atlassian.com/software/bitbucket/enterprise/data-center). With a few simple steps you can configure it to:
- Automatically create webhooks in Bitbucket to trigger Jenkins builds
- Allow Jenkins to clone/fetch from Bitbucket to run the builds

It streamlines this entire process, removing the need for multiple plugins to achieve the same workflow.

The plugin enables this in two ways. It adds a Bitbucket Server Source Code Manager (SCM) to Jenkins, making it easy to set up a connection to a Bitbucket Server repository when setting up a Jenkins job. It also adds a build trigger to Jenkins that automatically creates a webhook against Bitbucket Server that triggers the Jenkins job on relevant pushes.

## Plugin features

- Support for Jenkins Pipeline and Freestyle projects
- Secure credential management in Jenkins for cloning from Bitbucket Server
- Automatic webhook creation in a Bitbucket Server repo when a Jenkins job is saved
- Quick selection of Bitbucket Server projects and repos for a Jenkins job through a dropdown
- The ability to automatically send build statuses to Bitbucket Server
- Cloning from Bitbucket Server Smart Mirrors with no need to modify the clone URL

---

## Using the plugin

### Adding Bitbucket Server instance details

Bitbucket Server instances are added and configured at the system level. Once they’re added users can select them from the SCM when creating a Jenkins job. You must add at least one Bitbucket Server instance to Jenkins.

When adding a Bitbucket Server instance you must add at least one Bitbucket Server [personal access token](https://confluence.atlassian.com/display/BitbucketServer/personal+access+tokens). Doing this allows users to automatically set up build triggers when creating a Jenkins job. For this to work the tokens you add must have project admin permissions.

In addition, you can add Bitbucket Server credentials (in the form of username and password) to make it easier for users to set up Jenkins jobs. Users will be able to choose from these credentials to allow Jenkins to authenticate with Bitbucket Server and retrieve their projects.

<img src="images/addinstance.png" width="600"> <br/>

To add a Bitbucket Server instance:

1. In Jenkins go to **Jenkins** > **Manage Jenkins** > **Configure System**.
2. Under **Bitbucket Server plugin** click **Add a Bitbucket instance**.
3. Enter instance details.
4. Click **Save**.

## Creating a job

Once you’ve added a Bitbucket Server instance to Jenkins users will be able to select it when creating a job. This will make it easier for them to select the repo to be cloned. They’ll also be able to select the Bitbucket Server build trigger to automatically create a webhook.

<img src="images/createjob.png" width="600"> <br/>

To create a Jenkins job:
1. Select the **Source Code Management** tab.
2. Select **Bitbucket Server**.
3. Enter the details of the job.
4. Under **Build Trigger** select **Bitbucket Server Trigger**.
5. Add a **build step**.
6. Click **Save**.

---

## Contributing to the plugin

### Plugin development

This plugin uses [Apache Maven](http://maven.apache.org/) for development and releases. It also uses [Groovy](http://groovy-lang.org/) as part of the presentation layer for the plugin. To build Groovy files you need to [install the SDK](http://groovy-lang.org/download.html).

### Checkstyle

Follow the rules in `checkstyle.xml` by running checks using `mvn checkstyle:check`. We also recommend setting up a pre-commit hook to ensure you don't commit changes that violate the rules. A pre-commit hook already exists in `etc/git-hooks` and can be set up configuring the git hooks path.
```
git config core.hooksPath etc/git-hooks
```

Alternatively, you can link to the pre-commit hook directly:
```
ln -s -f ../../etc/git-hooks/pre-commit .git/hooks/pre-commit
```

### Building

To build the plugin run:
```
mvn package
```

### Running Jenkins with the plugin enabled

To run Jenkins with the plugin enabled you can spin up your Jenkins instance using `java -jar jenkins.war` in a directory that has the downloaded war-file. This enables running and testing in a real Jenkins instance.

To run Jenkins quickly during development you can also run `mvn hpi:run`. This will download and start the appropriate Jenkins version. The instance will be available on [http://localhost:8080/jenkins](http://localhost:8080/jenkins) and the logs will be in the invoking console

### Debugging

To start Jenkins (and Maven) in debug mode run:
```
mvnDebug hpi:run
```
Listening on port `8000`, it will wait for a debugger to attach before loading Jenkins and the plugin. Jenkins will then be available on [http://localhost:8080/jenkins](http://localhost:8080/jenkins) with logs in the invoking console.

You can then run Bitbucket Server using [AMPS](https://developer.atlassian.com/server/framework/atlassian-sdk/install-the-atlassian-sdk-on-a-linux-or-mac-system/) with the following command:
```
atlas-run-standalone --product bitbucket --version 6.5.0
```
This will start Bitbucket Server on [http://localhost:7990/bitbucket](http://localhost:7990/bitbucket).

### Running tests

Unit tests are run with the Surefire plugin using `mvn verify`. They can be skipped using ``-DskipTests`.

Integration tests are run under the `it` profile with the Failsafe plugin using `mvn verify -Pit`. The tests will start Bitbucket Server on [http://localhost:7990/bitbucket](http://localhost:7990/bitbucket) and stop it after they are complete.

---

## Changelog

### 1.0.3 (14 November 2019)
- Fix issue JENKINS-60116

### 1.0.2 (12 November 2019)
- Fix issues JENKINS-60128 and JENKINS-60127

### 1.0.1 (1 November 2019)
- Fix issue JENKINS-59578 - Changing server configuration does not update SCM configuration
- Migrate documentaiton from Wiki to Github

### 1.0 (25 October 2019)
- Fix issue JENKINS-59802 - problems editing Admin token
- Fix issue that Pipeline jobs did not post build status
- Be more forgiving when saving a project; saving as much as possible of the provided config
- Minor bugfixes

### 1.0-rc-1 (10 October 2019)
- First stable release candidate for the upcoming 1.0 release.
- Global Credentials and Admin token are now tracked.
- Few minor bug fixes.

### 1.0-beta-4 (8 October 2019)
- Bug fixes related to mirror cloning and pipeline.

### 1.0-beta-1 (27 Sep 2019)
- Jenkins Pipelines are now supported
- You can now clone from Bitbucket Server Smart Mirrors without modifying the clone URL

### 1.0-alpha-3 (18 Sep 2019)
- Webhooks are now created in Bitbucket Server automatically when configuring the SCM
- Bitbucket SCM storage fields have changed so will require re-creating jobs that use Bitbucket SCM
- Project and repo fields when configuring the SCM are now searchable dropdowns
- Build status is now posted to Bitbucket Server after starting and completing a build

### 1.0-alpha-2 (23 Aug 2019)
- Bugfix: Last entry in server configuration can now be removed
- Minor changes and fixes

### 1.0-alpha-1 (7 Aug 2019)
- First public release
