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

import org.eclipse.hara.ddiclient.core.api.HaraClientData
import org.eclipse.hara.ddiclient.core.api.Updater
import org.eclipse.hara.ddiclient.virtualdevice.Configuration
import java.text.MessageFormat

class UpdaterImpl(
    private val virtualDeviceId:Int,
    private val clientData: HaraClientData
): Updater {
    override fun apply(
            modules: Set<Updater.SwModuleWithPath>,
            messenger: Updater.Messenger
    ): Updater.UpdateResult {
        println("APPLY UPDATE $modules")
        val regex = Regex("VIRTUAL_DEVICE_UPDATE_RESULT_(\\*|${clientData.controllerId})")
        val result = modules.fold (Pair(true, listOf<String>())) { acc, module->

            val command = (module.metadata?.firstOrNull{md -> md.key.contains(regex)}?.value ?: "OK|1|").split("|")

            messenger.sendMessageToServer(
                MessageFormat.format(
                    Configuration.srvMsgTemplateBeforeUpdate,
                    module.name,
                    virtualDeviceId,
                    clientData.tenant,
                    clientData.controllerId,
                    clientData.gatewayToken)
            )
            Thread.sleep(command[1].toLong() * 1000)
            messenger.sendMessageToServer(
                MessageFormat.format(
                    Configuration.srvMsgTemplateAfterUpdate,
                    module.name,
                    virtualDeviceId,
                    clientData.tenant,
                    clientData.controllerId,
                    clientData.gatewayToken)
            )

            (acc.first && command[0] == "OK") to (acc.second + command.drop(2))
        }


        return Updater.UpdateResult(result.first, result.second)
    }
}