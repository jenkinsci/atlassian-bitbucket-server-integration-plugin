package com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentialsImpl

def f = namespace(lib.FormTagLib)

f.entry(title: _("bitbucket.admin.token"), field: "secret") {
    f.password(value: instance?.secret, placeholder: "This token must have project admin permissions")
}

f.entry(title: _("bitbucket.admin.token.description"), field: "description") {
    f.textbox(placeholder: "To help administrators identify this token")
}

f.invisibleEntry(field: "id") {
    f.input(type: "hidden", name: "id", value: "${instance?.id}")
}