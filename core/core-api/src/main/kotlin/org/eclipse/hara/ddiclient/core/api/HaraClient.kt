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

interface HaraClient {

    fun init(
            haraClientData: HaraClientData,
            directoryForArtifactsProvider: DirectoryForArtifactsProvider,
            configDataProvider: ConfigDataProvider,
            deploymentPermitProvider: DeploymentPermitProvider,
            messageListeners: List<MessageListener>,
            updaters: List<Updater>,
            httpBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
    )

    fun startAsync()

    fun stop()

    fun forcePing()
}
