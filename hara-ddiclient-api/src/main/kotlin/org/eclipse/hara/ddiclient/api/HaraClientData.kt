/*
 * Copyright © 2017-2024  Kynetics, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.api

import java.net.MalformedURLException
import java.net.URL

/**
 * Configuration data of Hara client.
 * @property tenant name of the tenant (targets group)
 * on the Update Server to connect to.
 * @property controllerId id used by the client to register on
 * the Update Server.
 * @property serverUrl URL of the Update Server.
 * @property gatewayToken used by the client to authenticate itself
 * on the Update Server. It is different for each tenant.
 * @property targetToken used by the client to authenticate itself
 * on the Update Server. It is different for each device.
 */
data class HaraClientData constructor(
        val tenant: String,
        val controllerId: String,
        val serverUrl: String,
        val gatewayToken: String? = null,
        val targetToken: String? = null,
) {

    init {
        notEmpty(tenant, "tenant")
        notEmpty(controllerId, "controllerId")
        notEmpty(serverUrl, "serverUrl")
        validUrl(serverUrl, "serverUrl")
        check(!(gatewayToken?.isEmpty() == true && targetToken?.isEmpty() == true)) {
            "gatewayToken and targetToken cannot both be empty" }
    }

    private fun notEmpty(item: String, itemName: String) {
        require(item.isNotBlank()) { "$itemName could not be null or empty" }
    }

    private fun validUrl(url: String, itemName: String) {
        try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException("$itemName is a malformed url")
        }
    }

}
