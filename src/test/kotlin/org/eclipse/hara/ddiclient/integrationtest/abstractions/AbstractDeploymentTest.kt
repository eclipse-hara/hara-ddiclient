/*
 *
 *  * Copyright © 2017-2024  Kynetics  LLC
 *  *
 *  * This program and the accompanying materials are made
 *  * available under the terms of the Eclipse Public License 2.0
 *  * which is available at https://www.eclipse.org/legal/epl-2.0/
 *  *
 *  * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.hara.ddiclient.integrationtest.abstractions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils
import org.eclipse.hara.ddiclient.integrationtest.utils.addOkhttpLogger
import org.eclipse.hara.ddiclient.integrationtest.utils.internalLog
import org.eclipse.hara.ddiclient.integrationtest.utils.safeStopClient
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

abstract class AbstractDeploymentTest: AbstractTest() {

    private var assertServerActionsScope = CoroutineScope(Dispatchers.IO)
    private var assertServerActionsOnCompleteJob: Deferred<Unit>? = null
    private var testScope = CoroutineScope(Dispatchers.Default)

    private val eventListener = object : MessageListener {
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


    protected fun defaultClientFromTargetId(
        directoryDataProvider: DirectoryForArtifactsProvider = TestUtils.directoryDataProvider,
        configDataProvider: ConfigDataProvider = TestUtils.configDataProvider,
        updater: Updater = TestUtils.updater,
        messageListeners: List<MessageListener> = listOf(eventListener),
        deploymentPermitProvider: DeploymentPermitProvider = object :
            DeploymentPermitProvider {},
        downloadBehavior: DownloadBehavior = TestUtils.downloadBehavior,
        okHttpClientBuilder: OkHttpClient.Builder = OkHttpClient.Builder().addOkhttpLogger(),
        targetToken: String? = "",
        gatewayToken: String? = TestUtils.gatewayToken
    ): (String) -> HaraClient = { targetId ->
        val clientData = HaraClientData(
            TestUtils.tenantName,
            targetId,
            TestUtils.hawkbitUrl,
            gatewayToken,
            targetToken)

        val client = HaraClientDefaultImpl()

        client.init(
            clientData,
            directoryDataProvider,
            configDataProvider,
            deploymentPermitProvider,
            listOf(*messageListeners.toTypedArray()),
            listOf(updater),
            downloadBehavior,
            httpBuilder = okHttpClientBuilder
        )
        client
    }

    protected fun createHaraClientWithAuthorizationPermissions(
        downloadAllowed: Boolean = true,
        updateAllowed: Boolean = true): HaraClient {

        val deploymentBehavior = object : DeploymentPermitProvider {
            override fun downloadAllowed() = CompletableDeferred(downloadAllowed)
            override fun updateAllowed() = CompletableDeferred(updateAllowed)
        }
        return defaultClientFromTargetId(
            deploymentPermitProvider = deploymentBehavior).invoke(targetId)
    }

    protected suspend fun startTheTestAndWaitForResult(client: HaraClient,
                                                       deployment: TestUtils.TargetDeployments) {
        client.startAsync()
        assertServerActionsOnCompleteJob = assertServerActionsOnComplete(client, deployment)

        testScope.async {
            while (assertServerActionsOnCompleteJob?.isCompleted == false) {
                delay(1.seconds)
            }
        }.await()
    }

    private fun assertServerActionsOnComplete(
        client: HaraClient,
        deployment: TestUtils.TargetDeployments): Deferred<Unit> {
        return assertServerActionsScope.async(start = CoroutineStart.LAZY) {
            val deploymentInfo = deployment.deploymentInfo.first()
            while (managementApi.getActionAsync(TestUtils.basic, deployment.targetId,
                    deploymentInfo.actionId).status != Action.Status.finished
            ) {
                delay(5.seconds)
            }

            val actionStatus =
                managementApi.getTargetActionStatusAsync(TestUtils.basic, deployment.targetId,
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

    protected val expectedActionOnStart = ActionStatus(setOf(
        TestUtils.firstActionWithAssignmentEntry
    ))

}