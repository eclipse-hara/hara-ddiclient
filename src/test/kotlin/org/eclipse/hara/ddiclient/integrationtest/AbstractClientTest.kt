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

import org.eclipse.hara.ddiclient.core.HaraClientDefaultImpl
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.basic
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.gatewayToken
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.getDownloadDirectoryFromActionId
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.tenantName
import org.eclipse.hara.ddiclient.integrationtest.TestUtils.hawkbitUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.eclipse.hara.ddiclient.core.api.*
import org.joda.time.Duration
import org.testng.Assert
import java.io.File
import java.util.LinkedList

abstract class AbstractClientTest {

    protected var client: HaraClient? = null

    private val queue = LinkedList<() -> Unit >()

    protected open fun defaultClientFromTargetId(
            directoryDataProvider: DirectoryForArtifactsProvider = TestUtils.directoryDataProvider,
            configDataProvider: ConfigDataProvider = TestUtils.configDataProvider,
            updater: Updater = TestUtils.updater,
            messageListeners: List<MessageListener> = emptyList(),
            deploymentPermitProvider: DeploymentPermitProvider = object : DeploymentPermitProvider {}
    ): (String) -> HaraClient = { targetId ->
        val clientData = HaraClientData(
                tenantName,
                targetId,
                hawkbitUrl,
                gatewayToken)

        val client = HaraClientDefaultImpl()

        val eventListener = object : MessageListener {
            override fun onMessage(message: MessageListener.Message) {
                when (message) {

                    is MessageListener.Message.Event.UpdateFinished, MessageListener.Message.State.CancellingUpdate -> {
                        queue.poll().invoke()
                    }

                    else -> { println(message) }
                }
            }
        }

        client.init(
                clientData,
                directoryDataProvider,
                configDataProvider,
                deploymentPermitProvider,
                listOf(eventListener, *messageListeners.toTypedArray()),
                listOf(updater)
        )
        client
    }

    // todo refactor test
    protected fun testTemplate(
            deployment: TestUtils.TargetDeployments,
            timeout: Long = Duration.standardSeconds(15).millis,
            clientFromTargetId: (String) -> HaraClient = defaultClientFromTargetId()
    ) = runBlocking {

        withTimeout(timeout) {
            client = clientFromTargetId(deployment.targetId)
            val managementApi = ManagementClient.newInstance(hawkbitUrl)

            deployment.deploymentInfo.forEach { deploymentInfo ->

                var actionStatus = managementApi.getTargetActionStatusAsync(basic, deployment.targetId, deploymentInfo.actionId)

                Assert.assertEquals(actionStatus, deploymentInfo.actionStatusOnStart)

                queue.add {
                    launch {
                        while(managementApi.getActionAsync(basic, deployment.targetId, deploymentInfo.actionId)
                                .status != Action.Status.finished
                        ) {
                            delay(100)
                        }
                        actionStatus = managementApi.getTargetActionStatusAsync(basic, deployment.targetId, deploymentInfo.actionId)

                        Assert.assertEquals(actionStatus.content, deploymentInfo.actionStatusOnFinish.content)

                        deploymentInfo.filesDownloadedPairedWithServerFile.forEach { (fileDownloaded, serverFile) ->
                            println(File(fileDownloaded).absolutePath)
                            println(File(serverFile).absolutePath)
                            Assert.assertEquals(File(fileDownloaded).readText(), File(serverFile).readText())
                        }

                        getDownloadDirectoryFromActionId(deploymentInfo.actionId.toString()).deleteRecursively()
                    }
                }
            }
            client?.startAsync()
            launch {
                while (queue.isNotEmpty()) {
                    delay(500) }
            }
        }
    }
}
