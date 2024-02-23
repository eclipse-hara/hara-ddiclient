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
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.messagesOnSoftUpdateAuthorization
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.messagesOnSuccessfullyDownloadDistribution
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.targetRetrievedUpdateAction
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

class HawkbitDownloadOnlyDeploymentTest : AbstractDeploymentTest() {

    private var actionId: Int = 0
    override val targetId: String = "DownloadOnlyTest"

    companion object {
        const val DISTRIBUTION_ID = 3
    }

    @BeforeClass
    override fun beforeTest() {
        super.beforeTest()
        setPollingTime("00:00:05")
    }

    @Test(enabled = true, timeOut = 60_000, priority = 14)
    fun testDownloadOnlyWhileWaitingForUpdateAuthorization() = runBlocking {

        reCreateTestTargetOnServer()

        assignDownloadOnlyDistribution()

        val client = createHaraClientWithAuthorizationPermissions(
            downloadAllowed = false, updateAllowed = true)

        startTheTestAndWaitForResult(client,
            createTargetTestDeployment(expectedActionsAfterDownloadOnlyDeployment))
    }

    private suspend fun assignDownloadOnlyDistribution() {
        val distribution = HawkbitAssignDistributionBody(DISTRIBUTION_ID,
            AssignDistributionType.DOWNLOAD_ONLY, 0)
        actionId = assignDistributionToTheTarget(distribution)
    }

    private fun createTargetTestDeployment(
        actionsOnFinish: ActionStatus): TestUtils.TargetDeployments {
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
                    actionStatusOnFinish = actionsOnFinish,
                    filesDownloadedPairedWithServerFile = filesDownloadedPairedToServerFile
                )
            )
        )
    }

    private val expectedActionsAfterDownloadOnlyDeployment : ActionStatus =
        ActionStatus(setOf(
            *endMessagesOnSuccessUpdate,
            *messagesOnSoftUpdateAuthorization,
            *messagesOnSuccessfullyDownloadDistribution(
                TestUtils.md5OfFileNamed("test1"), targetId,
                "1", "test_1"),
            targetRetrievedUpdateAction,
            TestUtils.firstActionWithAssignmentEntry
        ))
}