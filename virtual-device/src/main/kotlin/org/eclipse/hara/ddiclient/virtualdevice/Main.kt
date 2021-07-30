/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/
package org.eclipse.hara.ddiclient.virtualdevice

import kotlinx.coroutines.*
import org.eclipse.hara.ddiclient.core.HaraClientDefaultImpl
import org.eclipse.hara.ddiclient.core.api.HaraClientData
import org.eclipse.hara.ddiclient.virtualdevice.entrypoint.ConfigDataProviderImpl
import org.eclipse.hara.ddiclient.virtualdevice.entrypoint.DeploymentPermitProviderImpl
import org.eclipse.hara.ddiclient.virtualdevice.entrypoint.DirectoryForArtifactsProviderImpl
import org.eclipse.hara.ddiclient.virtualdevice.entrypoint.MessageListenerImpl
import org.eclipse.hara.ddiclient.virtualdevice.entrypoint.UpdaterImpl
import kotlin.random.Random.Default.nextLong

@OptIn(DelicateCoroutinesApi::class)
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

            GlobalScope.launch {
                val delay = nextLong(0, virtualDeviceStartingDelay)
                println("Virtual Device $it starts in $delay milliseconds")
                delay(delay)
                getClient(clientData, it).startAsync()
            }
        }
    }

    while (true) {}
}

private fun getClient(clientData: HaraClientData, virtualDeviceId: Int): HaraClientDefaultImpl {
    val client = HaraClientDefaultImpl()
    client.init(
        clientData,
        DirectoryForArtifactsProviderImpl(clientData.controllerId),
        ConfigDataProviderImpl(virtualDeviceId, clientData),
        DeploymentPermitProviderImpl(),
        listOf(MessageListenerImpl(virtualDeviceId, clientData)),
        listOf(UpdaterImpl(virtualDeviceId, clientData))
    )
    return client
}
