package com.atlassian.bitbucket.jenkins.internal.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;

/**
 * Indicates that the field in question has been evaluated and the change is safe for upgrade.
 * @since 3.0.0
 */
@Target({FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UpgradeHandled {

    /**
     * Document with a brief sentence how the upgrade was handled.
     */
    String handledBy();

    /**
     * Version where this annotation (but <b>NOT</b> the upgrade handling) can safely be removed. This
     * is typically when the current version is released.
     */
    String removeAnnotationInVersion();
}
