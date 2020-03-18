package com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource

def f = namespace(lib.FormTagLib)
def c = namespace(lib.CredentialsTagLib)
def s = namespace(jenkins.scm.api.FormTagLib)

f.section() {

    f.entry(title: _("bitbucket.scm.credentials"), field: "credentialsId") {
        c.select(context: app, includeUser: false, expressionAllowed: false, checkMethod: "post")
    }

    f.entry(title: _("bitbucket.scm.server"), field: "serverId") {
        f.select(context: app, checkMethod: "post")
    }

    f.entry(title: _("bitbucket.scm.projectName"), field: "projectName") {
        f.combobox(context: app, placeholder: "Start typing to find a project or click help to see how to find a personal project", checkMethod: "post", clazz:'searchable')
    }

    f.entry(title: _("bitbucket.scm.repositoryName"), field: "repositoryName") {
        f.combobox(context: app, placeholder: "Start typing to find a repository or click help to see how to find a personal repository", checkMethod: "post", clazz:'searchable')
    }

    f.entry(title: _("bitbucket.scm.mirror"), field: "mirrorName") {
        f.select(checkMethod: "post")
    }

    f.entry(title: _("Behaviours")) {
        s.traits(field:"traits")
    }

    if (instance != null && instance.id != null) {
        f.invisibleEntry(field: "id") {
            f.input(type: "hidden", name: "id", value: "${instance.id}")
        }
    }

    f.block() {
        f.validateButton(
                title: _("bitbucket.scm.test.connection"),
                progress: _("bitbucket.scm.test.connection"),
                method: "testConnection",
                with: "credentialsId,serverId,projectName,repositoryName,mirrorName"
        )
    }

    script(src:"${rootURL}${h.getResourcePath()}/plugin/atlassian-bitbucket-server-integration/js/searchableField.js")
}
