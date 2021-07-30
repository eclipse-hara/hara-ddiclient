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
import org.eclipse.hara.ddiclient.core.api.MessageListener
import org.eclipse.hara.ddiclient.virtualdevice.Configuration
import java.text.MessageFormat

class MessageListenerImpl(
    private val virtualDeviceId:Int,
    private val clientData: HaraClientData
): MessageListener {
    override fun onMessage(message: MessageListener.Message) {
        println(
            MessageFormat.format(
            Configuration.logMessageTemplate,
            virtualDeviceId,
            clientData.tenant,
            clientData.controllerId,
            clientData.gatewayToken,message))
    }
}