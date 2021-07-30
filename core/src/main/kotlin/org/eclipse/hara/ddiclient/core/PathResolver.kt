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

import org.eclipse.hara.ddiclient.core.api.DirectoryForArtifactsProvider
import org.eclipse.hara.ddiclient.core.api.Updater
import java.io.File

class PathResolver(private val dfap: DirectoryForArtifactsProvider) {

    companion object {
        const val ROOT = "artifacts"
    }

    fun fromArtifact(id: String): (artifact: Updater.SwModule.Artifact) -> String {
        return { artifact ->
            File(dfap.directoryForArtifacts(), "$ROOT/$id/${artifact.hashes.md5}").absolutePath
        }
    }

    fun baseDirectory(): File {
        return File(dfap.directoryForArtifacts(), ROOT)
    }

    fun updateDir(actionId: String): File {
        return File(baseDirectory(), actionId)
    }
}
