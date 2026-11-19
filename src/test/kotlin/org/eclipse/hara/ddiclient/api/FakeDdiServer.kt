/*
 * Copyright © 2026  Eurotech S.p.A.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.api

import com.google.gson.JsonParser
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * A fake hawkBit DDI server, to test a whole client without Docker.
 *
 * Like hawkBit, it offers the deployments given to [deploy] one at a time, in order: the next one
 * once the client has closed the current one. Each deployment has one software module with one
 * artifact, served with both download links and its SHA-1, MD5 and SHA-256 hashes. The server
 * records every deployment feedback the client sends.
 *
 * [startClient] starts a client against it; [close] stops those clients and the server.
 */
class FakeDdiServer : AutoCloseable {

    data class Deployment(
        val id: String,
        val download: String = "forced",
        val update: String = "forced",
        val artifact: Artifact = Artifact("image.bin", "hello"),
    )

    data class Artifact(val filename: String, val content: String)

    /** A deployment feedback, as the client sent it. */
    data class Feedback(val actionId: String, val execution: String, val finished: String, val details: List<String>)

    private val server = MockWebServer()
    private val deployments = ConcurrentHashMap<String, Deployment>()
    private val queue = mutableListOf<Deployment>()
    private val closed = ConcurrentHashMap<String, CompletableFuture<Feedback>>()
    private val received = mutableListOf<Feedback>()
    private val polls = AtomicInteger()
    private val clients = mutableListOf<HaraClient>()

    /** Where the clients started by [startClient] download, unless told otherwise; deleted by [close]. */
    val artifactsDir: File = Files.createTempDirectory("hara-fake-ddi").toFile()

    /** Every deployment feedback received so far, in order. */
    val feedback: List<Feedback>
        get() = synchronized(received) { received.toList() }

    init {
        server.dispatcher = DdiDispatcher()
        server.start()
    }

    fun deploy(vararg deployments: Deployment) {
        deployments.forEach { this.deployments[it.id] = it }
        synchronized(queue) { queue += deployments }
    }

    /**
     * Starts a client against this server. The defaults suit a test: one download attempt, no
     * retry, and an updater that succeeds.
     */
    fun startClient(
        updaters: List<Updater> = listOf(updater { Updater.UpdateResult(true) }),
        messageListeners: List<MessageListener> = emptyList(),
        directoryForArtifactsProvider: DirectoryForArtifactsProvider = object : DirectoryForArtifactsProvider {
            override fun directoryForArtifacts() = artifactsDir
        },
        configDataProvider: ConfigDataProvider = object : ConfigDataProvider {},
        softDeploymentPermitProvider: DeploymentPermitProvider = object : DeploymentPermitProvider {},
        forceDeploymentPermitProvider: DeploymentPermitProvider = object : DeploymentPermitProvider {},
        downloadBehavior: DownloadBehavior = object : DownloadBehavior {
            override fun onAttempt(attempt: Int, artifactId: String, previousError: Throwable?) =
                if (attempt == 1) DownloadBehavior.Try.After(0) else DownloadBehavior.Try.Stop
        },
    ): HaraClient {
        val client = HaraClientDefaultImpl()
        client.init(
            HaraClientData(TENANT, CONTROLLER_ID, server.url("/").toString(), gatewayToken = "token"),
            directoryForArtifactsProvider,
            configDataProvider,
            softDeploymentPermitProvider,
            messageListeners,
            updaters,
            downloadBehavior,
            forceDeploymentPermitProvider)
        synchronized(clients) { clients += client }
        client.startAsync()
        return client
    }

    /** Waits until the client closes the action [actionId], and returns the closing feedback. */
    fun awaitClosed(actionId: String, timeoutSeconds: Long = 15): Feedback = try {
        closedAction(actionId).get(timeoutSeconds, TimeUnit.SECONDS)
    } catch (e: TimeoutException) {
        throw AssertionError("Action $actionId was never closed. Polls: ${polls.get()}, feedback: $feedback", e)
    }

    override fun close() {
        synchronized(clients) { clients.forEach { runCatching { it.stop() } } }
        server.shutdown()
        artifactsDir.deleteRecursively()
    }

    private fun closedAction(actionId: String) = closed.computeIfAbsent(actionId) { CompletableFuture() }

