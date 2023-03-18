/*
 * Copyright © 2017-2023  Kynetics  LLC
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
import org.testng.Assert.*
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
    // Test that the dataProvider method returns an array of TargetDeployments:
    @Test
    fun testDataProviderReturnsArrayOfTargetDeployments() {
        val testObject = SuccessfulSoftUpdateWithDownloadAndUpdateAlwaysAllowed()
        val dataProviderResult = testObject.dataProvider()
        assertNotNull(dataProviderResult)
        assertTrue(dataProviderResult.isNotEmpty())
        assertTrue(dataProviderResult.all { it is TestUtils.TargetDeployments })
    }
    // Test that an exception is thrown if the TargetDeployments parameter in the test method has an empty deploymentInfo list:
    @Test
    fun testMethodThrowsExceptionIfDeploymentInfoIsEmpty() {
        val testObject = SuccessfulSoftUpdateWithDownloadAndUpdateAlwaysAllowed()
        val targetDeployments = TestUtils.TargetDeployments("testTarget", "testToken", emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            testObject.test(targetDeployments)
        }
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
