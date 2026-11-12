/*
 * Copyright © 2026  Eurotech S.p.A.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.api.actors

import org.eclipse.hara.ddiclient.api.actors.ConnectionManager.Companion.State
import org.testng.Assert.assertEquals
import org.testng.Assert.assertNull
import org.testng.Assert.assertSame
import org.testng.annotations.Test
import org.joda.time.Duration

class ConnectionManagerStateTest {

    @Test
    fun backoffStartsAt30SecondsAndDoublesUpTo5Minutes() {
        val intervals = generateSequence(State().nextBackoff()) { it.nextBackoff() }
            .take(6)
            .map { it.backoffPingInterval!!.standardSeconds }
            .toList()

        assertEquals(intervals, listOf(30L, 60L, 120L, 240L, 300L, 300L))
    }

    @Test
    fun backoffOnTimeoutIs5Minutes() {
        withoutSystemProperty(BACKOFF_PROPERTY) {
            assertEquals(State().nextBackoffInCaseOfTimeout().backoffPingInterval, Duration.standardMinutes(5))
        }
    }

    @Test
    fun backoffOnTimeoutCanBeShortenedForTests() {
        withoutSystemProperty(BACKOFF_PROPERTY) {
            System.setProperty(BACKOFF_PROPERTY, "7")
            assertEquals(State().nextBackoffInCaseOfTimeout().backoffPingInterval, Duration.standardSeconds(7))
        }
    }

    @Test
    fun withoutBackoffClearsTheBackoff() {
        val state = State().nextBackoff().withoutBackoff()

        assertNull(state.backoffPingInterval)
    }

    @Test
    fun pingIntervalPrefersBackoffThenClientThenServer() {
        val server = State().withServerSleep("00:05:00")
        val client = server.copy(clientPingInterval = Duration.standardSeconds(10))
        val backoff = client.nextBackoff()

        assertEquals(server.pingInterval, Duration.standardMinutes(5))
        assertEquals(client.pingInterval, Duration.standardSeconds(10))
        assertEquals(backoff.pingInterval, Duration.standardSeconds(30))
    }

    @Test
    fun serverSleepIsParsedAsHoursMinutesSeconds() {
        val state = State().withServerSleep("01:02:03")

        assertEquals(state.serverPingInterval, Duration.standardSeconds(3723))
    }

    @Test
    fun sameServerSleepKeepsTheState() {
        val state = State().withServerSleep("00:00:10")

        assertSame(state.withServerSleep("00:00:10"), state)
    }

    @Test
    fun clearEtagsResetsBothEtags() {
        val state = State(deploymentEtag = "d", controllerBaseEtag = "c").clearEtags()

        assertEquals(state.deploymentEtag, "")
        assertEquals(state.controllerBaseEtag, "")
    }

    private fun withoutSystemProperty(name: String, block: () -> Unit) {
        val saved = System.clearProperty(name)
        try {
            block()
        } finally {
            if (saved == null) System.clearProperty(name) else System.setProperty(name, saved)
        }
    }

    companion object {
        private const val BACKOFF_PROPERTY = "BACKOFF_INTERVAL_SECONDS"
    }
}
