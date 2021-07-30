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
import java.io.InputStream

typealias OnResourceChange<T> = suspend (T, String) -> Unit

interface DdiClient {

    suspend fun getControllerActions(): ControllerBaseResponse

    suspend fun onControllerActionsChange(etag: String = "", onChange: OnResourceChange<ControllerBaseResponse>)

    suspend fun getDeploymentActionDetails(actionId: String, historyCount: Int = -1): DeploymentBaseResponse

    suspend fun onDeploymentActionDetailsChange(actionId: String, historyCount: Int = -1, etag: String = "", onChange: OnResourceChange<DeploymentBaseResponse>)

    suspend fun getCancelActionDetails(actionId: String): CancelActionResponse

    suspend fun getSoftwareModulesArtifacts(softwareModuleId: String): List<ArtifactResponse>

    suspend fun postDeploymentActionFeedback(actionId: String, feedback: DeploymentFeedbackRequest)

    suspend fun postCancelActionFeedback(actionId: String, feedback: CancelFeedbackRequest)

    suspend fun putConfigData(data: ConfigurationDataRequest, onSuccessConfigData: () -> Unit)

    suspend fun downloadArtifact(url: String): InputStream
}
