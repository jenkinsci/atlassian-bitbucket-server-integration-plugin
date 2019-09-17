# Bitbucket Server integration
[![Build Status](https://ci.jenkins.io/job/Plugins/job/atlassian-bitbucket/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/atlassian-bitbucket-integration/job/master/)

_Note: This plugin is currently in alpha. It should not be used in a production environment. It is an [experimental plugin release](https://jenkins.io/doc/developer/publishing/releasing-experimental-updates/) that is for feedback purposes only._

The Bitbucket Server integration plugin is the easiest way to connect [Jenkins](http://jenkins.io/) to [Bitbucket Server](https://www.atlassian.com/software/bitbucket/enterprise/data-center). With a few simple steps you can configure it to automatically create webhooks in Bitbucket to trigger Jenkins builds, allow Jenkins to clone/fetch from Bitbucket to run the builds, and then post the build statuses back to Bitbucket. It streamlines this entire process, removing the need for multiple plugins to achieve the same workflow.

The plugin enables this in two ways. It adds a Bitbucket Server Source Code Manager (SCM) to Jenkins, making it easy to set up a connection to a Bitbucket Server repository when setting up a Jenkins job. It also adds a build trigger to Jenkins that automatically creates a webhook against Bitbucket Server that triggers the Jenkins job on relevant pushes.

For information about upcoming features and using the plugin see the [wiki](https://wiki.jenkins.io/display/JENKINS/Bitbucket+Server+integration+plugin+for+Jenkins).

## Plugin development

This plugin uses [Apache Maven](http://maven.apache.org/) for development and releases. It also uses [Groovy](http://groovy-lang.org/) as part of the presentation layer for the plugin. To build Groovy files you need to [install the SDK](http://groovy-lang.org/download.html).

## Checkstyle

Follow the rules in `checkstyle.xml` by running checks using `mvn checkstyle:check`. We also recommend setting up a pre-commit hook to ensure you don't commit changes that violate the rules. A pre-commit hook already exists in `etc/git-hooks` and can be set up configuring the git hooks path.
```
git config core.hooksPath etc/git-hooks
```

Alternatively, you can link to the pre-commit hook directly:
```
ln -s -f ../../etc/git-hooks/pre-commit .git/hooks/pre-commit
```

## Building

To build the plugin run:
```
mvn package
```

## Running Jenkins with the plugin enabled

To run Jenkins with the plugin enabled you can spin up your Jenkins instance using `java -jar jenkins.war` in a directory that has the downloaded war-file. This enables running and testing in a real Jenkins instance.

To run Jenkins quickly during development you can also run `mvn hpi:run`. This will download and start the appropriate Jenkins version. The instance will be available on [http://localhost:8080/jenkins]() and the logs will be in the invoking console

## Debugging

To start Jenkins (and Maven) in debug mode run:
```
mvnDebug hpi:run
```
Listening on port `8000`, it will wait for a debugger to attach before loading Jenkins and the plugin. Jenkins will then be available on http://localhost:8080/jenkins with logs in the invoking console.

You can then run Bitbucket Server using [AMPS](https://developer.atlassian.com/server/framework/atlassian-sdk/install-the-atlassian-sdk-on-a-linux-or-mac-system/) with the following command:
```
atlas-run-standalone --product bitbucket --version 6.5.0
```
This will start Bitbucket Server on [http://localhost:7990/bitbucket]().

## Running tests

Unit tests are run with the Surefire plugin using `mvn verify`. They can be skipped using ``-DskipTests`.

Integration tests are run under the `it` profile with the Failsafe plugin using `mvn verify -Pit`. The tests will start
Bitbucket Server on [http://localhost:7990/bitbucket]() and stop it after they are complete.
