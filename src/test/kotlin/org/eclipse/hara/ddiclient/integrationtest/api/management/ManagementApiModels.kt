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

import com.google.gson.annotations.SerializedName

data class ActionStatus(val content: Set<ContentEntry>, val total: Int = content.size,
                        val size: Int = content.size) {
    data class ContentEntry(val type: Type, val messages: List<String?>) {
        enum class Type {
            finished, error, warning, pending, running, canceled, retrieved, canceling, download
        }
    }
}

data class Action(val status: Status) {
    enum class Status {
        finished, pending
    }
}

data class ServerSystemConfig(val value: Any)

data class HawkbitAssignDistributionBody(
    val id: Int,
    val type: AssignDistributionType,
    @Suppress("SpellCheckingInspection")
    @SerializedName("forcetime")
    val forceTime: Long,
    val maintenanceWindow: MaintenanceWindow? = null)

data class HawkbitAssignDistributionResponse(
    val alreadyAssigned: Int,
    val assignedActions: List<AssignedAction>,
    val assigned: Int)


data class HawkbitTargetInfo(
    val name: String,
    val controllerId: String = name,
    val securityToken: String= "")

data class AssignedAction(val id: Int)

enum class AssignDistributionType {
    @SerializedName("soft")
    SOFT,
    @SerializedName("forced")
    FORCED,
    @SerializedName("timeforced")
    TIME_FORCED,
    @SerializedName("downloadonly")
    DOWNLOAD_ONLY,
}

data class MaintenanceWindow(val schedule: String?, val duration: String, val timezone: String)