/*
 * Copyright © 2017-2023  Kynetics  LLC
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.virtualdevice.entrypoint

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.eclipse.hara.ddiclient.api.HaraClientData
import org.eclipse.hara.ddiclient.api.Updater
import org.eclipse.hara.ddiclient.virtualdevice.Configuration
import java.text.MessageFormat

class UpdaterImpl(
    private val virtualDeviceId:Int,
    private val clientData: HaraClientData
): Updater {
    override fun apply(
        modules: Set<Updater.SwModuleWithPath>,
        messenger: Updater.Messenger
    ): Updater.UpdateResult = runBlocking {
        println("APPLY UPDATE $modules")
        val regex = Regex("VIRTUAL_DEVICE_UPDATE_RESULT_(\\*|${clientData.controllerId})")
        val result = modules.fold (Pair(true, listOf<String>())) { acc, module->

            val command = (module.metadata?.firstOrNull { md -> md.key.contains(regex) }?.value ?: "OK|1|")
                .split("|")
                .filter { it.isNotEmpty() }
                .run {
                    if (serverInstructionIsValid(this)) {
                        this
                    } else {
                        listOf("OK", "1", "Invalid configuration from server ${this.joinToString("|")}")
                    }
                }

            messenger.sendMessageToServer(
                MessageFormat.format(
                    Configuration.srvMsgTemplateBeforeUpdate,
                    module.name,
                    virtualDeviceId,
                    clientData.tenant,
                    clientData.controllerId,
                    clientData.gatewayToken)
            )

            delay(command[1].toLong() * 1000)

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

        Updater.UpdateResult(result.first, result.second)
    }


    private fun serverInstructionIsValid(instruction: List<String>):Boolean{
        return when{
            instruction.size < 2 -> false
            instruction[0] != "OK" && instruction[0] != "KO" -> false
            instruction[1].runCatching { toLong() }.isFailure -> false
            else -> true
        }
    }
}
