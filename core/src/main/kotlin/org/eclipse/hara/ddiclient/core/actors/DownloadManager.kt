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

import org.eclipse.hara.ddiapiclient.api.model.DeploymentBaseResponse
import org.eclipse.hara.ddiapiclient.api.model.DeploymentBaseResponse.Deployment.ProvisioningType.attempt
import org.eclipse.hara.ddiapiclient.api.model.DeploymentBaseResponse.Deployment.ProvisioningType.forced
import org.eclipse.hara.ddiapiclient.api.model.DeploymentFeedbackRequest
import org.eclipse.hara.ddiapiclient.api.model.DeploymentFeedbackRequest.Status.Execution
import org.eclipse.hara.ddiapiclient.api.model.DeploymentFeedbackRequest.Status.Execution.closed
import org.eclipse.hara.ddiapiclient.api.model.DeploymentFeedbackRequest.Status.Execution.proceeding
import org.eclipse.hara.ddiapiclient.api.model.DeploymentFeedbackRequest.Status.Result.Finished
import org.eclipse.hara.ddiapiclient.api.model.DeploymentFeedbackRequest.Status.Result.Finished.failure
import org.eclipse.hara.ddiapiclient.api.model.DeploymentFeedbackRequest.Status.Result.Finished.none
import org.eclipse.hara.ddiapiclient.api.model.DeploymentFeedbackRequest.Status.Result.Progress
import org.eclipse.hara.ddiclient.core.actors.ConnectionManager.Companion.Message.In.DeploymentFeedback
import org.eclipse.hara.ddiclient.core.actors.ConnectionManager.Companion.Message.Out.DeploymentInfo
import org.eclipse.hara.ddiclient.core.actors.DeploymentManager.Companion.Message.DownloadFailed
import org.eclipse.hara.ddiclient.core.actors.DeploymentManager.Companion.Message.DownloadFinished
import org.eclipse.hara.ddiclient.core.actors.DownloadManager.Companion.State.Download
import org.eclipse.hara.ddiclient.core.actors.DownloadManager.Companion.State.Download.State.Status
import org.eclipse.hara.ddiclient.core.actors.FileDownloader.Companion.FileToDownload
import org.eclipse.hara.ddiclient.core.api.MessageListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class DownloadManager
private constructor(scope: ActorScope) : AbstractActor(scope) {

    private val registry = coroutineContext[HaraClientContext]!!.registry
    private val pathResolver = coroutineContext[HaraClientContext]!!.pathResolver
    private val notificationManager = coroutineContext[NMActor]!!.ref
    private val connectionManager = coroutineContext[CMActor]!!.ref

    @ExperimentalCoroutinesApi
    private fun beforeStartReceive(): Receive = { msg ->
        when (msg) {

            is DeploymentInfo -> {
                clean(msg.info.id)
                val md5s = md5OfFilesToBeDownloaded(msg.info)

                if (md5s.isNotEmpty()) {

                    notificationManager.send(
                            MessageListener.Message.State.Downloading(
                                    msg.info.deployment.chunks.flatMap { it.artifacts }.filter { md5s.contains(it.hashes.md5) }.map { at ->
                                        MessageListener.Message.State.Downloading.Artifact(at.filename, at.size, at.hashes.md5)
                                    }.toList()
                            )
                    )

                    val dms = createDownloadsManagers(msg.info, md5s)
                    become(downloadingReceive(State(msg.info, dms)))
                    feedback(msg.info.id, proceeding, Progress(dms.size, 0), none,
                            "Start downloading ${dms.size} files")
                    dms.values.forEach {
                        it.downloader.send(FileDownloader.Companion.Message.Start)
                    }
                }
            }
            else -> unhandled(msg)
        }
    }

    private fun downloadingReceive(state: State): Receive = { msg ->
        when (msg) {

            is FileDownloader.Companion.Message.Success -> {
                processMessage(state, msg.md5, Status.SUCCESS, "successfully downloaded file with md5 ${msg.md5}")
            }

            is FileDownloader.Companion.Message.AlreadyDownloaded -> {
                processMessage(state, msg.md5, Status.SUCCESS, "${msg.md5} already downloaded")
            }

            is FileDownloader.Companion.Message.Info -> LOG.info(msg.toString())

            is FileDownloader.Companion.Message.Error -> {
                processMessage(state, msg.md5,
                    Status.ERROR, "failed to download file with md5 ${msg.md5} due to ${msg.message}", msg.message)
            }

            else -> unhandled(msg)
        }
    }

    private suspend fun processMessage(state: State, md5: String, status: Status, message: String, errorMsg: List<String>? = null) {
        val download = state.downloads.getValue(md5)
        val newErrMessages = if (errorMsg == null) download.state.messages else download.state.messages + errorMsg
        val newDownload = download.copy(state = download.state.copy(status = status, messages = newErrMessages))
        val newState = state.copy(downloads = state.downloads + (md5 to newDownload))
        val downloads = newState.downloads.values
        val progress = Progress(
                downloads.size,
                downloads.count { it.state.status == Status.SUCCESS })
        when {
            downloads.any { it.state.status == Status.RUNNING } -> {
                feedback(state.deplBaseResp.id, proceeding, progress, none, message)
                become(downloadingReceive(newState))
            }
            downloads.any { it.state.status == Status.ERROR } -> {
                feedback(state.deplBaseResp.id, closed, progress, failure, message)
                notificationManager.send(MessageListener.Message.Event.UpdateFinished(successApply = false,
                        details = listOf(message)))
                parent!!.send(DownloadFailed(listOf(message)))
            }
            else -> {
                feedback(state.deplBaseResp.id, proceeding, progress, none, message)
                feedback(state.deplBaseResp.id, proceeding, progress, none, "successfully downloaded all files")
                newState.downloads.values.forEach { it.downloader.close() }
                notificationManager.send(MessageListener.Message.Event.AllFilesDownloaded)
                parent!!.send(DownloadFinished)
                channel.close()
            }
        }
    }

    private suspend fun feedback(id: String, execution: Execution, progress: Progress, finished: Finished, vararg messages: String) {
        val deplFdbkReq = DeploymentFeedbackRequest.newInstance(id, execution, progress, finished, *messages)
        connectionManager.send(DeploymentFeedback(deplFdbkReq))
    }

    private fun md5OfFilesToBeDownloaded(dbr: DeploymentBaseResponse): Set<String> = when (dbr.deployment.download) {
        attempt, forced -> registry.allRequiredArtifactsFor(dbr.deployment.chunks).map { it.md5 }.toSet()
        else -> emptySet()
    }

    private fun createDownloadsManagers(dbr: DeploymentBaseResponse, md5s: Set<String>): Map<String, Download> {
        val wd = pathResolver.updateDir(dbr.id)
        if (!wd.exists()) {
            wd.mkdirs()
        }
        return dbr.deployment.chunks.flatMap { it.artifacts }.filter { md5s.contains(it.hashes.md5) }.map { at ->
            val md5 = at.hashes.md5
            val ftd = FileToDownload(at.filename, md5, at._links.download_http?.href ?: "" , wd, at.size)
            val dm = actorOf(childName(md5)) {
                FileDownloader.of(it, 3, ftd, dbr.id)
            }
            Pair(md5, Download(dm))
        }.toMap()
    }

    private fun childName(md5: String) = "fileDownloader_for_$md5"

    private fun clean(currentActionId: String) = runBlocking {
        LOG.info("Removing artifacts of old updates (current action is $currentActionId)")
        withContext(Dispatchers.IO) {
            pathResolver.baseDirectory()
                    .listFiles()
                    ?.filter { it != pathResolver.updateDir(currentActionId) }
                    ?.forEach {
                        LOG.info("Removing artifacts of update with action id ${it.name}")
                        it.deleteRecursively() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun beforeCloseChannel() {
        forEachActorNode { actorRef -> if(!actorRef.isClosedForSend) launch { actorRef.send(FileDownloader.Companion.Message.Stop) } }

    }

    init {
        become(beforeStartReceive())
    }

    companion object {
        fun of(scope: ActorScope) = DownloadManager(scope)

        data class State(
                val deplBaseResp: DeploymentBaseResponse,
                val downloads: Map<String, Download> = emptyMap()
        ) {
            data class Download(val downloader: ActorRef, val state: State = State()) {
                data class State(val status: Status = Status.RUNNING, val messages: List<String> = emptyList()) {
                    enum class Status { SUCCESS, ERROR, RUNNING }
                }
            }
        }
    }
}
