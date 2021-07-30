/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.core

import org.eclipse.hara.ddiapiclient.api.model.DeploymentBaseResponse
import org.eclipse.hara.ddiclient.core.api.Updater

class UpdaterRegistry(private vararg val updaters: Updater) {

    fun allRequiredArtifactsFor(chunks: Set<DeploymentBaseResponse.Deployment.Chunks>): Set<Updater.Hashes> =
            updaters.flatMap { u ->
                u.requiredSoftwareModulesAndPriority(chunks.map { convert(it) }.toSet())
                        .swModules.flatMap { it.hashes } }.toSet()

    fun allUpdatersWithSwModulesOrderedForPriority(chunks: Set<DeploymentBaseResponse.Deployment.Chunks>): Set<UpdaterWithSwModule> {

        val swModules = chunks.map { convert(it) }.toSet()

        return updaters.map { u ->
            val appl = u.requiredSoftwareModulesAndPriority(swModules)
            UpdaterWithSwModule(appl.priority, u, appl.swModules.map { swm ->
                swModules.find {
                    with(swm) {
                        it.name == name &&
                                it.type == type &&
                                it.version == version
                    }
                }!!
            }.toSet())
        }.toSortedSet(Comparator { p1, p2 -> p1.priority.compareTo(p2.priority) })
    }

    fun currentUpdateIsCancellable(): Boolean {
        return updaters.map { it.updateIsCancellable() }
                .reduce { acc, value -> acc && value }
    }

    data class UpdaterWithSwModule(val priority: Int, val updater: Updater, val softwareModules: Set<Updater.SwModule>)

    private fun convert(cnk: DeploymentBaseResponse.Deployment.Chunks): Updater.SwModule =
            Updater.SwModule(
                    cnk.metadata?.map { convert(it) }?.toSet(),
                    cnk.part,
                    cnk.name,
                    cnk.version,
                    cnk.artifacts.map { convert(it) }.toSet())

    private fun convert(mtdt: DeploymentBaseResponse.Deployment.Chunks.Metadata): Updater.SwModule.Metadata =
            Updater.SwModule.Metadata(
                    mtdt.key,
                    mtdt.value)

    private fun convert(artfct: DeploymentBaseResponse.Deployment.Chunks.Artifact): Updater.SwModule.Artifact =
            Updater.SwModule.Artifact(
                    artfct.filename,
                    Updater.Hashes(
                            artfct.hashes.sha1,
                            artfct.hashes.md5),
                    artfct.size)
}
