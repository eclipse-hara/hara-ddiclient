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
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.md5OfFileNamed
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.pathResolver
import org.eclipse.hara.ddiclient.integrationtest.utils.logCurrentFunctionName
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

class DeploymentForcedAndSoftTest : AbstractDeploymentTest() {

    private val cancellingTestTargetId = "CancelActionTest"
    private val osOnlyTestTargetId = "OsOnlySoftwareModuleTest"
    private val osWithAppsForcedUpdateTestTargetId = "OsWithAppsForcedUpdateTest"
    private val osWithAppsSoftUpdateTestTargetId = "OsWithAppsSoftUpdateTest"

    @BeforeClass
    override fun beforeTest() {
        super.beforeTest()
        setPollingTime("00:00:10")
    }

    @Test(enabled = true, timeOut = 30_000, priority = 30)
    private fun testDeployingNewUpdateCancelsTheCurrentActiveOne() {
        logCurrentFunctionName()

        runBlocking {
            reCreateTestTargetOnServer(cancellingTestTargetId)

            val client = createHaraClientWithAuthorizationPermissions(
                cancellingTestTargetId, downloadAllowed = true, updateAllowed = true)

            val deploymentsInfo = mutableListOf<TestUtils.TargetDeployments.DeploymentInfo>()

            val deployment = TestUtils.TargetDeployments(
                targetId = cancellingTestTargetId,
                targetToken = "",
                deploymentInfo = deploymentsInfo
            )

            val actionId = assignCancellingDistributionToTheTarget()

            val cancelDeploymentInfo = getCancellingDeploymentInfo(actionId)

            deploymentsInfo.add(cancelDeploymentInfo)

            val updateDeploymentInfo =
                getSuccessfulUpdateDeploymentInfoAfterCancelling(actionId)

            deploymentsInfo.add(updateDeploymentInfo)

            assignSuccessfulUpdateDistributionToTheTarget()

            startTheTestAndWaitForResult(client, deployment)
        }
    }

    private suspend fun assignSuccessfulUpdateDistributionToTheTarget() {
        val distribution = HawkbitAssignDistributionBody(
            TestUtils.APP_DISTRIBUTION_ID, AssignDistributionType.FORCED, 0)
        assignDistributionToTheTarget(cancellingTestTargetId, distribution)
    }

    private suspend fun assignCancellingDistributionToTheTarget(): Int {
        val distribution = HawkbitAssignDistributionBody(
            TestUtils.OS_DISTRIBUTION_ID, AssignDistributionType.FORCED, 0)
        return assignDistributionToTheTarget(cancellingTestTargetId, distribution)
    }

