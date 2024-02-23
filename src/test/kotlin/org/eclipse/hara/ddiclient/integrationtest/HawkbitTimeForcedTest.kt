/*
 * Copyright © 2017-2024  Kynetics  LLC
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hara.ddiclient.integrationtest

import kotlinx.coroutines.runBlocking
import org.eclipse.hara.ddiclient.integrationtest.abstractions.AbstractDeploymentTest
import org.eclipse.hara.ddiclient.integrationtest.api.management.ActionStatus
import org.eclipse.hara.ddiclient.integrationtest.api.management.AssignDistributionType
import org.eclipse.hara.ddiclient.integrationtest.api.management.HawkbitAssignDistributionBody
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.endMessagesOnSuccessUpdate
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.firstActionWithAssignmentEntry
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.messagesOnSoftDownloadAuthorization
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.waitingForDownloadAuthorizationMessage
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.waitingForUpdateAuthorizationMessage
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.seconds

class HawkbitTimeForcedTest : AbstractDeploymentTest() {

    private var actionId: Int = 0
    override val targetId: String = "TimeForceTest"

    companion object {
        const val DISTRIBUTION_ID = 3
    }

    @BeforeClass
    override fun beforeTest() {
        super.beforeTest()
        setPollingTime("00:00:10")
    }

    @Test(enabled = true, timeOut = 150_000, priority = 12)
    fun testTimeForcedUpdateWhileWaitingForDownloadAuthorization() = runBlocking {
        reCreateTestTargetOnServer()

        assignTimeForcedDistributionToTheTarget()

        val client = createHaraClientWithAuthorizationPermissions(
            downloadAllowed = false, updateAllowed = false)

        val deployment = createTargetTestDeployment(testingForUpdateAuthorization = false)

        startTheTestAndWaitForResult(client, deployment)

    }

    @Test(enabled = true, timeOut = 150_000, priority = 13)
    fun testTimeForcedUpdateWhileWaitingForUpdateAuthorization() = runBlocking {
        reCreateTestTargetOnServer()

        assignTimeForcedDistributionToTheTarget()

        val client = createHaraClientWithAuthorizationPermissions(
            downloadAllowed = true, updateAllowed = false)

        val deployment = createTargetTestDeployment(testingForUpdateAuthorization = true)

        startTheTestAndWaitForResult(client, deployment)
    }

    private fun createTargetTestDeployment(
        testingForUpdateAuthorization: Boolean): TestUtils.TargetDeployments {

        val filesDownloadedPairedToServerFile = setOf(
            TestUtils.pathResolver.fromArtifact(actionId.toString()).invoke(
                TestUtils.test1Artifact) to TestUtils.locationOfFileNamed("test1"))


        return TestUtils.TargetDeployments(
            targetId = targetId,
            targetToken = "",
            deploymentInfo = listOf(
                TestUtils.TargetDeployments.DeploymentInfo(
                    actionId = actionId,
                    actionStatusOnStart = expectedActionOnStart,
                    actionStatusOnFinish = getExpectedActionsAfterTimeForceDeployment(
                        testingForUpdateAuthorization),
                    filesDownloadedPairedWithServerFile =
                    filesDownloadedPairedToServerFile
                )
            )
        )
    }

    private fun getExpectedActionsAfterTimeForceDeployment(
        testingForUpdateAuthorization: Boolean): ActionStatus {
        val authorizationMessage: Array<ActionStatus.ContentEntry> =
            if (testingForUpdateAuthorization) {
                mutableSetOf(*messagesOnSoftDownloadAuthorization).apply {
                    add(waitingForUpdateAuthorizationMessage)
                }.toTypedArray()
            } else {
                arrayOf(waitingForDownloadAuthorizationMessage)
            }
        return ActionStatus(setOf(
            *endMessagesOnSuccessUpdate,
            *TestUtils.messagesOnSuccessfullyDownloadDistribution(
                TestUtils.md5OfFileNamed("test1"), targetId,
                "1", "test_1"),
            *authorizationMessage,
            TestUtils.targetRetrievedUpdateAction,
            firstActionWithAssignmentEntry,
        ))
    }

    private suspend fun assignTimeForcedDistributionToTheTarget() {
        val timeForcedTime: Long = 10.seconds.inWholeMilliseconds
        val distribution = HawkbitAssignDistributionBody(
            DISTRIBUTION_ID, AssignDistributionType.TIME_FORCED,
            System.currentTimeMillis() + timeForcedTime)
        actionId = assignDistributionToTheTarget(distribution)
    }
}