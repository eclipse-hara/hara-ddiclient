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

import java.net.MalformedURLException
import java.net.URL

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
        if ((gatewayToken == null || gatewayToken.isBlank()) && (targetToken == null || targetToken.isBlank())) {
            throw IllegalStateException("gatewayToken and targetToken cannot both be empty")
        }
    }

    private fun notEmpty(item: String, itemName: String) {
        if (item.isBlank()) {
            throw IllegalArgumentException("$itemName could not be null or empty")
        }
    }

    private fun validUrl(url: String, itemName: String) {
        try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException("$itemName is a malformed url")
        }
    }

}
