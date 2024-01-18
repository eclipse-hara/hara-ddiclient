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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/**
 * Provider of permit used to authorize action
 * during a soft update (not forced)
 */
interface DeploymentPermitProvider {

    /**
     * Provides the permit for the start download phase.
     * @return a [Deferred] that is completed when the user
     * allows (true) or denies (false) the client to
     * download the artifacts.
     */
    fun downloadAllowed(): Deferred<Boolean> = CompletableDeferred(true)

    /**
     * Provides the permit for the start of the update phase.
     * @return a [Deferred] that is completed when the user
     * allows (true) or denies (false) the client to
     * apply the downloaded artifacts.
     */
    fun updateAllowed(): Deferred<Boolean> = CompletableDeferred(true)
}
