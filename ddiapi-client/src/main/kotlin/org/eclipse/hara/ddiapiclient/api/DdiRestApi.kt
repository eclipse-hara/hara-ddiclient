/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/
package org.eclipse.hara.ddiapiclient.api

import org.eclipse.hara.ddiapiclient.api.model.ArtifactResponse
import org.eclipse.hara.ddiapiclient.api.model.ConfigurationDataRequest
import org.eclipse.hara.ddiapiclient.api.model.CancelActionResponse
import org.eclipse.hara.ddiapiclient.api.model.CancelFeedbackRequest
import org.eclipse.hara.ddiapiclient.api.model.ControllerBaseResponse
import org.eclipse.hara.ddiapiclient.api.model.DeploymentBaseResponse
import org.eclipse.hara.ddiapiclient.api.model.DeploymentFeedbackRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * REST resource handling for root controller CRUD operations.
 *
 * @author Daniele Sergio
 */
interface DdiRestApi {

    /**
     * Returns all artifacts of a given software module and target.
     *
     * @param tenant
     * of the client
     * @param controllerId
     * of the target that matches to controller id
     * @param softwareModuleId
     * of the software module
     * @return the response
     */
    @Headers("Accept: application/hal+json")
    @GET(DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/softwaremodules/{softwareModuleId}/artifacts")
    suspend fun getSoftwareModulesArtifacts(
        @Path("tenant") tenant: String,
        @Path("controllerId") controllerId: String,
        @Path("softwareModuleId") softwareModuleId: String
    ): List<ArtifactResponse>

    /**
     * Root resource for an individual [Target].
     *
     * @param tenant
     * of the request
     * @param controllerId
     * of the target that matches to controller id
     * @return the response
     */
    @Headers("Accept: application/hal+json")
    @GET(DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}")
    suspend fun getControllerActions(
        @Path("tenant") tenant: String,
        @Path("controllerId") controllerId: String,
        @Header(value = "If-None-Match") etag: String = ""
    ): Response<ControllerBaseResponse>

    /**
     * Handles GET [DdiArtifact] download request.
     *
     * @param url
     */
    @Streaming
    @GET
    suspend fun downloadArtifact(@Url url: String): ResponseBody

    /**
     * Resource for software module.
     *
     * @param tenant
     * of the request
     * @param controllerId
     * of the target
     * @param actionId
     * of the [DdiDeploymentBase] that matches to active
     * actions.
     * @param resource
     * an hashcode of the resource which indicates if the action has
     * been changed, e.g. from 'soft' to 'force' and the eTag needs
     * to be re-generated
     * @param actionHistoryMessageCount
     * specifies the number of messages to be returned from action
     * history.
     * actionHistoryMessageCount < 0, retrieves the maximum allowed
     * number of action status messages from history;
     * actionHistoryMessageCount = 0, does not retrieve any message;
     * and actionHistoryMessageCount > 0, retrieves the specified
     * number of messages, limited by maximum allowed number.
     * @return the response
     */
    @Headers("Accept: application/hal+json")
    @GET(DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/" + DdiRestConstants.DEPLOYMENT_BASE_ACTION +
            "/{actionId}")
    suspend fun getDeploymentActionDetails(
        @Path("tenant") tenant: String,
        @Path("controllerId") controllerId: String,
        @Path("actionId") actionId: String,
        @Query(value = "c") resource: Int?,
        @Query(value = "actionHistory") actionHistoryMessageCount: Int?,
        @Header(value = "If-None-Match") etag: String = ""
    ): Response<DeploymentBaseResponse>

    /**
     * This is the feedback channel for the [DdiDeploymentBase] action.
     *
     * @param tenant
     * of the client
     * @param feedback
     * to provide
     * @param controllerId
     * of the target that matches to controller id
     * @param actionId
     * of the action we have feedback for
     *
     * @return the response
     */
    @Headers("Content-Type: application/json")
    @POST(DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/" + DdiRestConstants.DEPLOYMENT_BASE_ACTION + "/{actionId}/" +
            DdiRestConstants.FEEDBACK)
    suspend fun postDeploymentActionFeedback(
        @Path("tenant") tenant: String,
        @Path("controllerId") controllerId: String,
        @Path("actionId") actionId: String?,
        @Body feedback: DeploymentFeedbackRequest
    ): Response<Unit>

    /**
     * This is the feedback channel for the config data action.
     *
     * @param tenant
     * of the client
     * @param configData
     * as body
     * @param controllerId
     * to provide data for
     *
     * @return status of the request
     */
    @Headers("Content-Type: application/json")
    @PUT(value = DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/" +
            DdiRestConstants.CONFIG_DATA_ACTION)
    suspend fun putConfigData(
        @Path("tenant") tenant: String,
        @Path("controllerId") controllerId: String,
        @Body configData: ConfigurationDataRequest
    ): Response<Unit>

    /**
     * RequestMethod.GET method for the [DdiCancel] action.
     *
     * @param tenant
     * of the request
     * @param controllerId
     * ID of the calling target
     * @param actionId
     * of the action
     *
     * @return the [DdiCancel] response
     */
    @Headers("Accept: application/hal+json")
    @GET(value = DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/" + DdiRestConstants.CANCEL_ACTION +
            "/{actionId}")
    suspend fun getCancelActionDetails(
        @Path("tenant") tenant: String,
        @Path("controllerId") controllerId: String,
        @Path("actionId") actionId: String
    ): CancelActionResponse

    /**
     * RequestMethod.POST method receiving the [DdiActionFeedback] from
     * the target.
     *
     * @param tenant
     * of the client
     * @param feedback
     * the [DdiActionFeedback] from the target.
     * @param controllerId
     * the ID of the calling target
     * @param actionId
     * of the action we have feedback for
     *
     * @return the [DdiActionFeedback] response
     */

    @Headers("Content-Type: application/json")
    @POST(DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/" + DdiRestConstants.CANCEL_ACTION + "/{actionId}/" +
            DdiRestConstants.FEEDBACK)
    suspend fun postCancelActionFeedback(
        @Path("tenant") tenant: String,
        @Path("controllerId") controllerId: String,
        @Path("actionId") actionId: String?,
        @Body feedback: CancelFeedbackRequest
    ): Unit
}
