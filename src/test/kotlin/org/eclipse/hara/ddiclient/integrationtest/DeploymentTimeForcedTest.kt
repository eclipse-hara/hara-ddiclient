/*
 * Copyright © 2017-2024  Kynetics, Inc.
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
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.messagesOnSoftDownloadAuthorization
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.waitingForDownloadAuthorizationMessage
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.waitingForUpdateAuthorizationMessage
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.seconds

class DeploymentTimeForcedTest : AbstractDeploymentTest() {

    private var actionId: Int = 0

    companion object {
        const val TARGET_ID: String = "TimeForceTest"
        const val DISTRIBUTION_ID = 3
    }

    @BeforeClass
    override fun beforeTest() {
        super.beforeTest()
        setPollingTime("00:00:10")
    }

    @Test(enabled = true, timeOut = 150_000, priority = 12)
    fun testTimeForcedUpdateWhileWaitingForDownloadAuthorization() = runBlocking {
        reCreateTestTargetOnServer(TARGET_ID)

        assignTimeForcedDistributionToTheTarget()

        val client = createHaraClientWithAuthorizationPermissions(
            TARGET_ID, downloadAllowed = false, updateAllowed = false)

        val deployment = createTargetTestDeployment(testingForUpdateAuthorization = false)

        startTheTestAndWaitForResult(client, deployment)

    }

    @Test(enabled = true, timeOut = 150_000, priority = 13)
    fun testTimeForcedUpdateWhileWaitingForUpdateAuthorization() = runBlocking {
        reCreateTestTargetOnServer(TARGET_ID)

        assignTimeForcedDistributionToTheTarget()

        val client = createHaraClientWithAuthorizationPermissions(
            TARGET_ID, downloadAllowed = true, updateAllowed = false)

        val deployment = createTargetTestDeployment(testingForUpdateAuthorization = true)

        startTheTestAndWaitForResult(client, deployment)
    }

    private fun createTargetTestDeployment(
        testingForUpdateAuthorization: Boolean): TestUtils.TargetDeployments {

        val filesDownloadedPairedToServerFile = setOf(
            TestUtils.pathResolver.fromArtifact(actionId.toString()).invoke(
                TestUtils.test1Artifact) to TestUtils.locationOfFileNamed("test1"))


        return TestUtils.TargetDeployments(
            targetId = TARGET_ID,
            targetToken = "",
            deploymentInfo = listOf(
                TestUtils.TargetDeployments.DeploymentInfo(
                    actionId = actionId,
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
                TestUtils.md5OfFileNamed("test1"), TARGET_ID,
                "1", "test_1"),
            *authorizationMessage,
            *TestUtils.firstActionsOnTargetDeployment
        ))
    }

    private suspend fun assignTimeForcedDistributionToTheTarget() {
        val timeForcedTime: Long = 10.seconds.inWholeMilliseconds
        val distribution = HawkbitAssignDistributionBody(
            DISTRIBUTION_ID, AssignDistributionType.TIME_FORCED,
            System.currentTimeMillis() + timeForcedTime)
        actionId = assignDistributionToTheTarget(TARGET_ID, distribution)
    }
}