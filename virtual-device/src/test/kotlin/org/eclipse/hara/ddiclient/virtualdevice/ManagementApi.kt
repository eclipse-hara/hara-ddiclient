/*
 *
 *  * Copyright © 2017-2024  Kynetics, Inc.
 *  *
 *  * This program and the accompanying materials are made
 *  * available under the terms of the Eclipse Public License 2.0
 *  * which is available at https://www.eclipse.org/legal/epl-2.0/
 *  *
 *  * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.hara.ddiclient.virtualdevice

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.concurrent.Executors
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.PUT
import java.text.SimpleDateFormat
import java.util.Date

val LOG_HTTP: Boolean = System.getProperty("LOG_HTTP", "false").toBoolean()

interface ManagementApi {
    companion object {
        const val BASE_V1_REQUEST_MAPPING = "/rest/v1"
    }

    @GET("$BASE_V1_REQUEST_MAPPING/targets")
    suspend fun getAllTargets(
        @Header("Authorization") auth: String
    ): HawkbitTargets

    @PUT("$BASE_V1_REQUEST_MAPPING/system/configs/pollingTime")
    suspend fun setPollingTime(
        @Header("Authorization") auth: String,
        @Body body: ServerSystemConfig
    )
}

object ManagementClient {
    fun createManagementApi(baseUrl: String): ManagementApi {
        val client = OkHttpClient.Builder().addOkhttpLogger().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .callbackExecutor(Executors.newSingleThreadExecutor())
            .build()
        return retrofit.create(ManagementApi::class.java)
    }

}

class HawkbitTargets(val content: List<DeviceTarget> )

class DeviceTarget(val controllerId: String?, val lastControllerRequestAt: Long?)

data class ServerSystemConfig(val value: Any)

val currentTime: String
    get() = SimpleDateFormat("HH.mm.ss.SSS").format(Date())


fun OkHttpClient.Builder.addOkhttpLogger(): OkHttpClient.Builder = apply {
    val logger = HttpLoggingInterceptor.Logger { message ->
        if (LOG_HTTP) {
            println("$currentTime: OkHttp: $message")
        }
    }
    addInterceptor(HttpLoggingInterceptor(logger).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
}