/*
 * Copyright © 2026  Eurotech S.p.A.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.integrationtest

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.eclipse.hara.ddiclient.api.Updater
import org.eclipse.hara.ddiclient.integrationtest.abstractions.AbstractTest
import org.eclipse.hara.ddiclient.integrationtest.api.management.ActionStatus
import org.eclipse.hara.ddiclient.integrationtest.api.management.AssignDistributionType
import org.eclipse.hara.ddiclient.integrationtest.api.management.HawkbitAssignDistributionBody
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.endMessagesOnSuccessUpdate
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class UpdaterErrorTest : AbstractTest() {

    companion object {
        const val TARGET_ID = "UpdaterErrorTest"
    }

    @BeforeClass
    override fun beforeTest() {
        super.beforeTest()
        setPollingTime("00:00:05")
    }

    // Only the update entries are checked: the download entries depend on artifacts other
    // tests leave in the shared download directory ("already downloaded" or not).
    @Test(enabled = true, timeOut = 60_000, priority = 45)
    fun anUpdaterErrorFailsTheActionAndTheClientKeepsWorking() = runBlocking {
        reCreateTestTargetOnServer(TARGET_ID)
        val updater = ThrowsOnceUpdater()
        client = clientFromTargetId(updater = updater).invoke(TARGET_ID)
        client!!.startAsync()

        val failedAction = assignForced(TestUtils.OS_DISTRIBUTION_ID)
        waitUntilClosed(failedAction)
        assertContains(failedAction,
            ActionStatus.ContentEntry(ActionStatus.ContentEntry.Type.error, listOf(
                "Details:",
                "Feedback updater named ThrowsOnceUpdater",
                "${ThrowsOnceUpdater::class.java.name}.apply failed: java.lang.IllegalStateException: boom")))

        // The same client must still handle the next deployment
        val nextAction = assignForced(TestUtils.APP_DISTRIBUTION_ID)
        waitUntilClosed(nextAction)
        assertContains(nextAction, *endMessagesOnSuccessUpdate)
        assertEquals(updater.calls.get(), 2)

        safeStopClient()
    }

    private suspend fun assignForced(distributionId: Int): Int =
        assignDistributionToTheTarget(TARGET_ID,
            HawkbitAssignDistributionBody(distributionId, AssignDistributionType.FORCED, 0))

    private suspend fun waitUntilClosed(actionId: Int) {
        while (managementApi.getActionAsync(TestUtils.basic, TARGET_ID, actionId).active) {
            delay(1.seconds)
        }
    }

    private suspend fun assertContains(actionId: Int, vararg expected: ActionStatus.ContentEntry) {
        val content = managementApi.getTargetActionStatusAsync(TestUtils.basic, TARGET_ID, actionId).content
        assertEquals(expected.filterNot { it in content }, emptyList<ActionStatus.ContentEntry>())
    }

    private class ThrowsOnceUpdater : Updater {
        val calls = AtomicInteger()

        override fun apply(modules: Set<Updater.SwModuleWithPath>, messenger: Updater.Messenger): Updater.UpdateResult {
            if (calls.incrementAndGet() == 1) {
                throw IllegalStateException("boom")
            }
            return TestUtils.updater.apply(modules, messenger)
        }
    }
}
