/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiapiclient.api.model

import com.google.gson.annotations.SerializedName
import org.joda.time.Instant

/*======================================================================================================================
 *==== REQUESTS ========================================================================================================
 *====================================================================================================================*/

data class CancelFeedbackRequest(
    val id: String,
    val time: String,
    val status: Status
) {
    data class Status(
            val execution: Execution,
            val result: Result,
            val details: List<String>
    ) {
        enum class Execution {
            closed,
            proceeding,
            canceled,
            scheduled,
            rejected,
            resumed }
        data class Result(
            val finished: Finished
        ) {
            enum class Finished {
                success,
                failure,
                none }
        }
    }
    companion object {
        fun newInstance(
                id: String,
                execution: Status.Execution,
                finished: Status.Result.Finished,
                vararg messages: String
        ): CancelFeedbackRequest {
            return CancelFeedbackRequest(id, Instant.now().toString(), Status(
                    execution,
                    Status.Result(
                            finished),
                    messages.toList()
            ))
        }
    }
}

data class DeploymentFeedbackRequest(
    val id: String,
    val time: String,
    val status: Status
) {
    data class Status(
            val execution: Execution,
            val result: Result,
            val details: List<String>
    ) {
        enum class Execution {
            closed,
            proceeding,
            canceled,
            scheduled,
            rejected,
            resumed }
        data class Result(
                val finished: Finished,
                val progress: Progress?
        ) {
            enum class Finished {
                success,
                failure,
                none }
            data class Progress(
                val of: Int,
                val cnt: Int
            )
        }
    }

    companion object {
        fun newInstance(
                id: String,
                execution: Status.Execution,
                progress: Status.Result.Progress,
                finished: Status.Result.Finished,
                vararg messages: String
        ): DeploymentFeedbackRequest {
            return DeploymentFeedbackRequest(id, Instant.now().toString(), Status(
                    execution,
                    Status.Result(
                            finished,
                            progress),
                    messages.toList()
            ))
        }
    }
}

data class ConfigurationDataRequest(
        val id: String,
        val time: String,
        val status: Status,
        val data: Map<String, String>?,
        val mode: Mode
) {
    data class Status(
            val execution: Execution,
            val result: Result,
            val details: List<String>
    ) {
        enum class Execution {
            closed,
            proceeding,
            canceled,
            scheduled,
            rejected,
            resumed }
        data class Result(
            val finished: Finished
        ) {
            enum class Finished {
                success,
                failure,
                none }
        }
    }
    enum class Mode {
        merge,
        replace,
        remove }

    companion object {
        fun of(map: Map<String, String>? = null, mode: Mode) = ConfigurationDataRequest(
                "",
                "20140511T121314", // Instant.now().toString(),
                Status(
                        Status.Execution.closed,
                        Status.Result(
                                Status.Result.Finished.success),
                                emptyList()
                        ),
                    map,
                    mode
                )
    }
}

/*======================================================================================================================
 *==== RESPONSES =======================================================================================================
 *====================================================================================================================*/

data class CancelActionResponse(
    val id: String,
    val cancelAction: CancelAction
) {
    data class CancelAction(
        val stopId: String
    )
}

data class ControllerBaseResponse(
        val config: Configuration,
        val _links: Links?
) {
    data class Configuration(
        val polling: Polling
    ) {
        data class Polling(
            val sleep: String
        )
    }
    data class Links(
            val deploymentBase: Link?,
            val cancelAction: Link?,
            val configData: Link?
    ) {
        data class Link(
            val href: String
        )
    }
    fun requireConfigData() = _links?.configData != null
    fun requireDeployment() = _links?.deploymentBase != null
    fun requireCancel() = _links?.cancelAction != null
    fun deploymentActionId() = xxxAid("deploymentBase")
    fun cancelActionId() = xxxAid("cancelAction")
    private fun xxxAid(pfx: String) =
            ".*$pfx/([a-zA-Z0-9_]+)"
            .toRegex()
            .find(_links?.deploymentBase?.href ?: _links?.cancelAction?.href ?: "")
            ?.destructured
            ?.component1()!!
}

data class DeploymentBaseResponse(
        val id: String,
        val deployment: Deployment,
        val actionHistory: ActionHistory?
) {
    data class Deployment(
            val download: ProvisioningType,
            val update: ProvisioningType,
            val maintenanceWindow: MaintenanceWindow,
            val chunks: Set<Chunks>
    ) {
        enum class ProvisioningType {
            skip,
            attempt,
            forced }
        enum class MaintenanceWindow {
            available,
            unavailable }
        data class Chunks(
                val metadata: Set<Metadata>?,
                val part: String,
                val name: String,
                val version: String,
                val artifacts: Set<Artifact>
        ) {
            data class Metadata(
                val key: String,
                val value: String
            )
            data class Artifact(
                    val filename: String,
                    val hashes: Hashes,
                    val size: Long,
                    val _links: Links
            ) {
                data class Hashes(
                    val sha1: String,
                    val md5: String
                )
                data class Links(
                        val download: Link?,
                        val md5sum: Link?,
                        @SerializedName("download-http")
                    val download_http: Link?,
                        @SerializedName("md5sum-http")
                    val md5sum_http: Link?
                ) {
                    data class Link(val href: String)
                }
            }
        }
    }
    data class ActionHistory(
            val status: Status?,
            val messages: List<String>
    ) {
        enum class Status {
            CANCELED,
            WARNING,
            ERROR,
            FINISHED,
            RUNNING }
    }
}

data class ArtifactResponse(
        val filename: String,
        val hashes: Hashes,
        val size: Long,
        val _links: Links
) {
    data class Hashes(
        val sha1: String,
        val md5: String
    )
    data class Links(
            val download: Link,
            val md5sum: Link,
            @SerializedName("download-http")
        val download_http: Link,
            @SerializedName("md5sum-http")
        val md5sum_http: Link
    ) {
        data class Link(val href: String)
    }
}
