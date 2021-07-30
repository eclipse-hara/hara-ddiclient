/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.core.actors

import org.eclipse.hara.ddiclient.core.api.MessageListener
import kotlinx.coroutines.ObsoleteCoroutinesApi

@OptIn(ObsoleteCoroutinesApi::class)
class NotificationManager
private constructor(scope: ActorScope) : AbstractActor(scope) {

    private val listeners = coroutineContext[HaraClientContext]!!.messageListeners

    private fun defaultReceive(): Receive = { msg ->

        when (msg) {

            is MessageListener.Message -> listeners.forEach { it.onMessage(msg) }

            else -> unhandled(msg)
        }
    }

    init {
        become(defaultReceive())
    }

    companion object {
        fun of(scope: ActorScope) = NotificationManager(scope)
    }
}
