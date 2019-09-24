git checkout master
git remote -v
git --version
REMOTE=`git remote`
git remote set-url $REMOTE http://admin:admin@localhost:7990/bitbucket/scm/project_1/$REMOTE.git
echo "Hello, World!" >> test.txt
git add test.txt
git commit -m "uniqueMessage"
GIT_TRACE=1 GIT_CURL_VERBOSE=1 git push