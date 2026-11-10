/*
 * Copyright © 2026  Eurotech S.p.A.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.smoke

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.eclipse.hara.ddiclient.api.ConfigDataProvider
import org.eclipse.hara.ddiclient.api.DeploymentPermitProvider
import org.eclipse.hara.ddiclient.api.DirectoryForArtifactsProvider
import org.eclipse.hara.ddiclient.api.DownloadBehavior
import org.eclipse.hara.ddiclient.api.HaraClientData
import org.eclipse.hara.ddiclient.api.HaraClientDefaultImpl
import org.eclipse.hara.ddiclient.api.Updater
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Runs one whole deployment on a real Java 8 runtime: poll, download an artifact, apply it,
 * report success. The server is a fake DDI server on the JDK's built-in HTTP server, so the
 * check needs neither Docker nor extra libraries. Run it with `./gradlew java8Smoke`.
 */
fun main() {
    val javaVersion = System.getProperty("java.specification.version")
    if (javaVersion != "1.8") {
        fail("the smoke check must run on Java 8, not on Java $javaVersion")
    }
    val result = try {
        runOneDeployment()
    } catch (t: Throwable) {
        t.printStackTrace()
        fail("${t.javaClass.name}: ${t.message}")
    }
    println("Java 8 smoke check passed on Java ${System.getProperty("java.version")}: $result")
    exitProcess(0)
}

private fun runOneDeployment(): String {
    val closedFeedback = CompletableFuture<String>()
    val installed = CompletableFuture<String>()
    val artifactsDir = Files.createTempDirectory("hara-java8-smoke").toFile()
    val server = FakeDdiServer(closedFeedback)
    val client = HaraClientDefaultImpl()
    try {
        client.init(
            HaraClientData(TENANT, CONTROLLER_ID, server.url, gatewayToken = "token"),
            object : DirectoryForArtifactsProvider {
                override fun directoryForArtifacts() = artifactsDir
            },
            object : ConfigDataProvider {},
            object : DeploymentPermitProvider {},
            emptyList(),
            listOf(object : Updater {
                override fun apply(modules: Set<Updater.SwModuleWithPath>, messenger: Updater.Messenger) =
                    Updater.UpdateResult(true).also {
                        installed.complete(File(modules.single().artifacts.single().path).readText())
                    }
            }),
            object : DownloadBehavior {
                override fun onAttempt(attempt: Int, artifactId: String, previousError: Throwable?) =
                    if (attempt <= 3) DownloadBehavior.Try.After(1) else DownloadBehavior.Try.Stop
            })
        client.startAsync()

        val feedback = closedFeedback.get(60, TimeUnit.SECONDS)
        check(feedback.contains("\"finished\":\"success\"")) { "the action didn't succeed: $feedback" }
        val content = installed.get(1, TimeUnit.SECONDS)
        check(content == CONTENT) { "the updater got \"$content\" instead of \"$CONTENT\"" }
        return "deployment downloaded, applied and reported as successful"
    } finally {
        client.stop()
        server.stop()
        artifactsDir.deleteRecursively()
    }
}

private fun fail(reason: String): Nothing {
    System.err.println("Java 8 smoke check failed: $reason")
    exitProcess(1)
}

/**
 * Offers one deployment (action 42, one artifact) until the client reports it closed.
 */
private class FakeDdiServer(private val closedFeedback: CompletableFuture<String>) {

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    val url = "http://127.0.0.1:${server.address.port}/"

    init {
        server.executor = Executors.newCachedThreadPool { runnable -> Thread(runnable).apply { isDaemon = true } }
        server.createContext("/") { exchange ->
            try {
                handle(exchange)
            } finally {
                exchange.close()
            }
        }
        server.start()
    }

    fun stop() = server.stop(0)

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val base = "/$TENANT/controller/v1/$CONTROLLER_ID"
        when {
            path == base -> json(exchange, if (closedFeedback.isDone) NOTHING_TO_DO else controllerBase())
            path == "$base/deploymentBase/42/feedback" -> {
                val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                if (body.contains("\"execution\":\"closed\"")) {
                    closedFeedback.complete(body)
                }
                respond(exchange, 200, null)
            }
            path == "$base/deploymentBase/42" -> json(exchange, deploymentBase())
            path == ARTIFACT -> respond(exchange, 200, CONTENT.toByteArray())
            else -> respond(exchange, 404, null)
        }
    }

    private fun controllerBase() = """
        {"config":{"polling":{"sleep":"00:00:01"}},
         "_links":{"deploymentBase":{"href":"${url}$TENANT/controller/v1/$CONTROLLER_ID/deploymentBase/42?c=-1"}}}
    """

    private fun deploymentBase() = """
        {"id":"42",
         "deployment":{"download":"forced","update":"forced",
           "chunks":[{"part":"os","version":"1.0","name":"smoke",
             "artifacts":[{"filename":"image.bin","hashes":{"sha1":"$CONTENT_SHA1","md5":"$CONTENT_MD5"},"size":5,
               "_links":{"download-http":{"href":"${url.removeSuffix("/")}$ARTIFACT"}}}]}]}}
    """

    private fun json(exchange: HttpExchange, body: String) {
        exchange.responseHeaders.add("Content-Type", "application/hal+json")
        respond(exchange, 200, body.toByteArray())
    }

    private fun respond(exchange: HttpExchange, code: Int, body: ByteArray?) {
        exchange.sendResponseHeaders(code, body?.size?.toLong() ?: -1)
        body?.let { exchange.responseBody.write(it) }
    }
}

private const val TENANT = "DEFAULT"
private const val CONTROLLER_ID = "java8-smoke"
private const val ARTIFACT = "/files/image.bin"
private const val CONTENT = "hello"
private const val CONTENT_MD5 = "5d41402abc4b2a76b9719d911017c592"
private const val CONTENT_SHA1 = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d"
private const val NOTHING_TO_DO = """{"config":{"polling":{"sleep":"00:00:01"}},"_links":{}}"""
