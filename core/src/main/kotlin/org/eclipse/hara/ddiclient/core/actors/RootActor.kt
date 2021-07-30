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

import org.eclipse.hara.ddiclient.core.actors.ConnectionManager.Companion.Message.In
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ObsoleteCoroutinesApi

@OptIn(ObsoleteCoroutinesApi::class)
class RootActor
private constructor(scope: ActorScope) : AbstractActor(scope) {

    private fun mainReceive(): Receive = { msg ->
        when (msg) {
            is In.Start, In.ForcePing -> child("connectionManager")!!.send(msg)

            is In.Stop -> {
                child("connectionManager")!!.send(msg)
                channel.close()
            }

            else -> unhandled(msg)
        }
    }

    init {
        val nmActor = actorOf("notificationManager") { NotificationManager.of(it) }
        val cmActor = actorOf("connectionManager", NMActor(nmActor)) { ConnectionManager.of(it) }
        val ctxt = CMActor(cmActor).plus(NMActor(nmActor))
        actorOf("actionManager", ctxt) { ActionManager.of(it) }
        become(mainReceive())
    }

    companion object {
        fun of(scope: ActorScope) = RootActor(scope)
    }
}

data class CMActor(val ref: ActorRef) : AbstractCoroutineContextElement(CMActor) {
    companion object Key : CoroutineContext.Key<CMActor>
    override fun toString(): String = "CMActor($ref)"
}

data class NMActor(val ref: ActorRef) : AbstractCoroutineContextElement(NMActor) {
    companion object Key : CoroutineContext.Key<NMActor>
    override fun toString(): String = "NMActor($ref)"
}
