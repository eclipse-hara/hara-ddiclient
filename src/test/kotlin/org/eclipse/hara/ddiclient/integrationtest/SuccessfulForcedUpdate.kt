/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/
package org.eclipse.hara.ddiclient.integrationtest

import org.eclipse.hara.ddiclient.integrationtest.TestUtils.defaultActionStatusOnStart
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.endMessagesOnSuccessUpdate
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.filesDownloadedInOsWithAppsPairedToServerFile
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.firstActionEntry
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.locationOfFileNamed
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.messagesOnSuccefullyDownloadAppDistribution
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.messagesOnSuccefullyDownloadOsDistribution
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.messagesOnSuccessfullyDownloadOsWithAppDistribution
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.pathResolver
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.startMessagesOnUpdateFond
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.test1Artifact
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.test4Artifact
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

class SuccessfulForcedUpdate : AbstractClientTest() {

    @DataProvider(name = "targetUpdateProvider")
    fun dataProvider(): Array<TestUtils.TargetDeployments> {
        return arrayOf(target1AcceptFirstCancelRequestThenApplyAppUpdate(),
                target2ApplyOsUpdate(),
                target3ApplyOsWithAppsUpdate())
    }

    @Test(enabled = true, dataProvider = "targetUpdateProvider")
    fun test(targetDeployments: TestUtils.TargetDeployments) {
        testTemplate(targetDeployments)
    }

    private fun target1AcceptFirstCancelRequestThenApplyAppUpdate(): TestUtils.TargetDeployments {
        val targetId = "target1"
        val contentEntriesOnFinish2 = ActionStatus(
            setOf(
                *endMessagesOnSuccessUpdate,
                *messagesOnSuccefullyDownloadAppDistribution(targetId),
                *startMessagesOnUpdateFond
            )
        )

        val actionStatusOnStart1 = ActionStatus(
            setOf(
                ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.canceling,
                    listOf("Update Server: cancel obsolete action due to new update")
                ),
                firstActionEntry
            )
        )

        val contentEntriesOnFinish1 = ActionStatus(
            setOf(
                ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.canceled,
                    listOf(
                        "Update Server: Cancelation confirmed.",
                        "Update Server: Cancellation completion is finished sucessfully."
                    )
                ),
                ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.retrieved,
                    listOf("Update Server: Target retrieved cancel action and should start now the cancelation.")
                ),
                ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.canceling,
                    listOf("Update Server: cancel obsolete action due to new update")
                ),
                firstActionEntry
            )
        )

        val filesDownloadedPairedToServerFile = setOf(pathResolver.fromArtifact("2").invoke(test1Artifact) to locationOfFileNamed("test1"))

        return TestUtils.TargetDeployments(
                targetId = targetId,
                targetToken = "4a28d893bb841def706073c789c0f3a7",
                deploymentInfo = listOf(
                        TestUtils.TargetDeployments.DeploymentInfo(
                                actionId = 1,
                                actionStatusOnStart = actionStatusOnStart1,
                                actionStatusOnFinish = contentEntriesOnFinish1,
                                filesDownloadedPairedWithServerFile = emptySet()
                        ),
                        TestUtils.TargetDeployments.DeploymentInfo(
                                actionId = 2,
                                actionStatusOnStart = defaultActionStatusOnStart,
                                actionStatusOnFinish = contentEntriesOnFinish2,
                                filesDownloadedPairedWithServerFile = filesDownloadedPairedToServerFile
                        )
                )
        )
    }

    private fun target2ApplyOsUpdate(): TestUtils.TargetDeployments {
        val targetId = "target2"

        val contentEntriesOnFinish = ActionStatus(
            setOf(
                *endMessagesOnSuccessUpdate,
                *messagesOnSuccefullyDownloadOsDistribution(targetId),
                *startMessagesOnUpdateFond
            )
        )

        val filesDownloadedPairedToServerFile = setOf(pathResolver.fromArtifact("3").invoke(test4Artifact) to locationOfFileNamed("test4"))

        return TestUtils.TargetDeployments(
                targetId = targetId,
                targetToken = "0fe7b8c9de2102ec6bf305b6f66df5b2",
                deploymentInfo = listOf(
                        TestUtils.TargetDeployments.DeploymentInfo(
                                actionId = 3,
                                actionStatusOnStart = defaultActionStatusOnStart,
                                actionStatusOnFinish = contentEntriesOnFinish,
                                filesDownloadedPairedWithServerFile = filesDownloadedPairedToServerFile

                        )
                )
        )
    }

    private fun target3ApplyOsWithAppsUpdate(): TestUtils.TargetDeployments {
        val targetId = "target3"
        val actionId = 4
        val contentEntriesOnFinish = ActionStatus(
            setOf(
                *endMessagesOnSuccessUpdate,
                *messagesOnSuccessfullyDownloadOsWithAppDistribution(targetId),
                *startMessagesOnUpdateFond
            )
        )
        return TestUtils.TargetDeployments(
                targetId = targetId,
                targetToken = "4a28d893bb841def706073c789c0f3a7",
                deploymentInfo = listOf(
                        TestUtils.TargetDeployments.DeploymentInfo(
                                actionId = actionId,
                                actionStatusOnStart = defaultActionStatusOnStart,
                                actionStatusOnFinish = contentEntriesOnFinish,
                                filesDownloadedPairedWithServerFile = filesDownloadedInOsWithAppsPairedToServerFile(actionId)

                        )
                )
        )
    }
}
