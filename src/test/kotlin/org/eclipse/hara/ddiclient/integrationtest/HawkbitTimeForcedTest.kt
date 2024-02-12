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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.eclipse.hara.ddiclient.api.ConfigDataProvider
import org.eclipse.hara.ddiclient.api.DeploymentPermitProvider
import org.eclipse.hara.ddiclient.api.DirectoryForArtifactsProvider
import org.eclipse.hara.ddiclient.api.DownloadBehavior
import org.eclipse.hara.ddiclient.api.HaraClient
import org.eclipse.hara.ddiclient.api.HaraClientData
import org.eclipse.hara.ddiclient.api.HaraClientDefaultImpl
import org.eclipse.hara.ddiclient.api.MessageListener
import org.eclipse.hara.ddiclient.api.Updater
import org.eclipse.hara.ddiclient.integrationtest.api.management.Action
import org.eclipse.hara.ddiclient.integrationtest.api.management.ActionStatus
import org.eclipse.hara.ddiclient.integrationtest.api.management.AssignDistributionType
import org.eclipse.hara.ddiclient.integrationtest.api.management.HawkbitAssignDistributionBody
import org.eclipse.hara.ddiclient.integrationtest.api.management.HawkbitTargetInfo
import org.eclipse.hara.ddiclient.integrationtest.api.management.ManagementApi
import org.eclipse.hara.ddiclient.integrationtest.api.management.ManagementClient
import org.eclipse.hara.ddiclient.integrationtest.api.management.ServerSystemConfig
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.basic
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.endMessagesOnSuccessUpdate
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.firstActionWithAssignmentEntry
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.messagesOnSoftDownloadAuthorization
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.waitingForDownloadAuthorizationMessage
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.waitingForUpdateAuthorizationMessage
import org.eclipse.hara.ddiclient.integrationtest.utils.addOkhttpLogger
import org.eclipse.hara.ddiclient.integrationtest.utils.internalLog
import org.testng.Assert
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test
import java.io.File
import java.lang.Exception
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class HawkbitTimeForcedTest {

    private lateinit var managementApi: ManagementApi
    private var actionId: Int = 22


    private var assertServerActionsScope = CoroutineScope(Dispatchers.IO)
    private var assertServerActionsOnCompleteJob: Deferred<Unit>? = null
    private var testScope = CoroutineScope(Dispatchers.Default)

    private val throwableScope = CoroutineScope(Dispatchers.Default)
    private var throwableJob: Deferred<Unit>? = null


    companion object {
        const val TARGET_ID = "TimeForceTest"
        const val DISTRIBUTION_ID = 3
    }

    @BeforeTest
    fun setup() = runBlocking {
        managementApi = ManagementClient.newInstance(TestUtils.hawkbitUrl)
        runBlocking {
            managementApi.setPollingTime(basic, ServerSystemConfig("00:00:10"))
        }
    }

    private fun defaultClientFromTargetId(
        directoryDataProvider: DirectoryForArtifactsProvider = TestUtils.directoryDataProvider,
        configDataProvider: ConfigDataProvider = TestUtils.configDataProvider,
        updater: Updater = TestUtils.updater,
        messageListeners: List<MessageListener> = emptyList(),
        deploymentPermitProvider: DeploymentPermitProvider = object :
            DeploymentPermitProvider {},
        downloadBehavior: DownloadBehavior = TestUtils.downloadBehavior
    ): (String) -> HaraClient = { targetId ->
        val clientData = HaraClientData(
            TestUtils.tenantName,
            targetId,
            TestUtils.hawkbitUrl,
            TestUtils.gatewayToken)

        val client = HaraClientDefaultImpl()

        val eventListener = object : MessageListener {
            override fun onMessage(message: MessageListener.Message) {
                "Message received: $message".internalLog()
                when (message) {

                    is MessageListener.Message.Event.UpdateFinished,
                    MessageListener.Message.State.CancellingUpdate -> {
                        testScope.launch {
                            try {
                                assertServerActionsOnCompleteJob?.await()
                            } catch (ignored: CancellationException) {
                            }
                        }
                    }

                    else -> {
                    }
                }
            }
        }

        client.init(
            clientData,
            directoryDataProvider,
            configDataProvider,
            deploymentPermitProvider,
            listOf(eventListener, *messageListeners.toTypedArray()),
            listOf(updater),
            downloadBehavior,
            httpBuilder = OkHttpClient.Builder().addOkhttpLogger()
        )
        client
    }

    @Test(enabled = true, timeOut = 150_000)
    fun testTimeForcedUpdateWhileWaitingForDownloadAuthorization() = runBlocking {
        reCreateTestTargetOnServer()

        assignDistributionToTheTarget()

        val client = createHaraClientWithPermissions(downloadAllowed = false)

        val deployment = createTargetTestDeployment(testingForUpdateAuthorization = false)

        startTheTestAndWaitForResult(client, deployment)

    }

    @Test(enabled = true, timeOut = 150_000)
    fun testTimeForcedUpdateWhileWaitingForUpdateAuthorization() = runBlocking {
        reCreateTestTargetOnServer()

        assignDistributionToTheTarget()

        val client = createHaraClientWithPermissions(downloadAllowed = true)

        val deployment = createTargetTestDeployment(testingForUpdateAuthorization = true)

        startTheTestAndWaitForResult(client, deployment)
    }

    private fun createHaraClientWithPermissions(
        downloadAllowed: Boolean = false): HaraClient {

        val deploymentBehavior = object : DeploymentPermitProvider {
            override fun downloadAllowed() = CompletableDeferred(downloadAllowed)
            override fun updateAllowed() = CompletableDeferred(false)
        }
        return defaultClientFromTargetId(
            deploymentPermitProvider = deploymentBehavior).invoke(TARGET_ID)
    }

    private suspend fun startTheTestAndWaitForResult(client: HaraClient,
                                                     deployment: TestUtils.TargetDeployments) {
        client.startAsync()
        assertServerActionsOnCompleteJob = assertServerActionsOnComplete(client, deployment)

        testScope.async {
            while (assertServerActionsOnCompleteJob?.isCompleted == false) {
                delay(1.seconds)
            }
        }.await()
    }

    private fun createTargetTestDeployment(
        testingForUpdateAuthorization: Boolean): TestUtils.TargetDeployments {

        val authorizationMessage: Array<ActionStatus.ContentEntry> = if (testingForUpdateAuthorization) {
            mutableSetOf(*messagesOnSoftDownloadAuthorization).apply {
                add(waitingForUpdateAuthorizationMessage)
            }.toTypedArray()
        } else {
            arrayOf(waitingForDownloadAuthorizationMessage)
        }

        val actionsOnFinish = ActionStatus(setOf(
            *endMessagesOnSuccessUpdate,
            *messagesOnSuccessfullyDownloadTimeForceDistribution,
            ActionStatus.ContentEntry(
                ActionStatus.ContentEntry.Type.retrieved,
                listOf(
                    "Update Server: Target retrieved update action and should start now the download.")
            ),
            *authorizationMessage,
            firstActionWithAssignmentEntry,
        ))

        val filesDownloadedPairedToServerFile = setOf(
            TestUtils.pathResolver.fromArtifact(actionId.toString()).invoke(
            TestUtils.test1Artifact) to TestUtils.locationOfFileNamed("test1"))


        return TestUtils.TargetDeployments(
            targetId = TARGET_ID,
            targetToken = "",
            deploymentInfo = listOf(
                TestUtils.TargetDeployments.DeploymentInfo(
                    actionId = actionId,
                    actionStatusOnStart = ActionStatus(
                        setOf(
                            firstActionWithAssignmentEntry
                        )),
                    actionStatusOnFinish = actionsOnFinish,
                    filesDownloadedPairedWithServerFile =
                    filesDownloadedPairedToServerFile
                )
            )
        )
    }

    private suspend fun reCreateTestTargetOnServer() {
        runCatching {
            managementApi.deleteTarget(basic, TARGET_ID)
        }
        runCatching {
            managementApi.createTarget(basic, listOf(HawkbitTargetInfo(TARGET_ID)))
        }
    }

    private suspend fun assignDistributionToTheTarget() {
        val timeForcedTime: Long = 10.seconds.inWholeMilliseconds
        val distributionBody = HawkbitAssignDistributionBody(
            DISTRIBUTION_ID, AssignDistributionType.TIME_FORCED,
            System.currentTimeMillis() + timeForcedTime)
        val response =
            managementApi.assignDistributionToTarget(basic, TARGET_ID, distributionBody)
        if (response.assignedActions.isNotEmpty()) {
            actionId = response.assignedActions.first().id
        }
    }

    private fun assertServerActionsOnComplete(
        client: HaraClient,
        deployment: TestUtils.TargetDeployments): Deferred<Unit> {
        return assertServerActionsScope.async(start = CoroutineStart.LAZY) {
            val deploymentInfo = deployment.deploymentInfo.first()
            while (managementApi.getActionAsync(basic, deployment.targetId,
                    deploymentInfo.actionId).status != Action.Status.finished
            ) {
                delay(5.seconds)
            }

            val actionStatus =
                managementApi.getTargetActionStatusAsync(basic, deployment.targetId,
                    deploymentInfo.actionId)
            assertEquals(actionStatus.content,
                deploymentInfo.actionStatusOnFinish.content)

            deploymentInfo.filesDownloadedPairedWithServerFile.forEach { (fileDownloaded, serverFile) ->
                assertEquals(File(fileDownloaded).readText(),
                    File(serverFile).readText())
                File(fileDownloaded).deleteRecursively()
            }

            client.safeStopClient()
        }
    }

    private suspend fun assertEquals(actual: Any?, expected: Any?) {
        throwableJob = throwableScope.async {
            Assert.assertEquals(actual, expected)
        }
        try {
            throwableJob?.await()
        } catch (ignored: CancellationException) {
        }
    }

    private val messagesOnSuccessfullyDownloadTimeForceDistribution = arrayOf(
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf("Successfully downloaded all files")
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf(
                "Successfully downloaded file with md5 ${
                    TestUtils.md5OfFileNamed("test1")
                }"
            )
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.download,
            listOf(
                "Update Server: Target downloads /${TestUtils.tenantNameToLower}/controller/v1/$TARGET_ID/softwaremodules/1/artifacts/test_1")
        ),
        ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf("Start downloading 1 files")
        )
    )


    private fun HaraClient.safeStopClient() {
        try {
            stop()
        } catch (ignored: Exception) {
        }
    }
}