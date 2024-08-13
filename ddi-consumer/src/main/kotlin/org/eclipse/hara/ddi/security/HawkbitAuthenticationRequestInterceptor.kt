/*
 * Copyright © 2017-2024  Kynetics, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddi.security

import java.io.IOException
import java.util.Objects
import okhttp3.Interceptor
import okhttp3.Response
import org.slf4j.LoggerFactory

/**
 * @author Daniele Sergio
 */
class HawkbitAuthenticationRequestInterceptor(private val authentications: List<Authentication>) : Interceptor {

    private var authenticationUse = 0

    init {
        Objects.requireNonNull(authentications)
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        if (authentications.isEmpty()) {
            return chain.proceed(chain.request())
        }

        val originalRequest = chain.request()

        val builder = originalRequest.newBuilder()
        val size = authentications.size
        val exitValue = authenticationUse
        var response: Response? = null
        do {
            response?.close()
            val authentication = authentications[authenticationUse]
            runCatching {
                builder.header(authentication.header, authentication.headerValue)
            }.onFailure {
                LOG.error("Error in setting the ${authentication.type.type} header", it)
            }
            response = chain.proceed(builder.build())
            if (response.code != 401) {
                break
            }
            authenticationUse = ++authenticationUse % size
        } while (authenticationUse != exitValue)

        return response!!
    }

    companion object {
        val LOG = LoggerFactory.getLogger(HawkbitAuthenticationRequestInterceptor::class.java)!!
    }
}