    private fun getCancellingDeploymentInfo(
        actionId: Int): TestUtils.TargetDeployments.DeploymentInfo {
        val contentEntriesOnFinish = ActionStatus(
            setOf(
                ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.canceled,
                    listOf(
                        "Update Server: Cancellation confirmed.",
                        "Update Server: Cancellation completion is finished sucessfully."
                    )
                ),
                ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.retrieved,
                    listOf(
                        "Update Server: Target retrieved cancel action and should start now the cancellation.")
                ),
                ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.canceling,
                    listOf("Update Server: cancel obsolete action due to new update")
                ),
                TestUtils.firstActionWithAssignmentEntry
            )
        )

        return TestUtils.TargetDeployments.DeploymentInfo(
            actionId = actionId,
            actionStatusOnFinish = contentEntriesOnFinish,
            filesDownloadedPairedWithServerFile = emptySet()
        )
    }

    private fun getSuccessfulUpdateDeploymentInfoAfterCancelling(
        lastActionId: Int): TestUtils.TargetDeployments.DeploymentInfo {
        val contentEntriesOnFinish = ActionStatus(
            setOf(
                *endMessagesOnSuccessUpdate,
                *TestUtils.messagesOnSuccessfullyDownloadDistribution(
                    md5OfFileNamed("test1"), cancellingTestTargetId,
                    "1", "test_1"),
                *TestUtils.firstActionsOnTargetDeployment
            )
        )

        return TestUtils.TargetDeployments.DeploymentInfo(
            actionId = lastActionId + 1,
            actionStatusOnFinish = contentEntriesOnFinish,
            filesDownloadedPairedWithServerFile = emptySet()
        )
    }


    @Test(enabled = true, timeOut = 30_000, priority = 31)
    private fun testOsOnlySoftwareModuleUpdate() {
        logCurrentFunctionName()

        runBlocking {
            reCreateTestTargetOnServer(osOnlyTestTargetId)

            val client = createHaraClientWithAuthorizationPermissions(
                osOnlyTestTargetId, downloadAllowed = true, updateAllowed = true)

            val distribution = HawkbitAssignDistributionBody(
                TestUtils.OS_DISTRIBUTION_ID, AssignDistributionType.FORCED, 0)
            val actionId = assignDistributionToTheTarget(osOnlyTestTargetId, distribution)

            val contentEntriesOnFinish = ActionStatus(
                setOf(
                    *endMessagesOnSuccessUpdate,
                    *TestUtils.messagesOnSuccessfullyDownloadDistribution(
                        md5OfFileNamed("test4"), osOnlyTestTargetId,
                        "3", "test_4"),
                    *TestUtils.firstActionsOnTargetDeployment
                )
            )

            val filesDownloadedPairedToServerFile = setOf(
                pathResolver.fromArtifact(actionId.toString())
                    .invoke(TestUtils.test4Artifact) to TestUtils.locationOfFileNamed(
                    "test4"))

            val deployment = TestUtils.TargetDeployments(
                targetId = osOnlyTestTargetId,
                targetToken = "0fe7b8c9de2102ec6bf305b6f66df5b2",
                deploymentInfo = listOf(TestUtils.TargetDeployments.DeploymentInfo(
                    actionId = actionId,
                    actionStatusOnFinish = contentEntriesOnFinish,
                    filesDownloadedPairedWithServerFile = filesDownloadedPairedToServerFile

                ))
            )

            startTheTestAndWaitForResult(client, deployment)
        }
    }


    @Test(enabled = true, timeOut = 30_000, priority = 32)
    private fun testOsWithAppsSoftwareModuleUpdate() {
        logCurrentFunctionName()

        runBlocking {
            reCreateTestTargetOnServer(osWithAppsForcedUpdateTestTargetId)

            val client = createHaraClientWithAuthorizationPermissions(
                osWithAppsForcedUpdateTestTargetId, downloadAllowed = true,
                updateAllowed = true)

            val distribution = HawkbitAssignDistributionBody(
                TestUtils.OS_WITH_APPS_DISTRIBUTION_ID, AssignDistributionType.FORCED, 0)
            val actionId =
                assignDistributionToTheTarget(osWithAppsForcedUpdateTestTargetId, distribution)

            val contentEntriesOnFinish = ActionStatus(
                setOf(
                    *endMessagesOnSuccessUpdate,
                    *messagesOnSuccessfullyDownloadOsWithAppDistribution(
                        osWithAppsForcedUpdateTestTargetId),
                    *TestUtils.firstActionsOnTargetDeployment
                )
            )

            val deployment = TestUtils.TargetDeployments(
                targetId = osWithAppsForcedUpdateTestTargetId,
                targetToken = "4a28d893bb841def706073c789c0f3a7",
                deploymentInfo = listOf(TestUtils.TargetDeployments.DeploymentInfo(
                    actionId = actionId,
                    actionStatusOnFinish = contentEntriesOnFinish,
                    filesDownloadedPairedWithServerFile = filesDownloadedInOsWithAppsPairedToServerFile(
                        actionId)

                ))
            )

            startTheTestAndWaitForResult(client, deployment)
        }
    }

    @Test(enabled = true, timeOut = 30_000, priority = 33)
    private fun testOsWithAppsSoftUpdate() {
        logCurrentFunctionName()

        runBlocking {
            reCreateTestTargetOnServer(osWithAppsSoftUpdateTestTargetId)

            val client = createHaraClientWithAuthorizationPermissions(
                osWithAppsSoftUpdateTestTargetId, downloadAllowed = true, updateAllowed = true)

            val distribution = HawkbitAssignDistributionBody(
                TestUtils.OS_WITH_APPS_DISTRIBUTION_ID, AssignDistributionType.SOFT, 0)
            val actionId =
                assignDistributionToTheTarget(osWithAppsSoftUpdateTestTargetId, distribution)

            val contentEntriesOnFinish = ActionStatus(
                setOf(
                    *endMessagesOnSuccessUpdate,
                    *TestUtils.messagesOnSoftUpdateAuthorization,
                    *messagesOnSuccessfullyDownloadOsWithAppDistribution(
                        osWithAppsSoftUpdateTestTargetId),
                    *TestUtils.messagesOnSoftDownloadAuthorization,
                    *TestUtils.firstActionsOnTargetDeployment
                )
            )

            val deployment = TestUtils.TargetDeployments(
                targetId = osWithAppsSoftUpdateTestTargetId,
                targetToken = "",
                deploymentInfo = listOf(
                    TestUtils.TargetDeployments.DeploymentInfo(
                        actionId = actionId,
                        actionStatusOnFinish = contentEntriesOnFinish,
                        filesDownloadedPairedWithServerFile =
                        filesDownloadedInOsWithAppsPairedToServerFile(actionId)

                    ))
            )

            startTheTestAndWaitForResult(client, deployment)
        }
    }

    private fun messagesOnSuccessfullyDownloadOsWithAppDistribution(target: String) = arrayOf(
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf("Successfully downloaded all files")
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf(
                "Successfully downloaded file with md5 ${
                    md5OfFileNamed(
                        "test1"
                    )
                }"
            )
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf(
                "Successfully downloaded file with md5 ${
                    md5OfFileNamed(
                        "test2"
                    )
                }"
            )
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf(
                "Successfully downloaded file with md5 ${
                    md5OfFileNamed(
                        "test3"
                    )
                }"
            )
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf(
                "Successfully downloaded file with md5 ${
                    md5OfFileNamed(
                        "test4"
                    )
                }"
            )
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.download,
            listOf("Update Server: Target downloads /${TestUtils.tenantNameToLower}/controller/v1/$target/softwaremodules/2/artifacts/test_2")
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.download,
            listOf("Update Server: Target downloads /${TestUtils.tenantNameToLower}/controller/v1/$target/softwaremodules/2/artifacts/test_3")
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.download,
            listOf("Update Server: Target downloads /${TestUtils.tenantNameToLower}/controller/v1/$target/softwaremodules/1/artifacts/test_1")
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.download,
            listOf("Update Server: Target downloads /${TestUtils.tenantNameToLower}/controller/v1/$target/softwaremodules/3/artifacts/test_4")
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf("Start downloading 4 files")
        )
    )


    private fun filesDownloadedInOsWithAppsPairedToServerFile(action: Int) = setOf(
        pathResolver.fromArtifact(action.toString()).invoke(
            TestUtils.test1Artifact
        ) to TestUtils.locationOfFileNamed("test1"),
        pathResolver.fromArtifact(action.toString()).invoke(
            TestUtils.test2Artifact
        ) to TestUtils.locationOfFileNamed("test2"),
        pathResolver.fromArtifact(action.toString()).invoke(
            TestUtils.test3Artifact
        ) to TestUtils.locationOfFileNamed("test3"),
        pathResolver.fromArtifact(action.toString()).invoke(
            TestUtils.test4Artifact
        ) to TestUtils.locationOfFileNamed("test4"),
    )
}
