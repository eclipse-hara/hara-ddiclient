/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.core

import org.eclipse.hara.ddiapiclient.api.DdiClientDefaultImpl
import org.eclipse.hara.ddiclient.core.actors.AbstractActor
import org.eclipse.hara.ddiclient.core.actors.ActorRef
import org.eclipse.hara.ddiclient.core.actors.ConnectionManager
import org.eclipse.hara.ddiclient.core.actors.RootActor
import org.eclipse.hara.ddiclient.core.actors.HaraClientContext
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.eclipse.hara.ddiclient.core.api.*

class HaraClientDefaultImpl : HaraClient {

    var rootActor: ActorRef? = null

    override fun init(
            haraClientData: HaraClientData,
            directoryForArtifactsProvider: DirectoryForArtifactsProvider,
            configDataProvider: ConfigDataProvider,
            deploymentPermitProvider: DeploymentPermitProvider,
            messageListeners: List<MessageListener>,
            updaters: List<Updater>,
            httpBuilder: OkHttpClient.Builder
            ) {
        rootActor = AbstractActor.actorOf("rootActor", HaraClientContext(
                DdiClientDefaultImpl.of(haraClientData, httpBuilder),
                UpdaterRegistry(*updaters.toTypedArray()),
                configDataProvider,
                PathResolver(directoryForArtifactsProvider),
                deploymentPermitProvider,
                messageListeners
        )) { RootActor.of(it) }
    }

    override fun startAsync() = runBlocking { rootActor!!.send(ConnectionManager.Companion.Message.In.Start) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun stop() = runBlocking {
        if (!rootActor!!.isClosedForSend) {
            rootActor!!.send(ConnectionManager.Companion.Message.In.Stop)
        }
    }

    override fun forcePing() = runBlocking { rootActor!!.send(ConnectionManager.Companion.Message.In.ForcePing) }
}
