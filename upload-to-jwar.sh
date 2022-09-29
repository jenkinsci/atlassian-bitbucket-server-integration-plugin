#!/bin/bash
mvn package -Dmaven.test.skip=true
echo "Uploading plugin"
curl -kv http://localhost:8080/pluginManager/uploadPlugin -u admin:admin -F file=@target/atlassian-bitbucket-server-integration.hpi
echo "Restarting jenkins"
curl -kv http://localhost:8080/safeRestart -u admin:admin -F Submit=Yes
