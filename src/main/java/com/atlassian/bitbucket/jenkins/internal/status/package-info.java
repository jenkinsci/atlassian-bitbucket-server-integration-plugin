/**
 *
 * There are primarily 3 types of job we should focus on:
 *
 *  <ol>
 *    <li>Freestyle Job
 *    <li>Workflow, this is further characterized below as,
 *      <ol><li>Pipeline job
 *       <li>Multi branch pipeline job</ol>
 *  </ol>
 *
 *  <p>Pipeline and multibranch pipeline can have build steps mentioned in:
 *  <ol>
 *  <li> Inline groovy script
 *  <li> Fetched from Git repository (Jenkinsfile). This can be specified as either through Bitbucket SCM or through Git SCM.
 *  </ol>
 *  <p>There can be multiple SCM associated with a single job. We try our best to handle those. We skip posting build status in case we can't.</p>
 *
 *  In addition, a pipeline script can also specify Git url as well. Example,
 *  <pre>
 *  node {
 *   git url: 'https://github.com/joe_user/simple-maven-project-with-tests.git'
 *   ...
 * }
 * </pre>
 *
 * <p>We assume that for a build status to be posted, there needs to be some association with {@link com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM}
 * or {@link com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource}
 * This can be done in following ways. We will send the build status in all of these cases:
 * <ol>
 *     <li> Freestyle job has Bitbucket SCM. For simply GitSCM, we will not post any build status since we don't have credentials and server id.
 *     <li> Pipeline job has Bitbucket SCM to fetch jenkins file.
 *     <li> Pipeline job has Bitbucket SCM to fetch jenkins file. Jenkins file has bb_checkout step mentioned.
 *     <li> Pipeline job has Git SCM to fetch jenkins file. Jenkins file has bb_checkout step mentioned.
 *     <li> Multi branch pipeline has Bitbucket SCM for branch scanning.
 *     <li> Multi branch pipeline has Bitbucket SCM for branch scanning and bb_checkout step mentioned in Jenkinsfile.
 *     <li> Multi branch pipeline has Git SCM and has bb_checkout step mentioned in Jenkinsfile.
 * </ol>
 *
 * Some of the things that should also be kept in mind are:
 * <ol>
 *     <li> Workflow job has an option of lightweight checkout. This is to fetch Jenkinsfile. This is not a representation of build being run.</li>
 * </ol>
 *
 * Overall workflow of sending build status is as follows:
 * <ol>
 *     <li>We add SCM Listener {@link com.atlassian.bitbucket.jenkins.internal.status.LocalSCMListener} which listens for checkouts</li>
 *     <li>On a checkout completion, we check association with {@link com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM} or {@link com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource}</li>
 *     <li>We attach a {@link com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction} for storing the checkout context.</li>
 *     <li>We send an In progress build status</li>
 *     <li>We add a Run listener {@link com.atlassian.bitbucket.jenkins.internal.status.BuildStatusPoster} which listens for builds</li>
 *     <li>On Build completion, we retrieve the {@code BitbucketRevisionAction} and send build status to Bitbucket.</li>
 * </ol>
 *
 *
 * Add package level annotations to indicate everything is non-null by default.
 */
@ParametersAreNonnullByDefault
@ReturnValuesAreNonnullByDefault
package com.atlassian.bitbucket.jenkins.internal.status;

import edu.umd.cs.findbugs.annotations.ReturnValuesAreNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
