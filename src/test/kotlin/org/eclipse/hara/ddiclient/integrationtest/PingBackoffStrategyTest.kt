/*
 *
 *  * Copyright © 2017-2024  Kynetics, Inc.
 *  *
 *  * This program and the accompanying materials are made
 *  * available under the terms of the Eclipse Public License 2.0
 *  * which is available at https://www.eclipse.org/legal/epl-2.0/
 *  *
 *  * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.hara.ddiclient.integrationtest

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.eclipse.hara.ddiclient.api.MessageListener
import org.eclipse.hara.ddiclient.api.MessageListener.Message.Event.NoNewState
import org.eclipse.hara.ddiclient.api.MessageListener.Message.Event.Polling
import org.eclipse.hara.ddiclient.api.MessageListener.Message.State.Idle
import org.eclipse.hara.ddiclient.api.MessageListener.Message.Event.Error
import org.eclipse.hara.ddiclient.integrationtest.abstractions.AbstractHaraMessageTest
import org.eclipse.hara.ddiclient.integrationtest.utils.addOkhttpLogger
import org.eclipse.hara.ddiclient.integrationtest.utils.internalLog
import org.eclipse.hara.ddiclient.integrationtest.utils.logCurrentFunctionName
import org.testng.Assert
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import java.io.InterruptedIOException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

class PingBackoffStrategyTest : AbstractHaraMessageTest() {

    private val testScope = CoroutineScope(Dispatchers.Default)

    companion object {
        const val TARGET_ID = "PingTimeOutTest"
    }

    private val expectedTestDuration =
        Channel<Long>(5, BufferOverflow.DROP_OLDEST)
    private var durationCheckJob: Deferred<Unit>? = null

    private val currentTime: Long
        get() = System.currentTimeMillis()

    override fun filterHaraMessages(message: MessageListener.Message): Boolean {
        return when (message) {
            is Polling,
            is Idle,
            is Error,
            is NoNewState -> true

            else -> false
        }
    }

    @BeforeClass
    override fun beforeTest() {
        super.beforeTest()
        setPollingTime("00:00:05")
    }

    @Test(enabled = true, timeOut = 400_000, priority = 20)
    fun `test Pinging Backoff Strategy Should Retry After 5 Minutes In Case Of Server Timeout`() {
        logCurrentFunctionName()

        runBlocking {
            val okHttpBuilder = OkHttpClient.Builder()
                .addInterceptor(TimeoutInterceptor())
                .addOkhttpLogger()

            client = clientFromTargetId(okHttpClientBuilder = okHttpBuilder).invoke(TARGET_ID)

            expectMessages(
                Polling,
                Idle,
                Polling,
                Error(details = listOf(
                    "exception: class java.io.InterruptedIOException message: Timeout exception")),
                Polling,
                Idle,
            )

            //Given that the polling time is 5 seconds, and the backoff strategy should retry
            //after 45 seconds (For testing only, the default values is 5 minutes) in case of timeout exception.
            //Therefore, the test should be finished in 50 seconds with 1 retry
            runTheTestAndExpectToFinishInSeconds(48..52)
        }
    }

    @Test(enabled = true, timeOut = 150_000, priority = 21)
    fun `test Pinging Backoff Strategy Should Retry every 30 seconds In Case Of failure other than timeout`() {
        logCurrentFunctionName()

        runBlocking {

            val okHttpBuilder = OkHttpClient.Builder()
                .addInterceptor(ConnectionLostInterceptor())
                .addOkhttpLogger()

            client = clientFromTargetId(okHttpClientBuilder = okHttpBuilder).invoke(TARGET_ID)

            expectMessages(
                Polling,
                Idle,
                Polling,
                Error(details = listOf(
                    "exception: class java.net.UnknownHostException message: Unable to resolve host Unable to resolve host")),
                Polling,
                Error(details = listOf(
                    "exception: class java.net.UnknownHostException message: Unable to resolve host Unable to resolve host")),
                Polling,
                Idle,
            )


            //Given that the polling time is 5 seconds, and the backoff strategy should retry after 30, 60, 120 ... seconds
            //in case of exceptions other than timeout.
            //Therefore, the test should be finished in 95 seconds with 2 retries
            val range = 93..98
            runTheTestAndExpectToFinishInSeconds(range)
        }
    }

    private suspend fun runTheTestAndExpectToFinishInSeconds(durationRange: IntRange) {
        val startTime = currentTime
        val testJob = testScope.launch {
            startAsyncAndWatchMessages()
        }

        testJob.invokeOnCompletion {
            val endTime = currentTime
            val duration = (endTime - startTime) / 1000

            runBlocking {
                expectedTestDuration.send(duration)
            }
        }

        durationCheckJob = testScope.async {
            for (testDuration in expectedTestDuration) {
                "Test duration: $testDuration".internalLog()
                assert {
                    Assert.assertTrue(testDuration in durationRange,
                        """The test did not finish in the expected time.
                                Expected time range: $durationRange,
                                Test duration: $testDuration
                                """
                    )
                }
                durationCheckJob?.cancel()
            }
        }

        try {
            durationCheckJob?.await()
        } catch (ignored: CancellationException) {
        }

        testJob.join()
    }

    class TimeoutInterceptor : Interceptor {
        private var attempt = 0

        override fun intercept(chain: Interceptor.Chain): Response {
            attempt++
            if (attempt == 2) {
                "Throwing timeout Exception".internalLog()
                throw InterruptedIOException("Timeout exception")
            }

            return chain.proceed(chain.request())
        }
    }

    class ConnectionLostInterceptor : Interceptor {
        private var attempt = 0

        override fun intercept(chain: Interceptor.Chain): Response {
            attempt++
            if (attempt in 2..3) {
                "Throwing unknown host Exception".internalLog()
                throw UnknownHostException("Unable to resolve host")
            }

            return chain.proceed(chain.request())
        }
    }
}