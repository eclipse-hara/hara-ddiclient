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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.eclipse.hara.ddiclient.api.ConfigDataProvider
import org.eclipse.hara.ddiclient.api.DeploymentPermitProvider
import org.eclipse.hara.ddiclient.api.DirectoryForArtifactsProvider
import org.eclipse.hara.ddiclient.api.DownloadBehavior
import org.eclipse.hara.ddiclient.api.HaraClient
import org.eclipse.hara.ddiclient.api.MessageListener
import org.eclipse.hara.ddiclient.api.Updater
import org.eclipse.hara.ddiclient.integrationtest.utils.internalLog
import kotlin.coroutines.cancellation.CancellationException

abstract class AbstractHaraMessageTest : AbstractTest() {

    private var expectedHaraMessages = mutableListOf<ExpectedMessage>()
    open val expectedMessagesAssertionListener:
            List<suspend (ExpectedMessage) -> Unit> = listOf()

    private val expectedMessageChannel =
        Channel<ExpectedMessage>(5, BufferOverflow.DROP_OLDEST)

    private val checkMessagesScope = CoroutineScope(Dispatchers.IO)
    private var checkExpectedMessagesJob: Deferred<Unit>? = null

    open val expectedMessagesList: MutableList<MutableList<ExpectedMessage>> =
        mutableListOf(expectedHaraMessages)

    open fun filterHaraMessages(message: MessageListener.Message): Boolean = true

    protected fun sendExpectedMessage(message: ExpectedMessage) {
        checkMessagesScope.launch {
            expectedMessageChannel.send(message)
        }
    }

    protected suspend fun startWatchingExpectedMessages(lastTest: Boolean = false) {
        checkExpectedMessagesJob = getExpectedMessagesCheckingJob(lastTest)
        try {
            checkExpectedMessagesJob?.await()
        } catch (ignored: CancellationException) {
        }
    }

    open val messageListener: MessageListener
        get() = object : MessageListener {
            override fun onMessage(message: MessageListener.Message) {
                "Received message: $message".internalLog()
                if (filterHaraMessages(message)) {
                    sendExpectedMessage(ExpectedMessage.HaraMessage(message))
                }
            }
        }

    private suspend fun getExpectedMessagesCheckingJob(lastTest: Boolean): Deferred<Unit> {
        return checkMessagesScope.async {
            for (msg in expectedMessageChannel) {
                when (msg) {
                    is ExpectedMessage.HaraMessage -> {
                        if (expectedHaraMessages.isNotEmpty()) {
                            assertEquals(msg, expectedHaraMessages.removeFirst())
                        }
                    }

                    else -> {
                        expectedMessagesAssertionListener.forEach {
                            it.invoke(msg)
                        }
                    }
                }
                val finished = expectedMessagesList.map {
                    it.isEmpty()
                }
                if (finished.all { it }) {
                    "All expected messages received".internalLog()
                    checkExpectedMessagesJob?.cancel()
                    if (lastTest) {
                        safeStopClient()
                    }
                }
            }
        }
    }

    override fun clientFromTargetId(
        directoryDataProvider: DirectoryForArtifactsProvider,
        configDataProvider: ConfigDataProvider, updater: Updater,
        messageListeners: List<MessageListener>,
        deploymentPermitProvider: DeploymentPermitProvider,
        downloadBehavior: DownloadBehavior,
        okHttpClientBuilder: OkHttpClient.Builder,
        targetToken: String?,
        gatewayToken: String?): (String) -> HaraClient {
        return super.clientFromTargetId(
            directoryDataProvider, configDataProvider, updater,
            listOf(messageListener),
            deploymentPermitProvider,
            downloadBehavior, okHttpClientBuilder,
            targetToken, gatewayToken)
    }

    protected fun expectMessages(vararg messages: MessageListener.Message) {
        expectedHaraMessages.clear()
        expectedHaraMessages.addAll(messages.map { ExpectedMessage.HaraMessage(it) })
    }


    abstract class ExpectedMessage {

        data class HaraMessage(val message: MessageListener.Message) : ExpectedMessage()
    }
}