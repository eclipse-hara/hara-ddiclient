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

/**
 * Constants for the direct device integration rest resources.
 *
 * @author Daniele Sergio
 */
class DdiRestConstants private constructor() {

    companion object {

        /**
         * The base URL mapping of the direct device integration rest resources.
         */
        const val BASE_V1_REQUEST_MAPPING = "/{tenant}/controller/v1"

        /**
         * Deployment action resources.
         */
        const val DEPLOYMENT_BASE_ACTION = "deploymentBase"

        /**
         * Cancel action resources.
         */
        const val CANCEL_ACTION = "cancelAction"

        /**
         * Feedback channel.
         */
        const val FEEDBACK = "feedback"

        /**
         * File suffix for MDH hash download (see Linux md5sum).
         */
        const val ARTIFACT_MD5_DWNL_SUFFIX = ".MD5SUM"

        /**
         * Config data action resources.
         */
        const val CONFIG_DATA_ACTION = "configData"

        /**
         * Default value specifying that no action history to be sent as part of
         * response to deploymentBase
         * [DdiRestApi.getDeploymentActionDetails].
         */
        const val NO_ACTION_HISTORY = 0

        const val DEFAULT_RESOURCE = -1
    }
}
