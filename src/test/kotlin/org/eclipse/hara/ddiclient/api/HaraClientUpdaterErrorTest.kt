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
import org.eclipse.hara.ddiclient.api.FakeDdiServer.Deployment
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A whole client against [FakeDdiServer], with an updater that throws once: the client must
 * close that action with failure and then install the next deployment.
 */
class HaraClientUpdaterErrorTest {

    private lateinit var server: FakeDdiServer

    @BeforeMethod
    fun setUp() {
        server = FakeDdiServer()
        server.deploy(Deployment("1"), Deployment("2"))
    }

    @AfterMethod
    fun tearDown() {
        server.close()
    }

    @Test
    fun anErrorInApplyFailsTheAction() {
        server.startClient(updaters = listOf(UpdaterThatThrowsOnce("apply")))

        assertFailedThenNextInstalled("$UPDATER.apply failed: java.lang.IllegalStateException: boom")
    }

    @Test
    fun anErrorChoosingTheFilesToDownloadFailsTheAction() {
        server.startClient(updaters = listOf(UpdaterThatThrowsOnce("requiredSoftwareModulesAndPriority")))

        assertFailedThenNextInstalled("Failed to select the files to download: " +
                "$UPDATER.requiredSoftwareModulesAndPriority failed: java.lang.IllegalStateException: boom")
    }

    @Test
    fun anErrorChoosingTheModulesToUpdateFailsTheAction() {
        // The update permit is asked for once the files are downloaded, just before the update
        val updating = AtomicBoolean()
        server.startClient(
            updaters = listOf(UpdaterThatThrowsOnce("requiredSoftwareModulesAndPriority") { updating.get() }),
            forceDeploymentPermitProvider = object : DeploymentPermitProvider {
                override fun updateAllowed(): Deferred<Boolean> {
                    updating.set(true)
                    return CompletableDeferred(true)
                }
            })

        assertFailedThenNextInstalled(
            "$UPDATER.requiredSoftwareModulesAndPriority failed: java.lang.IllegalStateException: boom")
    }

    private fun assertFailedThenNextInstalled(detail: String) {
        val failed = server.awaitClosed("1")
        assertEquals(failed.finished, "failure", server.feedback.toString())
        assertTrue(detail in failed.details, failed.details.toString())

        // The same client still handles the next deployment
        assertEquals(server.awaitClosed("2").finished, "success", server.feedback.toString())
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

    companion object {
        private val UPDATER = UpdaterThatThrowsOnce::class.java.name
    }
}
