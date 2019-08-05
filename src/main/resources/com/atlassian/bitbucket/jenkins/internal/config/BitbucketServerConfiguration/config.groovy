package com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration

def f = namespace(lib.FormTagLib)
def c = namespace(lib.CredentialsTagLib)


f.section() {
    f.entry(title: _("bitbucket.server.id"), field: "id") {
        f.readOnlyTextbox(clazz: "bbs-server-id-textbox", checkMethod: "post")
    }

    f.entry(title: _("bitbucket.server.name"), field: "serverName") {
        f.textbox(placeholder: "Descriptive name of server", checkMethod: "post")
    }

    f.entry(title: _("bitbucket.url"), field: "baseUrl") {
        f.textbox(placeholder: "http://your-bitbucket-server.org", checkMethod: "post")
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