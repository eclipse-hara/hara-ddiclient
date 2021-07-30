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
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.messagesOnSuccessfullyDownloadOsWithAppDistribution
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.startMessagesOnUpdateFond
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

class SuccessfulSoftUpdateWithDownloadAndUpdateAlwaysAllowed : AbstractClientTest() {

    @DataProvider(name = "targetUpdateProvider")
    fun dataProvider(): Array<TestUtils.TargetDeployments> {
        return arrayOf(target4ApplyOsWithAppsUpdate())
    }

    @Test(enabled = true, dataProvider = "targetUpdateProvider")
    fun test(targetDeployments: TestUtils.TargetDeployments) {
        testTemplate(targetDeployments)
    }

    private fun target4ApplyOsWithAppsUpdate(): TestUtils.TargetDeployments {
        val targetId = "Target4"
        val actionId = 5
        val contentEntriesOnFinish = ActionStatus(
            setOf(
                *endMessagesOnSuccessUpdate,
                *messagesOnSuccessfullyDownloadOsWithAppDistribution(targetId),
                *startMessagesOnUpdateFond
            )
        )
        return TestUtils.TargetDeployments(
            targetId = targetId,
            targetToken = "",
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
