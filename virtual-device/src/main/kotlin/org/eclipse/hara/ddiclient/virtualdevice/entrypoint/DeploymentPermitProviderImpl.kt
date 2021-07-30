/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.virtualdevice.entrypoint

import org.eclipse.hara.ddiclient.core.api.DeploymentPermitProvider
import org.eclipse.hara.ddiclient.virtualdevice.Configuration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

class DeploymentPermitProviderImpl: DeploymentPermitProvider {
    override fun downloadAllowed(): Deferred<Boolean> {
        return CompletableDeferred(Configuration.grantDownload)
    }

    override fun updateAllowed(): Deferred<Boolean> {
        return CompletableDeferred(Configuration.grantUpdate)
    }
}