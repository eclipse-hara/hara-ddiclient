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

/**
 * Update executor.
 */
interface Updater {

    /**
     * Messenger that sends messages to the update server
     */
    interface Messenger {

        /**
         * Method that sends message to the update server
         * @param msg message content to be sent to the update server
         */
        fun sendMessageToServer(vararg msg: String)
    }

    /**
     * Method that selects the software modules that the updater uses for the
     * update.
     * SwModsApplications are applied in ascending order of priority.
     *
     * @param swModules list of available software module.
     * @return [SwModsApplication]
     *
     */
    fun requiredSoftwareModulesAndPriority(swModules: Set<SwModule>): SwModsApplication =
            SwModsApplication(0,
                    swModules.map {
                        SwModsApplication.SwModule(
                                it.type,
                                it.name,
                                it.version,
                                it.artifacts.map { a -> a.hashes }.toSet())
                    }.toSet())

    /**
     * @return true if the update is cancellable, false otherwise
     */
    fun updateIsCancellable(): Boolean = true

    /**
     * Method that applies the software modules.
     * @param modules a set of software module to apply
     * @param messenger to send info to the update server
     * @return  an [UpdateResult] the result of the update.
     */
    fun apply(modules: Set<SwModuleWithPath>, messenger: Messenger): UpdateResult

    /**
     * Class that represents the result of an update
     * @param success true if the update is successfully installed, false otherwise
     * @param details additional info about the update result.
     */
    data class UpdateResult(val success: Boolean, val details: List<String> = emptyList())

    /**
     * Class that represents a software module
     * @param type of software module
     * @param metadata set of additional info about the software module
     * @param name of the software module
     * @param artifacts set of software module of the artifact
     */
    data class SwModule(
            val metadata: Set<Metadata>?,
            val type: String,
            val name: String,
            val version: String,
            val artifacts: Set<Artifact>
    ) {

        /**
         * Metadata of software module as [key] [value] value pair
         */
        data class Metadata(
            val key: String,
            val value: String
        )

        /**
         * Artifact of software module
         * @param filename of artifact
         * @param hashes of artifact
         * @param size of artifact in bytes
         */
        data class Artifact(
                val filename: String,
                val hashes: Hashes,
                val size: Long
        )
    }

    /**
     * Class that represents a software module where each artifact
     * is associated with a path
     * @param type of software module
     * @param metadata set of additional info about the software module
     * @param name of the software module
     * @param artifacts set of software module of the artifact
     */
    data class SwModuleWithPath(
            val metadata: Set<Metadata>?,
            val type: String,
            val name: String,
            val version: String,
            val artifacts: Set<Artifact>
    ) {
        /**
         * Metadata of software module as [key] [value] value pair
         */
        data class Metadata(
            val key: String,
            val value: String
        )

        /**
         * Artifact of software module
         * @param filename of artifact
         * @param hashes of artifact
         * @param size of artifact in bytes
         * @param path of the artifact
         */
        data class Artifact(
                val filename: String,
                val hashes: Hashes,
                val size: Long,
                val path: String
        )
    }

    /**
     *  A prioritize software module set.
     *  SwModApplications are applied in ascending order of
     *  the [priority] field
     *  @param priority defines the apply order of the SwModsApplication.
     *  @param swModules software modules that will be installed
     *
     */
    data class SwModsApplication(
        val priority: Int,
        val swModules: Set<SwModule> = emptySet()
    ) {
        /**
         * Class that represents a software module
         * @param type of software module
         * @param name of the software module
         * @param version of the software module
         * @param hashes set of artifact hashes, one for each software module artifact
         */
        data class SwModule(
            val type: String,
            val name: String,
            val version: String,
            val hashes: Set<Hashes>
        )
    }

    /**
     * Artifact hashes
     * @param sha1 artifact hash
     * @param md5 artifact hash
     */
    data class Hashes(
        val sha1: String,
        val md5: String
    )
}
