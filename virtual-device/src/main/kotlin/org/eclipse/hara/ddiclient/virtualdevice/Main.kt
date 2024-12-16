/*
 * Copyright © 2017-2024  Kynetics, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hara.ddiclient.virtualdevice

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.eclipse.hara.ddiclient.api.HaraClientDefaultImpl
import org.eclipse.hara.ddiclient.api.HaraClientData
import org.eclipse.hara.ddiclient.virtualdevice.entrypoint.*
import org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY
import java.time.Duration
import kotlin.random.Random.Default.nextLong

val virtualMachineGlobalScope = CoroutineScope(Dispatchers.Default)

fun main() {
    Configuration.apply {
        System.setProperty(DEFAULT_LOG_LEVEL_KEY, logLevel)
        val connTimeoutDuration = Duration.ofSeconds(connectTimeout)
        val callTimeoutDuration = Duration.ofSeconds(callTimeout)
        val readTimeoutDuration = Duration.ofSeconds(readTimeout)
        val writeTimeoutDuration = Duration.ofSeconds(writeTimeout)

        repeat(poolSize) {
            val clientData = HaraClientData(
                tenant,
                controllerIdGenerator.invoke(it),
                url,
                gatewayToken
            )

            val httpBuilder = OkHttpClient.Builder()
                .connectTimeout(connTimeoutDuration)
                .callTimeout(callTimeoutDuration)
                .readTimeout(readTimeoutDuration)
                .writeTimeout(writeTimeoutDuration)

            virtualMachineGlobalScope.launch {
                val delay = nextLong(0, virtualDeviceStartingDelay)
                println("Virtual Device $it starts in $delay milliseconds")
                delay(delay)
                getClient(this, clientData, it, httpBuilder).startAsync()
            }
        }
    }
    Unit
}

private fun getClient(
    scope: CoroutineScope,
    clientData: HaraClientData,
    virtualDeviceId: Int,
    httpBuilder: OkHttpClient.Builder): HaraClientDefaultImpl {
    return HaraClientDefaultImpl().apply {
        init(
            haraClientData = clientData,
            directoryForArtifactsProvider = DirectoryForArtifactsProviderImpl(
                clientData.controllerId),
            configDataProvider = ConfigDataProviderImpl(virtualDeviceId, clientData),
            softDeploymentPermitProvider = DeploymentPermitProviderImpl(),
            messageListeners = listOf(MessageListenerImpl(virtualDeviceId, clientData)),
            updaters = listOf(UpdaterImpl(virtualDeviceId, clientData)),
            downloadBehavior = DownloadBehaviorImpl(),
            scope = scope,
            httpBuilder = httpBuilder
        )
    }
}
