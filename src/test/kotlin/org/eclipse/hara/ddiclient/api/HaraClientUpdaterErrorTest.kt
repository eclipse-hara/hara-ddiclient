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
import org.eclipse.hara.ddiclient.api.FakeDdiServer.Deployment
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A whole client against [FakeDdiServer], with an updater that throws: the client must close that
 * action with failure, once, and then install the next deployment.
 */
class HaraClientUpdaterErrorTest {

    private lateinit var server: FakeDdiServer

    @BeforeMethod
    fun setUp() {
        server = FakeDdiServer()
        server.deploy(Deployment("1", artifact = Artifact(FIRST_ARTIFACT, "hello")),
            Deployment("2", artifact = Artifact("app.bin", "world")))
    }

    @AfterMethod
    fun tearDown() {
        server.close()
    }

    @Test
    fun anErrorInApplyFailsTheAction() {
        server.startClient(updaters = listOf(UpdaterThatThrowsOnce("apply")))

        assertFailedThenNextInstalled("${UpdaterThatThrowsOnce::class.java.name}.apply failed: " +
                "java.lang.IllegalStateException: boom")
    }

    @Test
    fun anErrorChoosingTheFilesToDownloadFailsTheAction() {
        server.startClient(updaters = listOf(UpdaterThatThrowsOnce("requiredSoftwareModulesAndPriority")))

        assertFailedThenNextInstalled("Failed to select the files to download: " +
                "${UpdaterThatThrowsOnce::class.java.name}.requiredSoftwareModulesAndPriority failed: " +
                "java.lang.IllegalStateException: boom")
    }

    @Test
    fun anErrorChoosingTheModulesToUpdateFailsTheAction() {
        val updating = AtomicBoolean()
        server.startClient(
            updaters = listOf(UpdaterThatThrowsOnce("requiredSoftwareModulesAndPriority") { updating.get() }),
            forceDeploymentPermitProvider = updatePermitThatSets(updating))

        assertFailedThenNextInstalled("${UpdaterThatThrowsOnce::class.java.name}.requiredSoftwareModulesAndPriority " +
                "failed: java.lang.IllegalStateException: boom")
    }

    @Test
    fun anErrorInApplyWithNoModulesToApplyFailsTheAction() {
        // The modules are chosen for the download, but none is left for the update
        val updating = AtomicBoolean()
        server.startClient(
            updaters = listOf(UpdaterWithNoModulesToApply { updating.get() }),
            forceDeploymentPermitProvider = updatePermitThatSets(updating))

        val failed = server.awaitClosed("1")
        assertEquals(failed.finished, "failure", server.feedback.toString())
        assertTrue("${UpdaterWithNoModulesToApply::class.java.name}.apply failed: java.lang.IllegalStateException: boom"
                in failed.details, failed.details.toString())
    }

    @Test
    fun anErrorChoosingTheFilesToDownloadIsReportedOnce() {
        // The deployment keeps coming back while the forced download permit is held: those
        // repeated messages must not report the failure again once the permit is granted
        val permit = CompletableDeferred<Boolean>()
        val failedUpdates = AtomicInteger()
        server.startClient(
            updaters = listOf(UpdaterThatChoosesOnce()),
            messageListeners = listOf(object : MessageListener {
                override fun onMessage(message: MessageListener.Message) {
                    if (message is MessageListener.Message.Event.UpdateFinished && !message.successApply) {
                        failedUpdates.incrementAndGet()
                    }
                }
            }),
            forceDeploymentPermitProvider = object : DeploymentPermitProvider {
                override fun downloadAllowed(): Deferred<Boolean> = permit
            })
        server.awaitPolls(4)
        permit.complete(true)

        assertFailedThenNextInstalled("Failed to select the files to download: " +
                "${UpdaterThatChoosesOnce::class.java.name}.requiredSoftwareModulesAndPriority failed: " +
                "java.lang.IllegalStateException: boom")
        assertEquals(server.feedback.count { it.actionId == "1" && it.execution == "closed" }, 1,
            server.feedback.toString())
        assertEquals(failedUpdates.get(), 1)
    }

    private fun assertFailedThenNextInstalled(detail: String) {
        val failed = server.awaitClosed("1")
        assertEquals(failed.finished, "failure", server.feedback.toString())
        assertTrue(detail in failed.details, failed.details.toString())

        // The same client still handles the next deployment
        assertEquals(server.awaitClosed("2").finished, "success", server.feedback.toString())
    }

    // The update permit is asked for once the files are downloaded, just before the update
    private fun updatePermitThatSets(updating: AtomicBoolean) = object : DeploymentPermitProvider {
        override fun updateAllowed(): Deferred<Boolean> {
            updating.set(true)
            return CompletableDeferred(true)
        }
    }

    private class UpdaterThatThrowsOnce(
        private val method: String,
        private val throwNow: () -> Boolean = { true },
    ) : Updater {

        private val thrown = AtomicBoolean()

        override fun requiredSoftwareModulesAndPriority(swModules: Set<Updater.SwModule>): Updater.SwModsApplication {
            throwOnce("requiredSoftwareModulesAndPriority")
            return super.requiredSoftwareModulesAndPriority(swModules)
        }

        override fun apply(modules: Set<Updater.SwModuleWithPath>, messenger: Updater.Messenger): Updater.UpdateResult {
            throwOnce("apply")
            return Updater.UpdateResult(true)
        }

        private fun throwOnce(calledMethod: String) {
            if (calledMethod == method && throwNow() && thrown.compareAndSet(false, true)) {
                throw IllegalStateException("boom")
            }
        }
    }

    // Chooses the modules for the download, none once [updating], and throws from apply
    private class UpdaterWithNoModulesToApply(private val updating: () -> Boolean) : Updater {

        override fun requiredSoftwareModulesAndPriority(swModules: Set<Updater.SwModule>): Updater.SwModsApplication =
            if (updating()) Updater.SwModsApplication(0) else super.requiredSoftwareModulesAndPriority(swModules)

        override fun apply(modules: Set<Updater.SwModuleWithPath>, messenger: Updater.Messenger): Updater.UpdateResult =
            throw IllegalStateException("boom")
    }

    // Chooses the modules of the first deployment once, then throws for it
    private class UpdaterThatChoosesOnce : Updater {

        private val chosen = AtomicBoolean()

        override fun requiredSoftwareModulesAndPriority(swModules: Set<Updater.SwModule>): Updater.SwModsApplication {
            val first = swModules.any { module -> module.artifacts.any { it.filename == FIRST_ARTIFACT } }
            if (first && chosen.getAndSet(true)) {
                throw IllegalStateException("boom")
            }
            return super.requiredSoftwareModulesAndPriority(swModules)
        }

        override fun apply(modules: Set<Updater.SwModuleWithPath>, messenger: Updater.Messenger) =
            Updater.UpdateResult(true)
    }

    companion object {
        private const val FIRST_ARTIFACT = "image.bin"
    }
}
