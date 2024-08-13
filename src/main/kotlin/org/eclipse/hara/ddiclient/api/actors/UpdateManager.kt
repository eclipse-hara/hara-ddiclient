/*
 * Copyright © 2017-2024  Kynetics, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.api.actors

import kotlinx.coroutines.Job
import org.eclipse.hara.ddi.api.model.DeploymentFeedbackRequest
import org.eclipse.hara.ddiclient.api.UpdaterRegistry
import org.eclipse.hara.ddiclient.api.actors.ConnectionManager.Companion.Message.In.DeploymentFeedback
import org.eclipse.hara.ddiclient.api.actors.ConnectionManager.Companion.Message.Out.DeploymentInfo
import org.eclipse.hara.ddiclient.api.MessageListener
import org.eclipse.hara.ddiclient.api.Updater
import org.eclipse.hara.ddi.api.model.DeploymentFeedbackRequest.Status.Result.Progress
import org.eclipse.hara.ddi.api.model.DeploymentFeedbackRequest.Status.Execution
import org.eclipse.hara.ddi.api.model.DeploymentFeedbackRequest.Status.Execution.proceeding
import org.eclipse.hara.ddi.api.model.DeploymentFeedbackRequest.Status.Execution.closed
import org.eclipse.hara.ddi.api.model.DeploymentFeedbackRequest.Status.Result.Finished
import org.eclipse.hara.ddi.api.model.DeploymentFeedbackRequest.Status.Result.Finished.none
import org.eclipse.hara.ddi.api.model.DeploymentFeedbackRequest.Status.Result.Finished.success
import org.eclipse.hara.ddi.api.model.DeploymentFeedbackRequest.Status.Result.Finished.failure
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.hara.ddi.api.model.CancelFeedbackRequest
import org.eclipse.hara.ddi.api.model.DeploymentBaseResponse
import org.eclipse.hara.ddiclient.api.DeploymentPermitProvider

@OptIn(ObsoleteCoroutinesApi::class)
class UpdateManager
private constructor(scope: ActorScope) : AbstractActor(scope) {

    private val registry = coroutineContext[HaraClientContext]!!.registry
    private val softRequest: DeploymentPermitProvider = coroutineContext[HaraClientContext]!!.softDeploymentPermitProvider
    private val forceRequest: DeploymentPermitProvider = coroutineContext[HaraClientContext]!!.forceDeploymentPermitProvider
    private val pathResolver = coroutineContext[HaraClientContext]!!.pathResolver
    private val connectionManager = coroutineContext[CMActor]!!.ref
    private val notificationManager = coroutineContext[NMActor]!!.ref
    private var waitingAuthJob: Job? = null

    private fun beforeStartReceive(): Receive = { msg ->
        when (msg) {

            is DeploymentInfo -> {
                val state = State(deplBaseResp = msg.info)
                val message = when {
                    state.isUpdateForced -> {
                        forceUpdateDevice(state)
                    }
                    else -> {
                        val softMsg = attemptUpdateDevice(state)
                        sendFeedback(msg.info.id, proceeding, Progress(0, 0), none, softMsg)
                        softMsg
                    }
                }
                LOG.info(message)
            }

            else -> handleMsgDefault(msg)
        }
    }

    private fun waitingUpdateAuthorization(state: State): Receive = { msg ->
        when (msg) {

            is DeploymentInfo -> {
                become(beforeStartReceive())
                channel.send(msg)
            }

            is Message.UpdateGranted -> {
                val message = "Authorization granted for update"
                LOG.info(message)
                sendFeedback(state.deplBaseResp!!.id, proceeding, Progress(0, 0), none, message)
                startUpdateProcedure(DeploymentInfo(state.deplBaseResp))
            }

            is ConnectionManager.Companion.Message.Out.DeploymentCancelInfo -> {
                stopUpdateAndNotify(msg)
            }

            is ActionManager.Companion.Message.CancelForced -> {
                stopUpdate()
            }

            else -> handleMsgDefault(msg)
        }
    }

    private fun forceUpdateDevice(state: State): String {
        waitingAuthJob = launch {
            forceRequest.onAuthorizationReceive {
                startUpdateProcedure(DeploymentInfo(state.deplBaseResp!!))
            }
            waitingAuthJob = null
        }
        return "Start updating the device"
    }

    private suspend fun attemptUpdateDevice(state: State): String {
        become(waitingUpdateAuthorization(state))
        waitingAuthJob = launch {
            softRequest.onAuthorizationReceive(
                onWaitForAuthorization = {
                    notificationManager.send(
                        MessageListener.Message.State.WaitingUpdateAuthorization(
                            state.isUpdateForced))
                }, onGrantAuthorization = {
                    channel.send(Message.UpdateGranted)
                })
            waitingAuthJob = null
        }
        return "Waiting authorization to update"
    }

    private suspend fun DeploymentPermitProvider.onAuthorizationReceive(
        onWaitForAuthorization: (suspend ()-> Unit)? = null,
        onGrantAuthorization: suspend ()-> Unit){
        //Since updateAllowed function might be a long-running operation,
        // it is better to execute it first and then notify the user about the waiting process
        val updateAllowed = updateAllowed()
        onWaitForAuthorization?.invoke()
        if(updateAllowed.await()){
            onGrantAuthorization.invoke()
        } else {
            LOG.info("Authorization denied for update")
        }
    }

    private suspend fun startUpdateProcedure(msg: DeploymentInfo) {
        LOG.info("START UPDATING!!!")
        notificationManager.send(MessageListener.Message.State.Updating)
        val updaters = registry.allUpdatersWithSwModulesOrderedForPriority(msg.info.deployment.chunks)
        val details = mutableListOf("Details:")
        val updaterError = update(updaters, msg, details)

        when{
            updaters.all { it.softwareModules.isEmpty() } -> {
                parent!!.send(DeploymentManager.Companion.Message.UpdateFinished)
                sendFeedback(msg.info.id, closed, Progress(0,0), success,
                    "No update applied"
                )
                notificationManager.sendMessageToChannelIfOpen(MessageListener.Message.Event.NoUpdate)
            }

            updaterError.isNotEmpty() -> {
                LOG.warn("update ${updaterError[0].first} failed!")
                parent!!.send(DeploymentManager.Companion.Message.UpdateFailed)
                sendFeedback(msg.info.id, closed, Progress(updaters.size, updaterError[0].first),
                    failure, *details.toTypedArray())
                notificationManager.sendMessageToChannelIfOpen(MessageListener.Message.Event.UpdateFinished(successApply = false, details = details))
            }

            else -> {
                parent!!.send(DeploymentManager.Companion.Message.UpdateFinished)
                sendFeedback(msg.info.id, closed, Progress(updaters.size, updaters.size),
                    success, *details.toTypedArray())
                notificationManager.sendMessageToChannelIfOpen(MessageListener.Message.Event.UpdateFinished(successApply = true, details = details))
            }
        }
    }

    private fun update(
        updaters: Set<UpdaterRegistry.UpdaterWithSwModule>,
        message: DeploymentInfo,
        details: MutableList<String>
    ):
            List<Pair<Int, UpdaterRegistry.UpdaterWithSwModule>> {
        return updaters
                .mapIndexed { index, u -> index to u }
                .dropWhile { (index, it) ->
                    val updateResult = it.updater.apply(it.softwareModules.map { swModule ->
                        convert(swModule, pathResolver.fromArtifact(message.info.id))
                    }.toSet(), object : Updater.Messenger {
                        override fun sendMessageToServer(vararg msg: String) {
                            runBlocking {
                                sendFeedback(message.info.id, proceeding,
                                    Progress(updaters.size, index), none, *msg)
                            }
                        }
                    })
                    if (updateResult.details.isNotEmpty()) {
                        details.add("Feedback updater named ${it.updater.javaClass.simpleName}")
                        details.addAll(updateResult.details)
                    }
                    updateResult.success
                }
    }

    private suspend fun sendFeedback(
            id: String,
            execution: Execution,
            progress: Progress,
            finished: Finished,
            vararg messages: String
    ) {
        val request = DeploymentFeedbackRequest.newInstance(id, execution, progress, finished, *messages)
        connectionManager.sendMessageToChannelIfOpen(DeploymentFeedback(request))
    }

    private fun convert(swModule: Updater.SwModule, pathCalculator: (Updater.SwModule.Artifact) -> String): Updater.SwModuleWithPath =
            Updater.SwModuleWithPath(
                    swModule.metadata?.map { Updater.SwModuleWithPath.Metadata(it.key, it.value) }?.toSet(),
                    swModule.type,
                    swModule.name,
                    swModule.version,
                    swModule.artifacts.map
                    { Updater.SwModuleWithPath.Artifact(it.filename, it.hashes, it.size, pathCalculator(it)) }.toSet()
            )

    private suspend fun stopUpdateAndNotify(msg: ConnectionManager.Companion.Message.Out.DeploymentCancelInfo) {
        connectionManager.send(ConnectionManager.Companion.Message.In.CancelFeedback(
            CancelFeedbackRequest.newInstance(msg.info.cancelAction.stopId,
                CancelFeedbackRequest.Status.Execution.closed,
                CancelFeedbackRequest.Status.Result.Finished.success)))
        stopUpdate()
    }

    private suspend fun stopUpdate() {
        LOG.info("Stopping update")
        channel.cancel()
        notificationManager.sendMessageToChannelIfOpen(MessageListener.Message.State.CancellingUpdate)
        parent!!.send(ActionManager.Companion.Message.UpdateStopped)
    }

    init {
        become(beforeStartReceive())
    }

    companion object {
        fun of(scope: ActorScope) = UpdateManager(scope)

        data class State(val deplBaseResp: DeploymentBaseResponse? = null) {
            val isUpdateForced: Boolean
                get() = deplBaseResp!!.deployment.update ==
                        DeploymentBaseResponse.Deployment.ProvisioningType.forced
        }
        sealed class Message {
            object UpdateGranted : Message()
        }
    }
}
