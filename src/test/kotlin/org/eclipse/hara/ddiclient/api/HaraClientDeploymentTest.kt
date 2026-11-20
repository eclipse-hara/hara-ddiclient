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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.eclipse.hara.ddiclient.api.FakeDdiServer.Artifact
import org.eclipse.hara.ddiclient.api.FakeDdiServer.Companion.updater
import org.eclipse.hara.ddiclient.api.FakeDdiServer.Deployment
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * A whole client against [FakeDdiServer], from the deployment to the feedback that closes it.
 */
class HaraClientDeploymentTest {

    private lateinit var server: FakeDdiServer

    @BeforeMethod
    fun setUp() {
        server = FakeDdiServer()
    }

    @AfterMethod
    fun tearDown() {
        server.close()
    }

    @Test
    fun aForcedDeploymentIsDownloadedAppliedAndClosedWithSuccess() {
        val installed = Collections.synchronizedList(mutableListOf<String>())
        server.deploy(Deployment("1", artifact = Artifact("image.bin", "hello")))

        server.startClient(updaters = listOf(updater { modules ->
            installed += modules.flatMap { it.artifacts }.map { File(it.path).readText() }
            Updater.UpdateResult(true)
        }))

        assertEquals(server.awaitClosed("1").finished, "success", server.feedback.toString())
        assertEquals(installed, listOf("hello"))
    }

    @Test
    fun aSoftDeploymentIsInstalledWhenThePermitsAreGranted() {
        server.deploy(Deployment("1", download = "attempt", update = "attempt"))

        server.startClient()

        assertEquals(server.awaitClosed("1").finished, "success", server.feedback.toString())
    }

    @Test
    fun anUpdaterThatReportsAFailureClosesTheActionWithFailure() {
        server.deploy(Deployment("1"))

        server.startClient(updaters = listOf(updater { Updater.UpdateResult(false, listOf("disk full")) }))

        val closed = server.awaitClosed("1")
        assertEquals(closed.finished, "failure", server.feedback.toString())
        assertTrue("disk full" in closed.details, closed.details.toString())
    }

    @Test
    fun theClientInstallsTheNextDeploymentAfterClosingOne() {
        val installed = Collections.synchronizedList(mutableListOf<String>())
        server.deploy(
            Deployment("1", artifact = Artifact("image.bin", "hello")),
            Deployment("2", artifact = Artifact("app.bin", "world")))

        server.startClient(updaters = listOf(updater { modules ->
            installed += modules.flatMap { it.artifacts }.map { File(it.path).readText() }
            Updater.UpdateResult(true)
        }))

        assertEquals(server.awaitClosed("1").finished, "success", server.feedback.toString())
        assertEquals(server.awaitClosed("2").finished, "success", server.feedback.toString())
        assertEquals(installed, listOf("hello", "world"))
    }

    @Test
    fun aForcedUpdateRunsOnceWhileTheDeploymentComesBack() {
        assertUpdatedOnce(Deployment("1", download = "forced", update = "forced"))
    }

    @Test
    fun aSoftUpdateRunsOnceWhileTheDeploymentComesBack() {
        assertUpdatedOnce(Deployment("1", download = "attempt", update = "attempt"))
    }

    // The update lasts several polls, and the server sends the deployment again on each of them.
    // Counted per run of the action: a deployment message read just before the server got the
    // closing feedback can make the client run the closed action once more, a separate issue
    private fun assertUpdatedOnce(deployment: Deployment) {
        val runs = AtomicInteger()
        val applied = AtomicInteger()
        val updatePermits = AtomicInteger()
        val permit = object : DeploymentPermitProvider {
            override fun updateAllowed(): Deferred<Boolean> {
                updatePermits.incrementAndGet()
                return CompletableDeferred(true)
            }
        }
        server.deploy(deployment)

        server.startClient(
            updaters = listOf(updater {
                applied.incrementAndGet()
                server.awaitPolls(UPDATE_POLLS)
                Updater.UpdateResult(true)
            }),
            messageListeners = listOf(object : MessageListener {
                override fun onMessage(message: MessageListener.Message) {
                    if (message == MessageListener.Message.Event.AllFilesDownloaded) {
                        runs.incrementAndGet()
                    }
                }
            }),
            softDeploymentPermitProvider = permit,
            forceDeploymentPermitProvider = permit)

        assertEquals(server.awaitClosed("1").finished, "success", server.feedback.toString())
        // By then the client has handled the deployment messages that came back during the update
        server.awaitPolls(UPDATE_POLLS + 3)
        val feedback = server.feedback.toString()
        assertEquals(applied.get(), runs.get(), feedback)
        assertEquals(updatePermits.get(), runs.get(), feedback)
        assertEquals(server.feedback.count { it.execution == "closed" }, runs.get(), feedback)
    }

    companion object {
        private const val UPDATE_POLLS = 4
    }
}
