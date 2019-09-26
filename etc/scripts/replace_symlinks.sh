#!/usr/bin/env bash
rm -rf src/main/resources/com/atlassian/bitbucket/jenkins/internal/scm/BitbucketSCMStep
mkdir src/main/resources/com/atlassian/bitbucket/jenkins/internal/scm/BitbucketSCMStep
cp -r src/main/resources/com/atlassian/bitbucket/jenkins/internal/scm/BitbucketSCM/* src/main/resources/com/atlassian/bitbucket/jenkins/internal/scm/BitbucketSCMStep/