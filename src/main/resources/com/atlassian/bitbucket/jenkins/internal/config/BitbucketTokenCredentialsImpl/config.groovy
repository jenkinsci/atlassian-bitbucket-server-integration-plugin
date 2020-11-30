package com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentialsImpl

def f = namespace(lib.FormTagLib)

f.entry(title: _("bitbucket.admin.token"), field: "secret") {
    f.password(value: instance?.secret, placeholder: "This token must have project admin permissions")
}

f.entry(title: _("bitbucket.admin.token.id"), field: "id") {
    f.textbox(value: instance?.id, placeholder: "Enter a unique ID for this token or leave blank to auto-generate")
}

f.entry(title: _("bitbucket.admin.token.description"), field: "description") {
    f.textbox(placeholder: "To help administrators identify this token")
}
