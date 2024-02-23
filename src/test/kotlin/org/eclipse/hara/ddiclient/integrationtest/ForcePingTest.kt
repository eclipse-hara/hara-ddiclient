/*
 *
 *  * Copyright © 2017-2024  Kynetics  LLC
 *  *
 *  * This program and the accompanying materials are made
 *  * available under the terms of the Eclipse Public License 2.0
 *  * which is available at https://www.eclipse.org/legal/epl-2.0/
 *  *
 *  * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.hara.ddiclient.integrationtest

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.hara.ddiclient.api.DeploymentPermitProvider
import org.eclipse.hara.ddiclient.api.MessageListener
import org.eclipse.hara.ddiclient.integrationtest.api.management.AssignDistributionType
import org.eclipse.hara.ddiclient.integrationtest.api.management.HawkbitAssignDistributionBody
import org.eclipse.hara.ddiclient.integrationtest.utils.logCurrentFunctionName
import org.eclipse.hara.ddiclient.api.MessageListener.Message.Event.Polling
import org.eclipse.hara.ddiclient.api.MessageListener.Message.Event.NoNewState
import org.eclipse.hara.ddiclient.api.MessageListener.Message.State.Idle
import org.eclipse.hara.ddiclient.integrationtest.abstractions.AbstractHaraMessageTest
import org.testng.Assert
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.seconds

class ForcePingTest : AbstractHaraMessageTest() {

    override val targetId: String = "forcePingTest"


    @BeforeClass
    override fun beforeTest() {
        super.beforeTest()
        setPollingTime("00:00:30")
        runBlocking {
            reCreateTestTargetOnServer()
        }
    }

    override fun filterHaraMessages(message: MessageListener.Message): Boolean {
        return when (message) {
            is Polling,
            is Idle,
            is NoNewState -> true

            else -> false
        }
    }

    @Test(enabled = true, priority = 8, timeOut = 10_000, invocationCount = 1)
    fun forcePingShouldPollFromServerImmediatelyTest() {
        logCurrentFunctionName()
        runBlocking {

            client = clientFromTargetId().invoke(targetId)

            expectMessages(
                Polling,
                Idle,
                Polling,
            )

            launch {
                runTheTest(true)
            }

            delay(3.seconds)
            client?.forcePing()
        }
    }

    @Test(enabled = true, priority = 9, timeOut = 60_000, invocationCount = 1)
    fun timeIntervalBetweenEachForcePingCallShouldBe30SecondsTest() {
        logCurrentFunctionName()
        runBlocking {
            setPollingTime("00:00:30")

            client = clientFromTargetId().invoke(targetId)

            expectMessages(
                Polling,
                Idle,
                Polling, //First Force Ping
                NoNewState,
                Polling, //Regular Polling
                Polling, //Deferred Force Ping
                NoNewState,
            )

            val testJob = launch {
                runTheTest(true)
            }

            delay(3.seconds)
            client?.forcePing()

            delay(5.seconds)
            client?.forcePing()

            delay(2.seconds)
            Assert.assertTrue(testJob.isActive, "The test finished earlier than expected!")
        }
    }

    @Test(enabled = true, priority = 10, timeOut = 10_000, invocationCount = 1)
    fun forcePingShouldReturnNoNewStateWhenTargetStateOnServerIsNotChanged() {
        logCurrentFunctionName()
        runBlocking {

            client = clientFromTargetId().invoke(targetId)

            expectMessages(
                Polling,
                Idle,
                Polling,
                NoNewState,
            )

            launch {
                runTheTest(true)
            }

            delay(2.seconds)
            client?.forcePing()
        }
    }

    @Test(enabled = true, priority = 11, timeOut = 150_000, invocationCount = 1)
    fun haraClientShouldReturnPollingStateAfterTheForcePingPollsUpdateFromServer() {
        logCurrentFunctionName()
        runBlocking {

            setPollingTime("00:00:10")
            val deploymentBehavior = object : DeploymentPermitProvider {
                override fun downloadAllowed() = CompletableDeferred(false)
                override fun updateAllowed() = CompletableDeferred(false)
            }

            client = clientFromTargetId(deploymentPermitProvider = deploymentBehavior).invoke(
                targetId)

            expectMessages(
                Polling,
                Idle,
                Polling, //Force Ping should poll update from server
                Polling, //Expected immediate Polling after the receiving the update from server
                Polling, //There should be no Idle/NoNewState after the last polling
                Polling, //Regular Pinging
            )


            launch {
                runTheTest(true)
            }

            delay(3.seconds)
            assignDistributionToTheTarget(
                HawkbitAssignDistributionBody(3, AssignDistributionType.SOFT, 0))

            delay(2.seconds)
            client?.forcePing()
        }
    }

    private suspend fun runTheTest(lastTest: Boolean = false) {
        client?.startAsync()
        startWatchingExpectedMessages(lastTest)
    }

}