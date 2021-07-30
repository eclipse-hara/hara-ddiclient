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

import org.eclipse.hara.ddiapiclient.api.DdiClient
import org.eclipse.hara.ddiapiclient.api.model.ConfigurationDataRequest
import org.eclipse.hara.ddiapiclient.api.model.CancelActionResponse
import org.eclipse.hara.ddiapiclient.api.model.CancelFeedbackRequest
import org.eclipse.hara.ddiapiclient.api.model.ControllerBaseResponse
import org.eclipse.hara.ddiapiclient.api.model.DeploymentBaseResponse
import org.eclipse.hara.ddiapiclient.api.model.DeploymentFeedbackRequest
import org.eclipse.hara.ddiclient.core.actors.ConnectionManager.Companion.Message.In
import org.eclipse.hara.ddiclient.core.actors.ConnectionManager.Companion.Message.Out
import org.eclipse.hara.ddiclient.core.actors.ConnectionManager.Companion.Message.Out.Err.ErrMsg
import org.eclipse.hara.ddiclient.core.api.MessageListener
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.joda.time.Duration
import org.joda.time.Instant
import java.util.Timer
import kotlin.concurrent.timer

@OptIn(ObsoleteCoroutinesApi::class)
class ConnectionManager
private constructor(scope: ActorScope) : AbstractActor(scope) {

    private val client: DdiClient = coroutineContext[HaraClientContext]!!.ddiClient
    private val notificationManager = coroutineContext[NMActor]!!.ref
    private val configDataProvider = coroutineContext[HaraClientContext]!!.configDataProvider

    private fun stoppedReceive(state: State): Receive = { msg ->
        when (msg) {

            is In.Start -> become(runningReceive(startPing(state)))

            is In.Stop -> {}

            is In.Register -> become(stoppedReceive(state.withReceiver(msg.listener)))

            is In.Unregister -> become(stoppedReceive(state.withoutReceiver(msg.listener)))

            is In.SetPing -> become(stoppedReceive(state.copy(clientPingInterval = msg.duration, lastPing = Instant.EPOCH)))

            else -> unhandled(msg)
        }
    }

    private fun runningReceive(state: State): Receive = { msg ->
        when (msg) {

            is In.Start -> {}

            is In.Stop -> become(stoppedReceive(stopPing(state)))

            is In.Register -> become(runningReceive(state.withReceiver(msg.listener)))

            is In.Unregister -> become(runningReceive(state.withoutReceiver(msg.listener)))

            is In.SetPing -> become(runningReceive(startPing(state.copy(clientPingInterval = msg.duration, lastPing = Instant.EPOCH))))

            is In.ForcePing -> {
                become(runningReceive(state.copy(controllerBaseEtag = "", deploymentEtag = "")))
                channel.send(In.SetPing(null))
            }

            is In.Ping -> onPing(state)

            is In.DeploymentFeedback -> {
                exceptionHandler(state) {
                    client.postDeploymentActionFeedback(msg.feedback.id, msg.feedback)
                }
            }

            is In.CancelFeedback -> {
                exceptionHandler(state) {
                    client.postCancelActionFeedback(msg.feedback.id, msg.feedback)
                }
            }

            is In.ConfigDataFeedback -> {
                exceptionHandler(state) {
                    client.putConfigData(msg.cfgDataReq) {
                        configDataProvider.onConfigDataUpdate()
                    }
                }
            }

            else -> {
                unhandled(msg)
            }
        }
    }

    private suspend fun onControllerBaseChange(state: State, s: State, res: ControllerBaseResponse, newControllerBaseEtag: String) {
        if (res.requireConfigData() || !configDataProvider.isUpdated()) {
            this.send(Out.ConfigDataRequired, state)
        }

        var actionFound = false
        var etag = state.deploymentEtag
        if (res.requireDeployment()) {
            notificationManager.send(MessageListener.Message.Event.UpdateAvailable(res.deploymentActionId()))
            client.onDeploymentActionDetailsChange(res.deploymentActionId(), 0, state.deploymentEtag) { deplBaseResp, newDeploymentEtag ->
                etag = newDeploymentEtag
                this.send(Out.DeploymentInfo(deplBaseResp, state.deploymentEtag.isEmpty()), state)
            }
            actionFound = true
        }

        if (res.requireCancel()) {
            val res2 = client.getCancelActionDetails(res.cancelActionId())
            this.send(Out.DeploymentCancelInfo(res2), state)
            actionFound = true
        }

        if (!actionFound) {
            this.send(Out.NoAction, state)
        }

        val newState = s.copy(controllerBaseEtag = newControllerBaseEtag, deploymentEtag = etag)
                .withServerSleep(res.config.polling.sleep)
                .withoutBackoff()
        become(runningReceive(startPing(newState)))
    }

    private suspend fun onPing(state: State) {
        LOG.info("Execute ping calls to the server...")
        val s = state.copy(lastPing = Instant.now())
        try {

            notificationManager.send(MessageListener.Message.Event.Polling)

            client.onControllerActionsChange(state.controllerBaseEtag) { res, newEtag ->
                onControllerBaseChange(state, s, res, newEtag)
            }
        } catch (t: Throwable) {
            fun loopMsg(t: Throwable): String = t.message + if (t.cause != null) " ${loopMsg(t.cause!!)}" else ""
            val errorDetails = "exception: ${t.javaClass} message: ${loopMsg(t)}"
            this.send(ErrMsg(errorDetails), state)
            LOG.warn(t.message, t)
            become(runningReceive(startPing(s.nextBackoff())))
            notificationManager.send(MessageListener.Message.Event.Error(listOf(errorDetails)))
        }
    }

    private suspend fun exceptionHandler(state: State, function: suspend () -> Unit) {
        try {
            function.invoke()
        } catch (t: Throwable) {
            this.send(ErrMsg("exception: ${t.javaClass}" + if (t.message != null) " message: ${t.message}" else ""), state)
            LOG.warn(t.message, t)
        }
    }

    private fun startPing(state: State): State {
        val now = Instant.now()
        val elapsed = Duration(state.lastPing, now)
        val timer = timer(name = "Polling",
                initialDelay = Math.max(state.pingInterval.minus(elapsed).millis, 0),
                period = Math.max(state.pingInterval.millis, 5_000)) {
            launch {
                channel.send(In.Ping)
            }
        }
        return stopPing(state).copy(timer = timer)
    }

    private fun stopPing(state: State): State = if (state.timer != null) {
        state.timer.cancel()
        state.copy(timer = null)
    } else {
        state
    }

    private suspend fun send(msg: Out, state: State) {
        state.receivers.forEach { it.send(msg) }
    }

    init {
        become(stoppedReceive(State()))
    }

    companion object {
        fun of(scope: ActorScope) = ConnectionManager(scope)

        private data class State(
            val serverPingInterval: Duration = Duration.standardSeconds(0),
            val clientPingInterval: Duration? = null,
            val backoffPingInterval: Duration? = null,
            val lastPing: Instant? = Instant.EPOCH,
            val deploymentEtag: String = "",
            val controllerBaseEtag: String = "",
            val timer: Timer? = null,
            val receivers: Set<ActorRef> = emptySet()
        ) {
            val pingInterval = when {
                backoffPingInterval != null -> backoffPingInterval
                clientPingInterval != null -> clientPingInterval
                else -> serverPingInterval
            }
            fun nextBackoff() = if (backoffPingInterval == null)
                this.copy(backoffPingInterval = Duration.standardSeconds(1))
            else this.copy(backoffPingInterval = minOf(backoffPingInterval.multipliedBy(2), Duration.standardMinutes(1)))

            fun withoutBackoff() = if (backoffPingInterval != null) this.copy(backoffPingInterval = null) else this

            fun withServerSleep(sleep: String): State {
                fun sleepStr2duration(str: String): Duration {
                    val fields = str.split(':').map { Integer.parseInt(it).toLong() }.toTypedArray()
                    return Duration.standardHours(fields[0]).plus(
                            Duration.standardMinutes(fields[1])).plus(
                            Duration.standardSeconds(fields[2]))
                }
                val newServerPingInterval = sleepStr2duration(sleep)
                return if (newServerPingInterval != serverPingInterval) this.copy(serverPingInterval = newServerPingInterval)
                else this
            }
            fun withReceiver(receiver: ActorRef) = this.copy(receivers = receivers + receiver)

            fun withoutReceiver(receiver: ActorRef) = this.copy(receivers = receivers - receiver)
        }

        sealed class Message {

            sealed class In : Message() {
                object Start : In()
                object Stop : In()
                object Ping : In()
                object ForcePing : In()
                data class Register(val listener: ActorRef) : In()
                data class Unregister(val listener: ActorRef) : In()
                data class SetPing(val duration: Duration?) : In()
                data class DeploymentFeedback(val feedback: DeploymentFeedbackRequest)
                data class CancelFeedback(val feedback: CancelFeedbackRequest)
                data class ConfigDataFeedback(val cfgDataReq: ConfigurationDataRequest)
            }

            open class Out : Message() {
                object ConfigDataRequired : Out()
                data class DeploymentInfo(val info: DeploymentBaseResponse, val forceAuthRequest:Boolean  = false) : Out()
                data class DeploymentCancelInfo(val info: CancelActionResponse) : Out()

                object NoAction : Out()

                sealed class Err : Out() {
                    data class ErrMsg(val message: String) : Err()
                }
            }
        }
    }
}
