/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiapiclient.security

import org.eclipse.hara.ddiapiclient.security.Authentication.AuthenticationType.ANONYMOUS_AUTHENTICATION

/**
 * @author Daniele Sergio
 */
class Authentication private constructor(val type: AuthenticationType, token: String) {

    val headerValue: String = String.format(HEADER_VALUE_TEMPLATE, type.type, token)
    val header: String = type.header

    enum class AuthenticationType constructor(internal val type: String) {
        TARGET_TOKEN_AUTHENTICATION("TargetToken"),
        GATEWAY_TOKEN_AUTHENTICATION("GatewayToken"),
        ANONYMOUS_AUTHENTICATION("");

        internal val header = "Authorization"
    }

    companion object {

        fun newInstance(type: AuthenticationType, token: String): Authentication {
            return if (type == ANONYMOUS_AUTHENTICATION) ANONYMOUS else Authentication(type, token)
        }

        private var ANONYMOUS: Authentication = Authentication(ANONYMOUS_AUTHENTICATION, "")

        private const val HEADER_VALUE_TEMPLATE = "%s %s"
    }
}
