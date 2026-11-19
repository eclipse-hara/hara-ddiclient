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

import org.eclipse.hara.ddi.api.model.DeploymentBaseResponse.Deployment.Chunks
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertThrows
import org.testng.Assert.assertTrue
import org.testng.Assert.expectThrows
import org.testng.annotations.Test
import kotlin.coroutines.cancellation.CancellationException

class UpdaterRegistryTest {

    private val os = chunk("os", "linux", "a1", "m1")
    private val app = chunk("application", "app", "a2", "m2")
    private val chunks = setOf(os, app)

    @Test
    fun updatersAreOrderedByPriority() {
        val appUpdater = TypeUpdater("application", priority = 2)
        val osUpdater = TypeUpdater("os", priority = 1)

        val ordered = UpdaterRegistry(appUpdater, osUpdater).allUpdatersWithSwModulesOrderedForPriority(chunks)

        assertEquals(ordered.map { it.updater }, listOf(osUpdater, appUpdater))
    }

    @Test
    fun eachUpdaterGetsOnlyTheModulesItAskedFor() {
        val ordered = UpdaterRegistry(TypeUpdater("os", priority = 1), TypeUpdater("application", priority = 2))
            .allUpdatersWithSwModulesOrderedForPriority(chunks)

        assertEquals(ordered.map { u -> u.softwareModules.map { it.name } }, listOf(listOf("linux"), listOf("app")))
        assertEquals(ordered.first().softwareModules.single().artifacts.single().hashes, Updater.Hashes("a1", "m1"))
    }

    @Test
    fun requiredArtifactsAreTheUnionOverAllUpdaters() {
        val registry = UpdaterRegistry(TypeUpdater("os", priority = 1), TypeUpdater("application", priority = 2))

        assertEquals(registry.allRequiredArtifactsFor(chunks), setOf(Updater.Hashes("a1", "m1"), Updater.Hashes("a2", "m2")))
    }

    @Test
    fun modulesNoUpdaterAsksForAreNotRequired() {
        val registry = UpdaterRegistry(TypeUpdater("os", priority = 1))

        assertEquals(registry.allRequiredArtifactsFor(chunks), setOf(Updater.Hashes("a1", "m1")))
    }

    @Test
    fun updateIsCancellableOnlyIfEveryUpdaterAgrees() {
        val cancellable = TypeUpdater("os", priority = 1)
        val notCancellable = TypeUpdater("application", priority = 2, cancellable = false)

        assertTrue(UpdaterRegistry(cancellable).currentUpdateIsCancellable())
        assertFalse(UpdaterRegistry(cancellable, notCancellable).currentUpdateIsCancellable())
    }

    @Test
    fun anErrorChoosingModulesIsWrappedInUpdaterException() {
        val registry = UpdaterRegistry(TypeUpdater("os", priority = 1), FailingUpdater(IllegalStateException("boom")))

        val e = expectThrows(UpdaterException::class.java) { registry.allUpdatersWithSwModulesOrderedForPriority(chunks) }
        assertThrows(UpdaterException::class.java) { registry.allRequiredArtifactsFor(chunks) }

        assertTrue(e.cause is IllegalStateException)
        assertEquals(e.message, "${FailingUpdater::class.java.name}.requiredSoftwareModulesAndPriority failed: " +
                "java.lang.IllegalStateException: boom")
    }

    @Test
    fun linkageErrorsAreWrappedToo() {
        val registry = UpdaterRegistry(FailingUpdater(NoClassDefFoundError("org/example/Missing")))

        val e = expectThrows(UpdaterException::class.java) { registry.allRequiredArtifactsFor(chunks) }

        assertTrue(e.cause is NoClassDefFoundError)
    }

    @Test
    fun cancellationIsNotWrapped() {
        val registry = UpdaterRegistry(FailingUpdater(CancellationException("stopped")))

        assertThrows(CancellationException::class.java) { registry.allRequiredArtifactsFor(chunks) }
    }

    @Test
    fun anUpdaterThatFailsToAnswerMakesTheUpdateNotCancellable() {
        assertFalse(UpdaterRegistry(TypeUpdater("os", priority = 1), FailingUpdater(RuntimeException())).currentUpdateIsCancellable())
    }

    @Test
    fun anErrorInApplyIsWrappedInUpdaterException() {
        val updater = FailingUpdater(RuntimeException("apply failed"))
        val messenger = object : Updater.Messenger {
            override fun sendMessageToServer(vararg msg: String) {}
        }

        val e = expectThrows(UpdaterException::class.java) {
            updater.call("apply") { apply(emptySet(), messenger) }
        }

        assertEquals(e.message, "${FailingUpdater::class.java.name}.apply failed: java.lang.RuntimeException: apply failed")
    }

    private class FailingUpdater(private val error: Throwable) : Updater {

        override fun requiredSoftwareModulesAndPriority(swModules: Set<Updater.SwModule>): Updater.SwModsApplication =
            throw error

        override fun updateIsCancellable(): Boolean = throw error

        override fun apply(modules: Set<Updater.SwModuleWithPath>, messenger: Updater.Messenger): Updater.UpdateResult =
            throw error
    }

    private class TypeUpdater(
        private val type: String,
        private val priority: Int,
        private val cancellable: Boolean = true
    ) : Updater {

        override fun requiredSoftwareModulesAndPriority(swModules: Set<Updater.SwModule>): Updater.SwModsApplication {
            val all = super.requiredSoftwareModulesAndPriority(swModules)
            return Updater.SwModsApplication(priority, all.swModules.filter { it.type == type }.toSet())
        }

        override fun updateIsCancellable(): Boolean = cancellable

        override fun apply(modules: Set<Updater.SwModuleWithPath>, messenger: Updater.Messenger) =
            Updater.UpdateResult(true)
    }

    private fun chunk(part: String, name: String, sha1: String, md5: String) = Chunks(
        metadata = null,
        part = part,
        name = name,
        version = "1.0",
        artifacts = setOf(
            Chunks.Artifact(
                filename = "$name.bin",
                hashes = Chunks.Artifact.Hashes(sha1, md5),
                size = 5,
                _links = Chunks.Artifact.Links(null, null, null, null)
            )
        )
    )
}