    private fun current(): Deployment? = synchronized(queue) { queue.firstOrNull() }

    private inner class DdiDispatcher : Dispatcher() {

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.requestUrl!!.encodedPath
            val deployment = deployments[path.removePrefix("$CONTROLLER/deploymentBase/")]
            return when {
                path == CONTROLLER -> {
                    polls.incrementAndGet()
                    json(controllerBase(current()))
                }
                path == "$CONTROLLER/configData" -> MockResponse()
                path.startsWith("$CONTROLLER/deploymentBase/") && path.endsWith("/feedback") -> onFeedback(path, request)
                deployment != null -> json(deploymentBase(deployment))
                path.startsWith(ARTIFACTS) -> artifact(path.removePrefix(ARTIFACTS))
                else -> MockResponse().setResponseCode(404)
            }
        }

        private fun onFeedback(path: String, request: RecordedRequest): MockResponse {
            val actionId = path.removePrefix("$CONTROLLER/deploymentBase/").removeSuffix("/feedback")
            val status = JsonParser.parseString(request.body.readUtf8()).asJsonObject.getAsJsonObject("status")
            val feedback = Feedback(
                actionId,
                status.get("execution").asString,
                status.getAsJsonObject("result").get("finished").asString,
                status.getAsJsonArray("details").map { it.asString })
            synchronized(received) { received += feedback }
            if (feedback.execution == "closed") {
                // Like hawkBit: a closed action is no longer offered
                synchronized(queue) { queue.removeAll { it.id == actionId } }
                closedAction(actionId).complete(feedback)
            }
            return MockResponse()
        }

        // <action id>/<file name>
        private fun artifact(path: String): MockResponse {
            val deployment = deployments[path.substringBefore('/')]
            return if (deployment != null && deployment.artifact.filename == path.substringAfter('/')) {
                MockResponse().setBody(deployment.artifact.content)
            } else {
                MockResponse().setResponseCode(404)
            }
        }

        private fun controllerBase(deployment: Deployment?): String {
            val links = deployment?.let {
                """"deploymentBase":{"href":"${server.url("$CONTROLLER/deploymentBase/${it.id}")}?c=-1"}"""
            } ?: ""
            return """{"config":{"polling":{"sleep":"$POLLING_SLEEP"}},"_links":{$links}}"""
        }

        private fun deploymentBase(deployment: Deployment): String {
            val artifact = deployment.artifact
            val bytes = artifact.content.toByteArray()
            val url = server.url("$ARTIFACTS${deployment.id}/${artifact.filename}")
            return """
                {"id":"${deployment.id}",
                 "deployment":{"download":"${deployment.download}","update":"${deployment.update}",
                   "maintenanceWindow":"available",
                   "chunks":[{"part":"os","version":"1.0","name":"image",
                     "artifacts":[{"filename":"${artifact.filename}",
                       "hashes":{"sha1":"${bytes.hash("SHA-1")}","md5":"${bytes.hash("MD5")}",
                                 "sha256":"${bytes.hash("SHA-256")}"},
                       "size":${bytes.size},
                       "_links":{"download":{"href":"$url"},"download-http":{"href":"$url"}}}]}]}}
            """
        }

        private fun json(body: String) = MockResponse()
            .setHeader("Content-Type", "application/hal+json")
            .setBody(body)
    }

    companion object {
        const val TENANT = "DEFAULT"
        const val CONTROLLER_ID = "fake-ddi-test"
        private const val CONTROLLER = "/$TENANT/controller/v1/$CONTROLLER_ID"
        private const val ARTIFACTS = "/artifacts/"
        private const val POLLING_SLEEP = "00:00:01"

        /** An updater whose [Updater.apply] runs [apply]. */
        fun updater(apply: (Set<Updater.SwModuleWithPath>) -> Updater.UpdateResult): Updater = TestUpdater(apply)

        private class TestUpdater(private val apply: (Set<Updater.SwModuleWithPath>) -> Updater.UpdateResult) : Updater {
            override fun apply(modules: Set<Updater.SwModuleWithPath>, messenger: Updater.Messenger) = apply(modules)
        }

        private fun ByteArray.hash(algorithm: String) =
            MessageDigest.getInstance(algorithm).digest(this).joinToString("") { "%02x".format(it) }
    }
}
