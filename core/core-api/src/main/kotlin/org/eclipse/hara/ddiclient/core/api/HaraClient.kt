/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.core.api

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
     * @param deploymentPermitProvider deployment permit provider
     * @param messageListeners message listeners
     * @param updaters list of updaters. Different updaters are responsible to install
     * different types of software module. See [Updater]
     * @param httpBuilder http builder
     */
    fun init(
        haraClientData: HaraClientData,
        directoryForArtifactsProvider: DirectoryForArtifactsProvider,
        configDataProvider: ConfigDataProvider,
        deploymentPermitProvider: DeploymentPermitProvider,
        messageListeners: List<MessageListener>,
        updaters: List<Updater>,
        downloadBehavior: DownloadBehavior,
        httpBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
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
