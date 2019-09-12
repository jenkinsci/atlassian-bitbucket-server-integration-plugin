# Bitbucket Server and Jenkins integration 
[![Build Status](https://ci.jenkins.io/job/Plugins/job/atlassian-bitbucket/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/atlassian-bitbucket-integration/job/master/)

## Introduction
This plugin makes it easier to connect up your Jenkins instance with [Bitbucket Server](https://www.atlassian.com/software/bitbucket/enterprise/data-center)

Configure the connection in a few simple steps, the integration will then take care of creating webhooks to trigger builds and
post the build status back to Bitbucket

Currently development is in alpha-stage so the plugin is not fully functional yet.

## Feature overview
This plugin adds a Bitbucket Server SCM that can be selected when configuring a Job.
When configuring a job using the Bitbucket Server SCM, the plugin will make it easier for you to configure your repository.
This plugin also adds a Bitbucket Server trigger. This will create a webhook against Bitbucket Server which will trigger
the job on relevant pushes.

##### Adding a Bitbucket Server
Each Bitbucket Server instance has to be added and configured at system configuration level.
Once it is configured, it can be

Here are the steps to set up a Bitbucket Server instance
1. Manage Jenkins > Configure System > Add Bitbucket Server
    1. Add a server name
    2. Add a server url
    3. Create a new project admin token by selecting the 'Bitbucket Admin token' type
    4. Create a username with password credential and select that for credentials
    5. Click save

##### Configuring a job
Using the Bitbucket Server SCM when configuring your job will make it easier to select the repository that is to be
cloned, and using the Bitbucket Server trigger will automatically create a webhook against Bitbucket.

Here is an example for creating a new Bitbucket Server job:
1. Click 'Create new job'
    1. Name the job and select 'Freestyle Project' then click Next
    2. In 'Source code management' select Bitbucket Server
    3. Select the Bitbucket Server Trigger
    4. Add a build step
    5. Click save

##### Limitations
The plugin is still in the early stages of development so it does not yet support all of the features described here.
Additional features will also be made available.

## Development
This plugin uses [Apache Maven](http://maven.apache.org) for development and releases.

This plugin also makes use of [Groovy](http://groovy-lang.org) as part of the presentation layer for the plugin.
Building Groovy files requires the SDK to be installed: [get it here](http://groovy-lang.org/download.html)

##### Checkstyle
Please follow the rules in `checkstyle.xml`. You can run the checks using `mvn checkstyle:check`. We also recommend
setting up a pre-commit hook to ensure you don't commit changes that violate the rules. A pre-commit hook already exists
in `etc/git-hooks` and can be set up configuring the git hooks path:
```bash
git config core.hooksPath etc/git-hooks
```
or by linking the pre-commit hook directly:
```
ln -s -f ../../etc/git-hooks/pre-commit .git/hooks/pre-commit
```

##### Building
To build the plugin simply run `mvn package`

##### Running Jenkins with the plugin enabled
There are several ways to do this, you can spin up your Jenkins instance using `java -jar jenkins.war` in a directory 
that has the downloaded war-file. This enables running and testing in a real Jenkins instance.

A quick way during development is to run `mvn hpi:run` this will download and start the appropriate Jenkins version. 
The instance is then available on [http://localhost:8080/jenkins](http:localhost:8080/jenkins) logs end up in the 
invoking console

##### Debugging
To start Jenkins in debug mode run:
```
mvnDebug hpi:run
```
This will start Jenkins (and Maven) in debug mode, listening on port `8000` it will wait for a debugger to attach before 
proceeding with loading Jenkins and the plugin.
Just as under the run section above, Jenkins will be available on [http://localhost:8080/jenkins](http:localhost:8080/jenkins)
with logs in the invoking console.

Run Bitbucket Server using [AMPS](https://developer.atlassian.com/server/framework/atlassian-sdk/install-the-atlassian-sdk-on-a-linux-or-mac-system/) with the following command:
```
atlas-run-standalone --product bitbucket --version 6.5.0
```
This will start Bitbucket Server on [http://localhost:7990/bitbucket]().

##### Running tests

Unit tests are run with the Surefire plugin using `mvn verify`. They can be skipped using `-DskipTests`.

Integration tests are run under the `it` profile with the Failsafe plugin using `mvn verify -Pit`.
The integration tests will start Bitbucket Server on [http://localhost:7990/bitbucket]() and stop it after the tests are complete.
