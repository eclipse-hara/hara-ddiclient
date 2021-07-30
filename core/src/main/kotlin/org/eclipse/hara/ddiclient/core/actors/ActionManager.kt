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

import org.eclipse.hara.ddiapiclient.api.model.ConfigurationDataRequest
import org.eclipse.hara.ddiapiclient.api.model.CancelFeedbackRequest
import org.eclipse.hara.ddiclient.core.actors.ConnectionManager.Companion.Message.In
import org.eclipse.hara.ddiclient.core.actors.ConnectionManager.Companion.Message.Out
import org.eclipse.hara.ddiclient.core.actors.ConnectionManager.Companion.Message.Out.Err.ErrMsg
import org.eclipse.hara.ddiclient.core.api.MessageListener
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.joda.time.Duration

@OptIn(ObsoleteCoroutinesApi::class)
class ActionManager
private constructor(scope: ActorScope) : AbstractActor(scope) {

    private val registry = coroutineContext[HaraClientContext]!!.registry
    private val configDataProvider = coroutineContext[HaraClientContext]!!.configDataProvider
    private val connectionManager = coroutineContext[CMActor]!!.ref
    private val notificationManager = coroutineContext[NMActor]!!.ref

    private fun defaultReceive(state: State): Receive = { msg ->
        when {

            msg is Out.ConfigDataRequired -> {
                val map = configDataProvider.configData()

                if (map.isNotEmpty()) {
                    val cdr = ConfigurationDataRequest.of(map, ConfigurationDataRequest.Mode.merge)
                    connectionManager.send(In.ConfigDataFeedback(cdr))
                } else {
                    LOG.info("Config data required ignored because of map is empty")
                }
            }

            msg is Out.DeploymentInfo -> onDeployment(msg, state)

            msg is DeploymentManager.Companion.Message.DownloadFailed -> {
                become(defaultReceive(state.copy(deployment = null)))
                child("deploymentManager")!!.close()
            }

            msg is DeploymentManager.Companion.Message.UpdateFailed ||
                    msg is DeploymentManager.Companion.Message.UpdateFinished -> {
                LOG.info(msg.javaClass.simpleName)
                become(defaultReceive(state.copy(deployment = null)))
                child("deploymentManager")!!.close()
                LOG.info("Restore server ping interval")
                connectionManager.send(In.SetPing(null))
            }

            msg is Out.DeploymentCancelInfo -> onCancelInfo(msg, state)

            msg is Message.UpdateStopped -> {
                LOG.info("update stopped")
                become(defaultReceive(state.copy(deployment = null)))
                LOG.info("Restore server ping interval")
                connectionManager.send(In.SetPing(null))
            }

            state.inDeployment && msg is Out.NoAction -> {
                LOG.warn("CancelForced/RemoveTarget.")
                child("deploymentManager")!!.send(Message.CancelForced)
            }

            msg is Out.NoAction -> onNoAction(state)

            msg is ErrMsg -> {
                LOG.warn("ErrMsg. Not yet implemented")
            }

            else -> unhandled(msg)
        }
    }

    private suspend fun onDeployment(msg: Out.DeploymentInfo, state: State) {
        when {
            state.inDeployment(msg.info.id) -> child("deploymentManager")!!.send(msg)

            state.inDeployment -> child("deploymentManager")!!.send(Message.CancelForced)

            else -> {
                val deploymentManager = actorOf("deploymentManager") { DeploymentManager.of(it) }
                become(defaultReceive(state.copy(deployment = msg)))
                deploymentManager.send(msg)
                LOG.info("DeploymentInfo msg, decreased ping interval to be reactive on server requests (ping: 30s)")
                connectionManager.send(In.SetPing(Duration.standardSeconds(30)))
            }
        }
    }

    private suspend fun onCancelInfo(msg: Out.DeploymentCancelInfo, state: State) {
        when {
            !state.inDeployment && registry.currentUpdateIsCancellable() -> {
                connectionManager.send(In.CancelFeedback(
                        CancelFeedbackRequest.newInstance(msg.info.cancelAction.stopId,
                                CancelFeedbackRequest.Status.Execution.closed,
                                CancelFeedbackRequest.Status.Result.Finished.success)))
                notificationManager.send(MessageListener.Message.State.CancellingUpdate)
                connectionManager.send(In.SetPing(null))
            }

            !registry.currentUpdateIsCancellable() -> {
                connectionManager.send(In.CancelFeedback(
                        CancelFeedbackRequest.newInstance(msg.info.cancelAction.stopId,
                                CancelFeedbackRequest.Status.Execution.rejected,
                                CancelFeedbackRequest.Status.Result.Finished.success,
                                "Update already started. Can't be stopped.")))
            }

            else -> {
                LOG.warn("DeploymentCancelInfo")
                child("deploymentManager")!!.send(msg)
                LOG.info("Restore server ping interval")
                connectionManager.send(In.SetPing(null))
            }
        }
    }

    private suspend fun onNoAction(state: State) {
        when {
            state.inDeployment -> {
                LOG.warn("CancelForced/RemoveTarget.")
                child("deploymentManager")!!.send(Message.CancelForced)
            }

            else -> {
                notificationManager.send(MessageListener.Message.State.Idle)
            }
        }
    }
    init {
        become(defaultReceive(State()))
        runBlocking { connectionManager.send(In.Register(channel)) }
    }

    companion object {
        fun of(scope: ActorScope) = ActionManager(scope)

        data class State(val deployment: Out.DeploymentInfo? = null) {
            val inDeployment = deployment != null
            fun inDeployment(id: String) = inDeployment && deployment!!.info.id == id
        }

        sealed class Message {

            object CancelForced : Message()
            object UpdateStopped : Message()
        }
    }
}
