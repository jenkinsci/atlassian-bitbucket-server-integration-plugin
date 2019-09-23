git checkout master
echo git remote get-url origin
git remote set-url origin http://localhost:7990/bitbucket/scm/project_1/rep_1.git
echo "Hello, World!" >> test.txt
git add test.txt
git commit -m "uniqueMessage"
git push