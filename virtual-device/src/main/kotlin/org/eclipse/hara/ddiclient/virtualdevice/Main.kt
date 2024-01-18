/*
 * Copyright © 2017-2024  Kynetics  LLC
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hara.ddiclient.virtualdevice

import kotlinx.coroutines.*
import org.eclipse.hara.ddiclient.api.HaraClientDefaultImpl
import org.eclipse.hara.ddiclient.api.HaraClientData
import org.eclipse.hara.ddiclient.virtualdevice.entrypoint.*
import kotlin.random.Random.Default.nextLong

fun main() = runBlocking {
    Configuration.apply {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, logLevel)

        repeat(poolSize) {
            val clientData = HaraClientData(
                tenant,
                controllerIdGenerator.invoke(),
                url,
                gatewayToken
            )

            launch(Dispatchers.IO) {
                val delay = nextLong(0, virtualDeviceStartingDelay)
                println("Virtual Device $it starts in $delay milliseconds")
                delay(delay)
                getClient(this, clientData, it).startAsync()
            }
        }
    }
    Unit
}

private fun getClient(scope: CoroutineScope, clientData: HaraClientData, virtualDeviceId: Int): HaraClientDefaultImpl {
    val client = HaraClientDefaultImpl()
    client.init(
        clientData,
        DirectoryForArtifactsProviderImpl(clientData.controllerId),
        ConfigDataProviderImpl(virtualDeviceId, clientData),
        DeploymentPermitProviderImpl(),
        listOf(MessageListenerImpl(virtualDeviceId, clientData)),
        listOf(UpdaterImpl(virtualDeviceId, clientData)),
        DownloadBehaviorImpl(),
        scope=scope
    )
    return client
}
