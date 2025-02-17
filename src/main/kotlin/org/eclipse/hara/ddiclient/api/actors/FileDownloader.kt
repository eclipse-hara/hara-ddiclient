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

import kotlinx.coroutines.*
import org.eclipse.hara.ddi.api.DdiClient
import org.eclipse.hara.ddi.api.model.DeploymentFeedbackRequest
import org.eclipse.hara.ddiclient.api.MessageListener
import org.eclipse.hara.ddiclient.api.inputstream.FilterInputStreamWithProgress
import java.io.File
import java.text.NumberFormat
import java.util.Timer
import java.util.concurrent.ArrayBlockingQueue
import kotlin.concurrent.fixedRateTimer
import org.eclipse.hara.ddiclient.api.DownloadBehavior
import java.lang.StringBuilder
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ObsoleteCoroutinesApi::class)
class FileDownloader
private constructor(
        scope: ActorScope,
        private val fileToDownload: FileToDownload,
        actionId: String
) : AbstractActor(scope) {

    private val client: DdiClient = coroutineContext[HaraClientContext]!!.ddiClient
    private val downloadBehavior: DownloadBehavior = coroutineContext[HaraClientContext]!!.downloadBehavior
    private val notificationManager = coroutineContext[NMActor]!!.ref
    private val connectionManager = coroutineContext[CMActor]!!.ref
    private var downloadJob: Job? = null
    private var downloadScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private fun beforeStart(state: State): Receive = { msg ->
        when (msg) {

            is Message.Start -> {
                become(downloading(state))
                if (fileToDownload.destination.exists()) {
                    channel.send(Message.FileDownloaded)
                } else {
                    notificationManager.send(MessageListener.Message.Event.StartDownloadFile(fileToDownload.fileName))
                    tryDownload(state)
                }
            }

            else -> handleMsgDefault(msg)
        }
    }

    private fun downloading(state: State): Receive = { msg ->

        when (msg) {

            is Message.FileDownloaded -> checkMd5OfDownloadedFile()

            is Message.FileChecked -> {
                parent!!.send(Message.Success(channel, fileToDownload.md5))
                notificationManager.send(MessageListener.Message.Event.FileDownloaded(fileToDownload.fileName))
            }

            is Message.TrialExhausted -> {
                val errors = mutableListOf<String>()
                errors.add(0, "Exhausted trials of downloading (${fileToDownload.fileName}) after ${state.currentAttempt} attempts!")
                parent!!.send(Message.Error(channel, fileToDownload.md5, errors))
                notificationManager.send(MessageListener.Message.Event.Error(errors))
            }

            is Message.RetryDownload -> {
                val newState = state.copy(currentAttempt = state.nextAttempt(), errors = state.errors + msg.message)
                become(downloading(newState))
                tryDownload(newState, msg.cause)
            }

            else -> handleMsgDefault(msg)
        }
    }

    private suspend fun tryDownload(state: State, error:Throwable? = null) {

        when(val tryDownload = downloadBehavior.onAttempt(state.currentAttempt, "${state.actionId}-${fileToDownload.md5}", error)){

            is DownloadBehavior.Try.Stop ->  channel.send(Message.TrialExhausted)

            is DownloadBehavior.Try.After -> {
                downloadJob = downloadScope.launch {
                    if(error != null){
                        val errorMessage = "Retry download of ${fileToDownload.fileName} due to: $error. The download will start in ${tryDownload.seconds.toDuration(DurationUnit.SECONDS)}."
                        parent!!.send(Message.Info(channel, fileToDownload.md5, errorMessage))
                        notificationManager.send(MessageListener.Message.Event.Error(listOf(errorMessage)))
                    }
                    delay(tryDownload.seconds * 1000)
                    runCatching {
                        download(state.actionId)
                        channel.send(Message.FileDownloaded)
                    }.onFailure {t ->
                        channel.send(Message.RetryDownload("exception: ${t.javaClass.simpleName}. message: ${t.message}", t))
                        LOG.warn("Failed to download file ${fileToDownload.fileName}", t)
                    }
                }
            }
        }
    }

    private fun Double.toPercentage(minFractionDigits: Int = 0): String {
        val format = NumberFormat.getPercentInstance()
        format.minimumFractionDigits = minFractionDigits
        return format.format(this)
    }

    private suspend fun download(actionId: String) {
        val file = fileToDownload.tempFile
        if (file.exists()) {
            file.delete()
        }

        val inputStream = FilterInputStreamWithProgress(client.downloadArtifact(fileToDownload.url), fileToDownload.size)

        val queue = ArrayBlockingQueue(10, true, (1..9).map { it.toDouble() / 10 })

        val timer = checkDownloadProgress(inputStream, queue, actionId)

        runCatching {
            inputStream.use { inputStream ->
                file.outputStream().use { outputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var progressBytes = 0L
                    var bytes = inputStream.read(buffer)
                    while (bytes >= 0) {
                        if (!downloadScope.isActive) {
                            LOG.info("Download of ${fileToDownload.fileName} was cancelled")
                            return
                        }
                        outputStream.write(buffer, 0, bytes)
                        progressBytes += bytes
                        bytes = inputStream.read(buffer)
                    }
                }
            }
        }.also {
            timer.purge()
            timer.cancel()
        }.onFailure {
            throw it
        }

    }

    private fun checkDownloadProgress(
            inputStream: FilterInputStreamWithProgress,
            queue: ArrayBlockingQueue<Double>,
            actionId: String
    ): Timer {
        return fixedRateTimer("Download Checker ${fileToDownload.fileName}", true, 60_000, 60_000) {
            launch {
                val progress = inputStream.getProgress()
                val limit = queue.peek() ?: 1.0
                if (progress > limit) {
                    feedback(actionId,
                            DeploymentFeedbackRequest.Status.Execution.proceeding,
                            DeploymentFeedbackRequest.Status.Result.Progress(0, 0),
                            DeploymentFeedbackRequest.Status.Result.Finished.none,
                            "Downloading file named ${fileToDownload.fileName} " +
                                    "- ${progress.toPercentage(2)}")
                    while (progress > queue.peek() ?: 1.0 && queue.isNotEmpty()) {
                        queue.poll()
                    }
                }
                notificationManager.send(MessageListener.Message.Event.DownloadProgress(fileToDownload.fileName, progress))
            }
        }
    }

    private suspend fun feedback(id: String, execution: DeploymentFeedbackRequest.Status.Execution, progress: DeploymentFeedbackRequest.Status.Result.Progress, finished: DeploymentFeedbackRequest.Status.Result.Finished, vararg messages: String) {
        val deplFdbkReq = DeploymentFeedbackRequest.newInstance(id, execution, progress, finished, *messages)
        connectionManager.send(ConnectionManager.Companion.Message.In.DeploymentFeedback(deplFdbkReq))
    }

    private suspend fun checkMd5OfDownloadedFile() {
        launch {
            var fileAlreadyDownloaded = false
            val file = if(fileToDownload.destination.exists()) {
                LOG.info("${fileToDownload.fileName} already downloaded. Checking md5...")
                fileAlreadyDownloaded = true
                fileToDownload.destination
            } else {
                fileToDownload.tempFile
            }

            val md5 = file.md5()
            val md5CheckResult = md5 == fileToDownload.md5
            when {

                fileAlreadyDownloaded && md5CheckResult -> {
                    parent!!.send(Message.AlreadyDownloaded(channel, fileToDownload.md5))
                }

                !fileAlreadyDownloaded && md5CheckResult -> {
                    fileToDownload.onFileSaved()
                    fileToDownload.destinationMd5File.writeText(md5)
                    channel.send(Message.FileChecked)
                }

                file.delete() -> channel.send(Message.RetryDownload("Downloaded file (${fileToDownload.fileName}) has wrong md5 sum ($md5)"))

                else -> channel.send(Message.RetryDownload("Can't remove file named ${file.name}"))
            }
        }
    }

    override suspend fun stopActor() {
        runCatching {
            downloadJob?.let {
                LOG.debug("Cancelling download job $it")
                if (it.isActive) {
                    it.cancel()
                    downloadScope.cancel()
                }
            }
        }
        super.stopActor()
    }

    private fun State.nextAttempt():Int = if (currentAttempt == Int.MAX_VALUE) currentAttempt else currentAttempt + 1

    init {
        become(beforeStart(State(1, actionId)))
    }

    companion object {
        const val DOWNLOADING_EXTENSION = "downloading"
        fun of(
                scope: ActorScope,
                fileToDownload: FileToDownload,
                actionId: String
        ) = FileDownloader(scope, fileToDownload, actionId)

        data class FileToDownload(
                val fileName: String,
                val md5: String,
                val url: String,
                val folder: File,
                val size: Long
        ) {
            val tempFile = File(folder, "$md5.$DOWNLOADING_EXTENSION")
            val destination = File(folder, md5)
            val destinationMd5File = File(folder, "$md5.md5")
            fun onFileSaved() = tempFile.renameTo(destination)
        }

        private data class State(
            val currentAttempt: Int,
            val actionId: String,
            val errors: List<String> = emptyList()
        )

        sealed class Message {

            object Start : Message()

            object FileDownloaded : Message()
            object FileChecked : Message()
            data class RetryDownload(val message: String, val cause: Throwable? = null) : Message()
            object TrialExhausted : Message()

            data class Success(val sender: ActorRef, val md5: String) : Message()
            data class AlreadyDownloaded(val sender: ActorRef, val md5: String) : Message()
            data class Info(val sender: ActorRef, val md5: String, val message: String) : Message()
            data class Error(val sender: ActorRef, val md5: String, val message: List<String> = emptyList()) : Message()
        }

        fun File.md5(): String {
            val md = MessageDigest.getInstance("MD5")
            val sb = StringBuilder()

            inputStream().use { fis ->
                val digestInputStream = DigestInputStream(fis, md)
                val buffer = ByteArray(4096)
                while (digestInputStream.read(buffer) > -1) {}
                digestInputStream.close()
                digestInputStream.messageDigest
                    .digest()
                    .forEach {
                        sb.append(String.format("%02X", it))
                    }
            }
            return sb.toString().lowercase()
        }
    }
}
