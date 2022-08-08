/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.api

import org.eclipse.hara.ddi.api.DdiClientDefaultImpl
import org.eclipse.hara.ddiclient.api.actors.AbstractActor
import org.eclipse.hara.ddiclient.api.actors.ActorRef
import org.eclipse.hara.ddiclient.api.actors.ConnectionManager
import org.eclipse.hara.ddiclient.api.actors.RootActor
import org.eclipse.hara.ddiclient.api.actors.HaraClientContext
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

class HaraClientDefaultImpl : HaraClient {

    var rootActor: ActorRef? = null

    override fun init(
        haraClientData: HaraClientData,
        directoryForArtifactsProvider: DirectoryForArtifactsProvider,
        configDataProvider: ConfigDataProvider,
        softDeploymentPermitProvider: DeploymentPermitProvider,
        messageListeners: List<MessageListener>,
        updaters: List<Updater>,
        downloadBehavior: DownloadBehavior,
        forceDeploymentPermitProvider: DeploymentPermitProvider,
        httpBuilder: OkHttpClient.Builder
            ) {
        rootActor = AbstractActor.actorOf("rootActor", HaraClientContext(
                DdiClientDefaultImpl.of(haraClientData, httpBuilder),
                UpdaterRegistry(*updaters.toTypedArray()),
                configDataProvider,
                PathResolver(directoryForArtifactsProvider),
                softDeploymentPermitProvider,
                messageListeners,
                downloadBehavior,
                forceDeploymentPermitProvider
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
