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

import java.io.File

/**
 * Provides the directory used by the client
 * to store the artifacts downloaded during the
 * updates.
 */
interface DirectoryForArtifactsProvider {

    /**
     * @return the base directory used to store
     * the artifacts
     */
    fun directoryForArtifacts(): File
}
