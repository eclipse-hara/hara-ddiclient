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
        when {

            msg is DeploymentInfo && msg.downloadIs(ProvisioningType.forced)  -> {
                forceDownloadTheArtifact(state, msg, forceRequest)
            }

            msg is DeploymentInfo && msg.downloadIs(ProvisioningType.attempt) -> {
                attemptDownloadingTheArtifact(state, msg, softRequest)
            }

            msg is DeploymentInfo && msg.downloadIs(ProvisioningType.skip) -> {
                // todo implement download skip option
                LOG.warn("skip download not yet implemented (used attempt)")
                attemptDownloadingTheArtifact(state, msg, softRequest)
            }

            msg is DeploymentCancelInfo -> {
                stopUpdateAndNotify(msg)
            }

            else -> unhandled(msg)
        }
    }

    private suspend fun forceDownloadTheArtifact(state: State,
                                                 msg: DeploymentInfo,
                                                 deploymentPermitProvider: DeploymentPermitProvider) {
        val message = "Start downloading artifacts"
        LOG.info(message)
        sendFeedback(message)
        val result = deploymentPermitProvider.downloadAllowed().await()
        if (result) {
            become(downloadingReceive(state.copy(deplBaseResp = msg.info)))
            child("downloadManager")!!.send(msg)
        } else {
            LOG.info("Authorization denied for download files")
        }
    }

    private suspend fun attemptDownloadingTheArtifact(state: State,
                                                      msg: DeploymentInfo,
                                                      deploymentPermitProvider: DeploymentPermitProvider) {
        val message = "Waiting authorization to download"
        LOG.info(message)
        sendFeedback(message)
        become(waitingDownloadAuthorization(state.copy(deplBaseResp = msg.info)))
        notificationManager.send(MessageListener.Message.State
            .WaitingDownloadAuthorization(false))
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

    private fun waitingDownloadAuthorization(state: State): Receive = { msg ->
        when (msg) {
            is DeploymentInfo -> {
                when {

                    msg.downloadIs(ProvisioningType.attempt) && !msg.forceAuthRequest -> {}

                    else -> {
                        become(beginningReceive(state))
                        channel.send(msg)
                    }
                }
            }

            is Message.DownloadGranted -> {
                val message = "Authorization granted for downloading files"
                LOG.info(message)
                sendFeedback(message)
                become(downloadingReceive(state))
                child("downloadManager")!!.send(DeploymentInfo(state.deplBaseResp!!))
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

    private fun downloadingReceive(state: State): Receive = { msg ->
        when (msg) {
            is Message.DownloadFinished -> {
                val message: String
                if (state.updateIs(ProvisioningType.forced)) {
                    message = "Start updating the device"
                    waitingAuthJob = launch(Dispatchers.IO) {
                        if(forceRequest.updateAllowed().await()){
                            become(updatingReceive())
                            child("updateManager")!!.send(DeploymentInfo(state.deplBaseResp!!))
                        } else {
                            LOG.info("Authorization denied for update")
                        }
                        waitingAuthJob = null
                    }
                } else {
                    message = "Waiting authorization to update"
                    become(waitingUpdateAuthorization(state))
                    notificationManager.send(MessageListener.Message.State.WaitingUpdateAuthorization(state.updateIs(ProvisioningType.forced)))
                    waitingAuthJob = launch(Dispatchers.IO) {
                        onAuthorizationReceive(softRequest)
                        waitingAuthJob = null
                    }
                }
                LOG.info(message)
                sendFeedback(message)
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
