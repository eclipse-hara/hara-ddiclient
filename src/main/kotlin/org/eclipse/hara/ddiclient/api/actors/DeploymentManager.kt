/*
 * Copyright © 2017-2023  Kynetics  LLC
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.api.actors

import org.eclipse.hara.ddi.api.model.CancelFeedbackRequest
import org.eclipse.hara.ddi.api.model.DeploymentBaseResponse
import org.eclipse.hara.ddi.api.model.DeploymentBaseResponse.Deployment.ProvisioningType
import org.eclipse.hara.ddi.api.model.DeploymentFeedbackRequest
import org.eclipse.hara.ddiclient.api.actors.ActionManager.Companion.Message.CancelForced
import org.eclipse.hara.ddiclient.api.actors.ActionManager.Companion.Message.UpdateStopped
import org.eclipse.hara.ddiclient.api.actors.ConnectionManager.Companion.Message.Out.DeploymentCancelInfo
import org.eclipse.hara.ddiclient.api.actors.ConnectionManager.Companion.Message.Out.DeploymentInfo
import org.eclipse.hara.ddiclient.api.MessageListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.eclipse.hara.ddiclient.api.DeploymentPermitProvider

@OptIn(ObsoleteCoroutinesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeploymentManager
private constructor(scope: ActorScope) : AbstractActor(scope) {

    private val registry = coroutineContext[HaraClientContext]!!.registry
    private val softRequest: DeploymentPermitProvider = coroutineContext[HaraClientContext]!!.softDeploymentPermitProvider
    private val forceRequest: DeploymentPermitProvider = coroutineContext[HaraClientContext]!!.forceDeploymentPermitProvider
    private val connectionManager = coroutineContext[CMActor]!!.ref
    private val notificationManager = coroutineContext[NMActor]!!.ref
    private var waitingAuthJob: Job? = null
    private fun beginningReceive(state: State): Receive = { msg ->
        // todo implement download skip option and move content of attempt function to 'msg is DeploymentInfo && msg.downloadIs(attempt)' when case
        suspend fun attempt(msg: DeploymentInfo, deploymentPermitProvider: DeploymentPermitProvider, forcedDownload:Boolean) {
            val message = "Waiting authorization to download"
            LOG.info(message)
            sendFeedback(message)
            become(waitingDownloadAuthorization(state.copy(deplBaseResp = msg.info)))
            notificationManager.send(MessageListener.Message.State.WaitingDownloadAuthorization(forcedDownload))
            waitingAuthJob?.cancel()
            waitingAuthJob = launch {
                val result = deploymentPermitProvider.downloadAllowed().await()
                if (result) {
                    channel.send(Message.DownloadGranted)
                } else {
                    LOG.info("Authorization denied for download files")
                }
                waitingAuthJob = null
            }
        }

        when {

            msg is DeploymentInfo && msg.downloadIs(ProvisioningType.forced)  -> {
                attempt(msg, forceRequest, true)
            }

            msg is DeploymentInfo && msg.downloadIs(ProvisioningType.attempt) -> {
                attempt(msg, softRequest, false)
            }

            msg is DeploymentInfo && msg.downloadIs(ProvisioningType.skip) -> {
                LOG.warn("skip download not yet implemented (used attempt)")
                attempt(msg, softRequest, false)
            }

            msg is DeploymentCancelInfo -> {
                stopUpdateAndNotify(msg)
            }

            else -> unhandled(msg)
        }
    }

    private fun waitingDownloadAuthorization(state: State): Receive = { msg ->
        when {

            msg is DeploymentInfo && msg.downloadIs(ProvisioningType.attempt) && !msg.forceAuthRequest -> {}

            msg is DeploymentInfo -> {
                become(beginningReceive(state))
                channel.send(msg)
            }

            msg is Message.DownloadGranted -> {
                val message = "Authorization granted for downloading files"
                LOG.info(message)
                sendFeedback(message)
                become(downloadingReceive(state))
                child("downloadManager")!!.send(DeploymentInfo(state.deplBaseResp!!))
            }

            msg is DeploymentCancelInfo -> {
                stopUpdateAndNotify(msg)
            }

            msg is CancelForced -> {
                stopUpdate()
            }

            else -> unhandled(msg)
        }
    }

    private fun downloadingReceive(state: State): Receive = { msg ->
        when (msg) {
            is Message.DownloadFinished -> {
                val message = "Waiting authorization to update"
                LOG.info(message)
                sendFeedback(message)
                become(waitingUpdateAuthorization(state))
                notificationManager.send(MessageListener.Message.State.WaitingUpdateAuthorization(state.updateIs(ProvisioningType.forced)))
                waitingAuthJob = launch(Dispatchers.IO) {
                    when{
                        state.updateIs(ProvisioningType.attempt) -> onAuthorizationReceive(softRequest)
                        else -> onAuthorizationReceive(forceRequest)
                    }
                    waitingAuthJob = null
                }
            }
            is Message.DownloadFailed -> {
                LOG.error("download failed")
                parent!!.send(msg)
            }
            is DeploymentCancelInfo -> {
                stopUpdateAndNotify(msg)
            }
            is CancelForced -> {
                stopUpdate()
            }
            else -> unhandled(msg)
        }
    }

    private suspend fun onAuthorizationReceive(deploymentPermitProvider: DeploymentPermitProvider){
        if(deploymentPermitProvider.updateAllowed().await()){
            channel.send(Message.UpdateGranted)
        } else {
            LOG.info("Authorization denied for update")
        }
    }

    private fun waitingUpdateAuthorization(state: State): Receive = { msg ->
        when (msg) {

            is DeploymentInfo -> {
                become(downloadingReceive(state.copy(deplBaseResp = msg.info)))
                channel.send(Message.DownloadFinished)
            }

            is Message.UpdateGranted -> {
                val message = "Authorization granted for update"
                LOG.info(message)
                sendFeedback(message)
                become(updatingReceive())
                child("updateManager")!!.send(DeploymentInfo(state.deplBaseResp!!))
            }

            is DeploymentCancelInfo -> {
                stopUpdateAndNotify(msg)
            }

            is CancelForced -> {
                stopUpdate()
            }

            else -> unhandled(msg)
        }
    }

    private fun updatingReceive(): Receive = { msg ->
        when (msg) {

            is Message.UpdateFailed -> {
                LOG.info("update failed")
                parent!!.send(msg)
            }

            is Message.UpdateFinished -> {
                LOG.info("update finished")
                parent!!.send(msg)
            }

            is DeploymentCancelInfo -> {
                LOG.info("can't stop update")
                connectionManager.send(ConnectionManager.Companion.Message.In.CancelFeedback(
                        CancelFeedbackRequest.newInstance(msg.info.cancelAction.stopId,
                                CancelFeedbackRequest.Status.Execution.rejected,
                                CancelFeedbackRequest.Status.Result.Finished.success,
                                "Update already started. Can't be stopped.")))
            }

            is CancelForced -> {
                LOG.info("Force cancel ignored")
            }
        }
    }

    private suspend fun stopUpdateAndNotify(msg: DeploymentCancelInfo) {
        connectionManager.send(ConnectionManager.Companion.Message.In.CancelFeedback(
                CancelFeedbackRequest.newInstance(msg.info.cancelAction.stopId,
                        CancelFeedbackRequest.Status.Execution.closed,
                        CancelFeedbackRequest.Status.Result.Finished.success)))
        stopUpdate()
    }

    private suspend fun stopUpdate() {
        LOG.info("Stopping update")
        channel.cancel()
        notificationManager.send(MessageListener.Message.State.CancellingUpdate)
        parent!!.send(UpdateStopped)
    }

    private fun DeploymentInfo.downloadIs(level: ProvisioningType): Boolean {
        return this.info.deployment.download == level
    }

    init {
        actorOf("downloadManager") { DownloadManager.of(it) }
        actorOf("updateManager") { UpdateManager.of(it) }
        become(beginningReceive(State()))
        channel.invokeOnClose {
            waitingAuthJob?.cancel()
        }
    }

    private suspend fun sendFeedback(id: String, vararg messages: String) {
        connectionManager.send(
                ConnectionManager.Companion.Message.In.DeploymentFeedback(
                        DeploymentFeedbackRequest.newInstance(id,
                                DeploymentFeedbackRequest.Status.Execution.proceeding,
                                DeploymentFeedbackRequest.Status.Result.Progress(0, 0),
                                DeploymentFeedbackRequest.Status.Result.Finished.none,
                                *messages
                        )
                )
        )
    }

    companion object {
        fun of(scope: ActorScope) = DeploymentManager(scope)

        data class State(val deplBaseResp: DeploymentBaseResponse? = null) {
            fun updateIs(level: ProvisioningType): Boolean = deplBaseResp!!.deployment.update == level
        }

        sealed class Message {
            object DownloadGranted : Message()
            object UpdateGranted : Message()
            object DownloadFinished : Message()
            data class DownloadFailed(val details: List<String>) : Message()
            object UpdateFailed : Message()
            object UpdateFinished : Message()
        }
    }
}
