package com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration

def f = namespace(lib.FormTagLib)
def c = namespace(lib.CredentialsTagLib)

f.section() {
    if (instance != null && instance.id != null) {
        f.invisibleEntry(field: "id") {
            f.input(type: "hidden", name: "id", value: "${instance.id}");
        }
    }

    f.entry(title: _("bitbucket.server.name"), field: "serverName") {
        f.textbox(placeholder: "To help your users identify this instance", checkMethod: "post")
    }

    f.entry(title: _("bitbucket.url"), field: "baseUrl") {
        f.textbox(placeholder: "E.g. https://bitbucketserver.mycompany.com", checkMethod: "post")
    }

    f.entry(title: _("bitbucket.admin.credentials"), field: "adminCredentialsId") {
        c.select(context: app, includeUser: false, expressionAllowed: false, checkMethod: "post")
    }

    f.entry(title: _("bitbucket.credentials"), field: "credentialsId") {
        c.select(context: app, includeUser: false, expressionAllowed: false, checkMethod: "post")
    }

    f.block() {
        f.validateButton(
                title: _("bitbucket.test.connection"),
                progress: _("bitbucket.test.connection"),
                method: "testConnection",
                with: "adminCredentialsId,baseUrl,credentialsId"
        )
    }
}