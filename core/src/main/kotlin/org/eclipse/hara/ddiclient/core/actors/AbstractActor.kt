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

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import org.eclipse.hara.ddiapiclient.api.DdiClient
import org.eclipse.hara.ddiclient.core.PathResolver
import org.eclipse.hara.ddiclient.core.UpdaterRegistry
import org.eclipse.hara.ddiclient.core.api.ConfigDataProvider
import org.eclipse.hara.ddiclient.core.api.DeploymentPermitProvider
import org.eclipse.hara.ddiclient.core.api.MessageListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

typealias Receive = suspend (Any) -> Unit

typealias ActorRef = SendChannel<Any>

@OptIn(ObsoleteCoroutinesApi::class)
typealias ActorScope = kotlinx.coroutines.channels.ActorScope<Any>

val EmptyReceive: Receive = {}

@OptIn(ObsoleteCoroutinesApi::class)
abstract class AbstractActor protected constructor(private val actorScope: ActorScope) : ActorScope by actorScope {

    private var __receive__: Receive = EmptyReceive

    private val childs: MutableMap<String, ActorRef> = emptyMap<String, ActorRef>().toMutableMap()

    protected fun child(name: String) = childs[name]

    protected fun become(receive: Receive) { __receive__ = receive }

    protected val LOG = LoggerFactory.getLogger(this::class.java)

    protected fun unhandled(msg: Any) {
        if (LOG.isWarnEnabled) {
            LOG.warn("received unexpected message $msg in ${coroutineContext[CoroutineName]} actor")
        }
    }

    protected val parent: ActorRef? = coroutineContext[ParentActor]?.ref

    protected val name: String = coroutineContext[CoroutineName]!!.name

    protected open fun beforeCloseChannel() {
        childs.forEach { (_, c) -> c.close() }
    }

    protected fun forEachActorNode(ope: (ActorRef) -> Unit) {
            childs.forEach { (_, actorRef) -> ope(actorRef) }
    }

    override val channel: Channel<Any> = object : Channel<Any> by actorScope.channel {
        override suspend fun send(element: Any) {
            LOG.debug("Send message {} to actor {}.", element.javaClass.simpleName, name)
            actorScope.channel.send(element)
        }

        override fun close(cause: Throwable?): Boolean {
            beforeCloseChannel()
            return actorScope.channel.close(cause)
        }
    }

    protected fun <T : AbstractActor> actorOf(
        name: String,
        context: CoroutineContext = EmptyCoroutineContext,
        capacity: Int = 3,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        onCompletion: CompletionHandler? = null,
        block: suspend (ActorScope) -> T
    ): ActorRef {
        val childRef = actorScope.actor<Any>(
                Dispatchers.IO.plus(CoroutineName(name)).plus(ParentActor(this.channel)).plus(context),
                capacity, start, onCompletion) { __workflow__(LOG, block)() }
        childs.put(name, childRef)
        return childRef
    }

    companion object {

        private fun <T : AbstractActor> __workflow__(logger: Logger, block: suspend (ActorScope) -> T): suspend ActorScope.() -> Unit = {
            try {
                val actor = block(this)
                actor.LOG.info("Actor {} created.", actor.name)
                try {
                    for (message in channel) { actor.__receive__(message) }
                    actor.LOG.info("Actor {} exiting.", actor.name)
                } catch (t: Throwable) {
                    actor.LOG.error("Error processing message in actor {}. error: {} message: {}", actor.name, t.javaClass, t.message)
                    actor.LOG.debug(t.message, t)
                    if (actor.parent != null) {
                        actor.parent.send(ActorException(actor.name, actor.channel, t))
                    } else {
                        throw t
                    }
                }
            } catch (t: Throwable) {
                val name = coroutineContext[CoroutineName]?.name
                val parent = coroutineContext[ParentActor]?.ref
                logger.error("Error creating actor ${name ?: "Unknown"}. error: ${t.javaClass} message: ${t.message}")
                if (parent != null) {
                    parent.send(ActorCreationException(name ?: "Unknown", channel, t))
                } else {
                    throw t
                }
            }
        }

        @OptIn(DelicateCoroutinesApi::class)
        fun <T : AbstractActor> actorOf(
            name: String,
            context: CoroutineContext = EmptyCoroutineContext,
            capacity: Int = 3,
            start: CoroutineStart = CoroutineStart.DEFAULT,
            onCompletion: CompletionHandler? = null,
            block: suspend (ActorScope) -> T
        ): ActorRef =
                GlobalScope.actor(Dispatchers.IO.plus(CoroutineName(name)).plus(context), capacity, start, onCompletion) {
                    __workflow__(LoggerFactory.getLogger(AbstractActor::class.java), block)()
                }
    }
}

data class HaraClientContext(
        val ddiClient: DdiClient,
        val registry: UpdaterRegistry,
        val configDataProvider: ConfigDataProvider,
        val pathResolver: PathResolver,
        val deploymentPermitProvider: DeploymentPermitProvider,
        val messageListeners: List<MessageListener>
) : AbstractCoroutineContextElement(HaraClientContext) {
    companion object Key : CoroutineContext.Key<HaraClientContext>
    override fun toString(): String = "HaraClientContext($this)"
}

data class ParentActor(val ref: ActorRef) : AbstractCoroutineContextElement(ParentActor) {
    companion object Key : CoroutineContext.Key<ParentActor>
    override fun toString(): String = "ParentActor($ref)"
}

class ActorException(val actorName: String, val actorRef: ActorRef, throwable: Throwable) : Exception(throwable)
class ActorCreationException(val actorName: String, val actorRef: ActorRef, throwable: Throwable) : Exception(throwable)
