package com.atlassian.bitbucket.jenkins.internal.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * Indicates that the class in question is not subject to upgrade concerns. This means it is not part of a job in any
 * shape or form. It is not persisted in any way. Examples include Actions that displays a help link.
 * @since 3.0.0
 */
@Target({TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotUpgradeSensitive {

}
