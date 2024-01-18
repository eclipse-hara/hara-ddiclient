/*
 * Copyright © 2017-2024  Kynetics  LLC
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.api

/**
 * Update executor.
 */
interface Updater {

    /**
     * Messenger that communicates with the Update Server
     */
    interface Messenger {

        /**
         * Method that sends a message to the Update Server
         * @param msg message content to send to the Update Server
         */
        fun sendMessageToServer(vararg msg: String)
    }

    /**
     * Method that selects the software modules that the [Updater] uses for the
     * update.
     * [SwModsApplication] are applied in ascending order of priority.
     *
     * @param swModules list of available software modules.
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
     * @param modules the set of software module to apply
     * @param messenger the messenger to send info to the Update Server
     * @return the result of the update
     */
    fun apply(modules: Set<SwModuleWithPath>, messenger: Messenger): UpdateResult

    /**
     * Class that represents the result of an update.
     * @property success true if the update is successfully installed, false otherwise
     * @property details additional info about the update result
     */
    data class UpdateResult(val success: Boolean, val details: List<String> = emptyList())

    /**
     * Class that represents a software module.
     * @property type
     * @property metadata set of additional info
     * @property name
     * @property version
     * @property artifacts set of artifacts
     */
    data class SwModule(
        val metadata: Set<Metadata>?,
        val type: String,
        val name: String,
        val version: String,
        val artifacts: Set<Artifact>
    ) {

        /**
         * Class that represents the metadata of a software module as [key] [value] value pair.
         */
        data class Metadata(
            val key: String,
            val value: String
        )

        /**
         * Class that represents an artifact of a software module.
         * @property filename
         * @property hashes
         * @property size in bytes
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
     * @property type
     * @property metadata set of additional info
     * @property name
     * @property version
     * @property artifacts set of artifacts
     */
    data class SwModuleWithPath(
        val metadata: Set<Metadata>?,
        val type: String,
        val name: String,
        val version: String,
        val artifacts: Set<Artifact>
    ) {
        /**
         * Class that represents the metadata of a software module as [key] [value] value pair.
         */
        data class Metadata(
            val key: String,
            val value: String
        )

        /**
         * Class that represents an artifact of a software module.
         * @property filename
         * @property hashes
         * @property size in bytes
         * @property path
         */
        data class Artifact(
            val filename: String,
            val hashes: Hashes,
            val size: Long,
            val path: String
        )
    }

    /**
     *  Class that represents a prioritized software module set.
     *  SwModApplications are applied in ascending order of
     *  the [priority] field
     *  @property priority defines the order of application of the SwModsApplication.
     *  @property swModules set of software modules that will be installed
     *
     */
    data class SwModsApplication(
        val priority: Int,
        val swModules: Set<SwModule> = emptySet()
    ) {
        /**
         * Class that represents a software module
         * @property type
         * @property name
         * @property version
         * @property hashes set of artifact hashes, one for each software module artifact
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
     * @property sha1 SHA1 hash
     * @property md5 MD5 hash
     */
    data class Hashes(
        val sha1: String,
        val md5: String
    )
}
