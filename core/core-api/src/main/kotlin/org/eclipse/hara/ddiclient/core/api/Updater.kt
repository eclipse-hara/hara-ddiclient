/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.core.api

interface Updater {

    interface Messenger {
        fun sendMessageToServer(vararg msg: String)
    }

    fun requiredSoftwareModulesAndPriority(swModules: Set<SwModule>): SwModsApplication =
            SwModsApplication(0,
                    swModules.map {
                        SwModsApplication.SwModule(
                                it.type,
                                it.name,
                                it.version,
                                it.artifacts.map { a -> a.hashes }.toSet())
                    }.toSet())

    fun updateIsCancellable(): Boolean = true

    /**
     * @return true if succesfully applied
     */
    fun apply(modules: Set<SwModuleWithPath>, messenger: Messenger): UpdateResult

    data class UpdateResult(val success: Boolean, val details: List<String> = emptyList())
    data class SwModule(
            val metadata: Set<Metadata>?,
            val type: String,
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
                val size: Long
        )
    }

    data class SwModuleWithPath(
            val metadata: Set<Metadata>?,
            val type: String,
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
                val path: String
        )
    }

    data class SwModsApplication(
        val priority: Int,
        val swModules: Set<SwModule> = emptySet()
    ) {
        data class SwModule(
            val type: String,
            val name: String,
            val version: String,
            val hashes: Set<Hashes>
        )
    }

    data class Hashes(
        val sha1: String,
        val md5: String
    )
}
