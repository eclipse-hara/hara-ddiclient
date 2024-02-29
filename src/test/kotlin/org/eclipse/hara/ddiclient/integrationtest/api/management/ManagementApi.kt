/*
 * Copyright © 2017-2024  Kynetics  LLC
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hara.ddiclient.integrationtest.api.management

import okhttp3.OkHttpClient
import org.eclipse.hara.ddiclient.integrationtest.utils.addOkhttpLogger
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.concurrent.Executors

interface ManagementApi {
    companion object {
        const val BASE_V1_REQUEST_MAPPING = "/rest/v1"
    }

    @GET("$BASE_V1_REQUEST_MAPPING/targets/{targetId}/actions/{actionId}/status")
    suspend fun getTargetActionStatusAsync(
        @Header("Authorization") auth: String,
        @Path("targetId") targetId: String,
        @Path("actionId") actionId: Int
    ): ActionStatus

    @GET("$BASE_V1_REQUEST_MAPPING/targets/{targetId}/actions/{actionId}")
    suspend fun getActionAsync(
        @Header("Authorization") auth: String,
        @Path("targetId") targetId: String,
        @Path("actionId") actionId: Int
    ): Action

    @DELETE("$BASE_V1_REQUEST_MAPPING/targets/{targetId}/actions/{actionId}")
    suspend fun deleteTargetActionAsync(
        @Header("Authorization") auth: String,
        @Path("targetId") targetId: String,
        @Path("actionId") actionId: Int
    )

    @PUT("$BASE_V1_REQUEST_MAPPING/system/configs/authentication.gatewaytoken.enabled")
    suspend fun setGatewayTokenAuthorizationEnabled(
        @Header("Authorization") auth: String,
        @Body body: ServerSystemConfig
    )

    @PUT("$BASE_V1_REQUEST_MAPPING/system/configs/authentication.targettoken.enabled")
    suspend fun setTargetTokenAuthorizationEnabled(
        @Header("Authorization") auth: String,
        @Body body: ServerSystemConfig
    )

    @PUT("$BASE_V1_REQUEST_MAPPING/system/configs/pollingTime")
    suspend fun setPollingTime(
        @Header("Authorization") auth: String,
        @Body body: ServerSystemConfig
    )

    @POST("$BASE_V1_REQUEST_MAPPING/targets/{targetId}/assignedDS")
    suspend fun assignDistributionToTarget(
        @Header("Authorization") auth: String,
        @Path("targetId") targetId: String,
        @Body body: HawkbitAssignDistributionBody
    ): HawkbitAssignDistributionResponse

    @DELETE("$BASE_V1_REQUEST_MAPPING/targets/{targetId}")
    suspend fun deleteTarget(
        @Header("Authorization") auth: String,
        @Path("targetId") targetId: String
    )

    @POST("$BASE_V1_REQUEST_MAPPING/targets")
    suspend fun createTarget(
        @Header("Authorization") auth: String,
        @Body body: List<HawkbitTargetInfo>
    ): List<HawkbitTargetInfo>
}

object ManagementClient {

    fun newInstance(url: String): ManagementApi {
        return object : ManagementApi {
            private val delegate: ManagementApi = Retrofit.Builder().baseUrl(url)
                    .client(OkHttpClient.Builder().addOkhttpLogger().build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .callbackExecutor(Executors.newSingleThreadExecutor())
                    .build()
                    .create(ManagementApi::class.java)

            override suspend fun getTargetActionStatusAsync(auth: String, targetId: String, actionId: Int): ActionStatus {
                return delegate.getTargetActionStatusAsync(auth, targetId, actionId)
            }

            override suspend fun getActionAsync(auth: String, targetId: String, actionId: Int): Action {
                return delegate.getActionAsync(auth, targetId, actionId)
            }

            override suspend fun deleteTargetActionAsync(auth: String, targetId: String, actionId: Int): Unit {
                TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
            }

            override suspend fun setGatewayTokenAuthorizationEnabled(auth: String,
                                                                     body: ServerSystemConfig) {
                delegate.setGatewayTokenAuthorizationEnabled(auth, body)
            }

            override suspend fun setTargetTokenAuthorizationEnabled(auth: String,
                                                                    body: ServerSystemConfig) {
                delegate.setTargetTokenAuthorizationEnabled(auth, body)
            }

            override suspend fun setPollingTime(auth: String, body: ServerSystemConfig) {
                delegate.setPollingTime(auth, body)
            }

            override suspend fun assignDistributionToTarget(auth: String, targetId: String, body: HawkbitAssignDistributionBody): HawkbitAssignDistributionResponse {
                return delegate.assignDistributionToTarget(auth, targetId, body)
            }

            override suspend fun deleteTarget(auth: String, targetId: String) {
                return delegate.deleteTarget(auth, targetId)
            }

            override suspend fun createTarget(auth: String,
                                              body: List<HawkbitTargetInfo>): List<HawkbitTargetInfo> {
                return delegate.createTarget(auth, body)
            }
        }
    }
}
