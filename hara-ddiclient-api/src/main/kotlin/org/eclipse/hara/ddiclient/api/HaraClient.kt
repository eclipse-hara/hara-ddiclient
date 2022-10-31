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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

/**
 * Client that executes actions requested by the Update Server. The
 * actions are provided as response of the ping request.
 * The allowed actions are:
 * - execute an update
 * - cancel an update
 * - send config data
 */
interface HaraClient {

    /**
     * Initialization function
     * @param haraClientData client configuration data
     * @param directoryForArtifactsProvider directory provider
     * @param configDataProvider config data provider
     * @param softDeploymentPermitProvider deployment permit provider
     * @param messageListeners message listeners
     * @param updaters list of updaters. Different updaters are responsible to install
     * different types of software module. See [Updater]
     * @param httpBuilder http builder
     * @param scope coroutine scope used for launching actors
     */
    fun init(
        haraClientData: HaraClientData,
        directoryForArtifactsProvider: DirectoryForArtifactsProvider,
        configDataProvider: ConfigDataProvider,
        softDeploymentPermitProvider: DeploymentPermitProvider,
        messageListeners: List<MessageListener>,
        updaters: List<Updater>,
        downloadBehavior: DownloadBehavior,
        forceDeploymentPermitProvider: DeploymentPermitProvider = object : DeploymentPermitProvider{},
        httpBuilder: OkHttpClient.Builder = OkHttpClient.Builder(),
        scope:CoroutineScope = CoroutineScope(Dispatchers.Default)
    )

    /**
     * Start polling the Update Server.
     * See [stop] to stop polling
     */
    fun startAsync()

    /**
     * Stop polling the Update Server.
     * See [startAsync] to start polling
     */
    fun stop()

    /**
     * Force the client to execute a ping request (poll immediately).
     */
    fun forcePing()
}
