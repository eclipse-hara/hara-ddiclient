/*
 * Copyright © 2026  Eurotech S.p.A.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.api

import org.testng.Assert.assertNull
import org.testng.Assert.expectThrows
import org.testng.annotations.Test

class HaraClientDataTest {

    @Test
    fun targetTokenOnly() {
        HaraClientData(TENANT, CONTROLLER_ID, URL, targetToken = "token")
    }

    @Test
    fun gatewayTokenOnly() {
        HaraClientData(TENANT, CONTROLLER_ID, URL, gatewayToken = "token")
    }

    // Client certificate authentication (mTLS): the TLS layer authenticates the device
    @Test
    fun noTokensAtAll() {
        val data = HaraClientData(TENANT, CONTROLLER_ID, URL)

        assertNull(data.gatewayToken)
        assertNull(data.targetToken)
    }

    @Test
    fun bothTokensEmptyIsRejected() {
        expectThrows(IllegalStateException::class.java) {
            HaraClientData(TENANT, CONTROLLER_ID, URL, gatewayToken = "", targetToken = "")
        }
    }

    @Test
    fun blankTenantIsRejected() {
        expectThrows(IllegalArgumentException::class.java) {
            HaraClientData(" ", CONTROLLER_ID, URL, targetToken = "token")
        }
    }

    @Test
    fun blankControllerIdIsRejected() {
        expectThrows(IllegalArgumentException::class.java) {
            HaraClientData(TENANT, "", URL, targetToken = "token")
        }
    }

    @Test
    fun malformedUrlIsRejected() {
        expectThrows(IllegalArgumentException::class.java) {
            HaraClientData(TENANT, CONTROLLER_ID, "hawkbit.example.com", targetToken = "token")
        }
    }

    companion object {
        private const val TENANT = "DEFAULT"
        private const val CONTROLLER_ID = "device-1"
        private const val URL = "https://hawkbit.example.com"
    }
}
