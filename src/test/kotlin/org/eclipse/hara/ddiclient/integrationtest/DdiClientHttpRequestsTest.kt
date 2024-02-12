/*
 * Copyright © 2017-2024  Kynetics  LLC
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hara.ddiclient.integrationtest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.eclipse.hara.ddi.security.Authentication
import org.eclipse.hara.ddiclient.api.ConfigDataProvider
import org.eclipse.hara.ddiclient.api.DeploymentPermitProvider
import org.eclipse.hara.ddiclient.api.DirectoryForArtifactsProvider
import org.eclipse.hara.ddiclient.api.DownloadBehavior
import org.eclipse.hara.ddiclient.api.HaraClient
import org.eclipse.hara.ddiclient.api.HaraClientData
import org.eclipse.hara.ddiclient.api.HaraClientDefaultImpl
import org.eclipse.hara.ddiclient.api.MessageListener
import org.eclipse.hara.ddiclient.api.MessageListener.Message.Event.Polling
import org.eclipse.hara.ddiclient.api.MessageListener.Message.State.Idle
import org.eclipse.hara.ddiclient.api.Updater
import org.eclipse.hara.ddiclient.integrationtest.api.management.ManagementApi
import org.eclipse.hara.ddiclient.integrationtest.api.management.ManagementClient
import org.eclipse.hara.ddiclient.integrationtest.api.management.ServerSystemConfig
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.basic
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.gatewayToken
import org.eclipse.hara.ddiclient.integrationtest.utils.addOkhttpLogger
import org.eclipse.hara.ddiclient.integrationtest.utils.internalLog
import org.eclipse.hara.ddiclient.integrationtest.utils.logCurrentFunctionName
import org.testng.Assert
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test
import java.lang.Exception
import java.net.HttpURLConnection
import kotlin.time.Duration.Companion.seconds

class DdiClientHttpRequestsTest {

    private lateinit var managementApi: ManagementApi

    private var expectedMessages = mutableListOf<ExpectedMessage.HaraMessage>()
    private var expectedServerResponses = mutableListOf<ExpectedMessage.OkHttpMessage>()
    private val expectedMessageChannel =
        Channel<ExpectedMessage>(5, BufferOverflow.DROP_OLDEST)

    private val checkMessagesScope = CoroutineScope(Dispatchers.IO)
    private val throwableScope = CoroutineScope(Dispatchers.Default)

    private var checkExpectedMessagesJob: Deferred<Unit>? = null
    private var throwableJob: Deferred<Unit>? = null
    private var client: HaraClient?= null
        set(value) {
            safeStopClient()
            field = value
        }

    companion object {
        const val TEST_TARGET_ID = "DoubleToken"
        const val TEST_TARGET_SECURITY_TOKEN = "r2m3ixxc86a2v4q81wntpyhr78zy08we"
    }

    private val messageListener: MessageListener
        get() = object : MessageListener {
        override fun onMessage(message: MessageListener.Message) {
            "Received message: $message".internalLog()
            if (message is Polling || message is Idle) {
                checkMessagesScope.launch {
                    expectedMessageChannel.send(ExpectedMessage.HaraMessage(message))
                }
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        managementApi = ManagementClient.newInstance(TestUtils.hawkbitUrl)
        runBlocking {
            managementApi.setPollingTime(basic, ServerSystemConfig("00:00:05"))
        }
    }

    @AfterTest
    fun afterTest() {
        runBlocking {
            managementApi.setPollingTime(basic, ServerSystemConfig("00:00:30"))
            enableTargetTokenInServer(true)
            enableGatewayTokenInServer(true)
        }
    }

    private fun clientFromTargetId(
        directoryDataProvider: DirectoryForArtifactsProvider = TestUtils.directoryDataProvider,
        configDataProvider: ConfigDataProvider = TestUtils.configDataProvider,
        updater: Updater = TestUtils.updater,
        deploymentPermitProvider: DeploymentPermitProvider = object :
            DeploymentPermitProvider {},
        downloadBehavior: DownloadBehavior = TestUtils.downloadBehavior,
        targetToken: String? = TEST_TARGET_SECURITY_TOKEN,
        gatewayToken: String? = TestUtils.gatewayToken): (String) -> HaraClient =
        { targetId ->
            val clientData = HaraClientData(
                TestUtils.tenantName,
                targetId,
                TestUtils.hawkbitUrl,
                gatewayToken, targetToken)

            val client = HaraClientDefaultImpl()

            val okHttpClient = OkHttpClient.Builder().apply {
                addInterceptor(Interceptor { chain ->
                    val request = chain.request()
                    val response = chain.proceed(request)

                    val actualServerResponse = ExpectedMessage.OkHttpMessage(
                        response.code, request.header("Authorization"))

                    checkMessagesScope.launch {
                        expectedMessageChannel.send(actualServerResponse)
                    }
                    response
                })
                addOkhttpLogger()
            }
            client.init(
                clientData,
                directoryDataProvider,
                configDataProvider,
                deploymentPermitProvider,
                listOf(messageListener),
                listOf(updater),
                downloadBehavior,
                httpBuilder = okHttpClient
            )

            client
        }

    @Suppress("UNUSED_VARIABLE")
    @Test(enabled = true, priority = 1)
    fun useEmptyTokensTest() = runBlocking {
        try {
            val client = clientFromTargetId(targetToken = "", gatewayToken = "").also {
                it.invoke(TEST_TARGET_ID)
            }
        } catch (e: Throwable) {
            assertEquals(e.message, "gatewayToken and targetToken cannot both be empty")
        }
    }

    @Test(enabled = true, timeOut = 60_000, priority = 2)
    fun useOnlyGatewayTokenTest() {
        runBlocking {
            client = clientFromTargetId(targetToken = "").invoke(TEST_TARGET_ID)
            enableTargetTokenInServer(false)
            enableGatewayTokenInServer(false)

            `test #1-1= request should fail, when gateway token is disabled in server`()
            `test #1-2= request with correct gw token should succeed, when gateway token is enabled in server`()
            `test #1-3= request with invalid gateway token should fail`()
        }
    }

    private suspend fun `test #1-1= request should fail, when gateway token is disabled in server`() {
        logCurrentFunctionName()
        expectPollingOnlyMessage()
        expectedServerResponses.apply {
            add(gatewayTokenMessage(HttpURLConnection.HTTP_UNAUTHORIZED))
        }
        startSubTestTest()
    }

    private suspend fun `test #1-2= request with correct gw token should succeed, when gateway token is enabled in server`() {
        logCurrentFunctionName()
        enableGatewayTokenInServer()
        expectPollingAndIdleMessages()
        expectedServerResponses.apply {
            add(gatewayTokenMessage(HttpURLConnection.HTTP_OK))
        }
        startSubTestTest()
    }

    private suspend fun `test #1-3= request with invalid gateway token should fail`() {
        logCurrentFunctionName()
        enableGatewayTokenInServer()

        val invalidGatewayToken = "InValidToken"
        client = clientFromTargetId(targetToken = "",
            gatewayToken = invalidGatewayToken).invoke(TEST_TARGET_ID)
        expectPollingOnlyMessage()
        expectedServerResponses.apply {
            add(gatewayTokenMessage(HttpURLConnection.HTTP_UNAUTHORIZED, invalidGatewayToken))
        }
        startSubTestTest(true)
    }

    @Test(enabled = true, timeOut = 60_000, priority = 3)
    fun useOnlyTargetTokenTest() {
        runBlocking {
            client = clientFromTargetId(gatewayToken = "").invoke(TEST_TARGET_ID)
            enableTargetTokenInServer(false)
            enableGatewayTokenInServer(false)

            `test #2-1= request should fail, when target token is disabled in server`()
            `test #2-2= request with correct target token should succeed, when target token is enabled in server`()
            `test #2-3= request with invalid target token should fail`()
        }
    }

    private suspend fun `test #2-1= request should fail, when target token is disabled in server`() {
        logCurrentFunctionName()
        expectPollingOnlyMessage()
        expectedServerResponses.apply {
            add(targetTokenMessage(HttpURLConnection.HTTP_UNAUTHORIZED))
        }
        startSubTestTest()
    }

    private suspend fun `test #2-2= request with correct target token should succeed, when target token is enabled in server`() {
        logCurrentFunctionName()
        enableTargetTokenInServer()
        expectPollingAndIdleMessages()
        expectedServerResponses.apply {
            add(targetTokenMessage(HttpURLConnection.HTTP_OK))
        }
        startSubTestTest()
    }

    private suspend fun `test #2-3= request with invalid target token should fail`() {
        logCurrentFunctionName()
        enableTargetTokenInServer()

        val invalidTargetToken = "InValidToken"
        client = clientFromTargetId(targetToken = invalidTargetToken,
            gatewayToken = "").invoke(TEST_TARGET_ID)
        expectPollingOnlyMessage()
        expectedServerResponses.apply {
            add(targetTokenMessage(HttpURLConnection.HTTP_UNAUTHORIZED, invalidTargetToken))
        }
        startSubTestTest(true)
    }


    @Test(enabled = true, timeOut = 60_000, priority = 4)
    fun usingEmptyTargetTokenRequestShouldOnlyUseGatewayTokenTest() {
        runBlocking {
            client = clientFromTargetId(targetToken = "").invoke(TEST_TARGET_ID)
            enableTargetTokenInServer(false)
            enableGatewayTokenInServer(true)

            `test #3-1= request should succeed with gateway token, when target token is empty`()
        }
    }

    private suspend fun `test #3-1= request should succeed with gateway token, when target token is empty`() {
        logCurrentFunctionName()
        expectPollingAndIdleMessages()
        expectedServerResponses.apply {
            add(gatewayTokenMessage(HttpURLConnection.HTTP_OK))
        }
        startSubTestTest(true)
    }


    @Test(enabled = true, timeOut = 60_000, priority = 5)
    fun usingEmptyGatewayTokenRequestShouldOnlyUseTargetTokenTest() {
        runBlocking {
            enableTargetTokenInServer(true)
            enableGatewayTokenInServer(false)
            client = clientFromTargetId(gatewayToken = "").invoke(TEST_TARGET_ID)

            `test #4-1= request should succeed with target token, when gateway token is empty`()
        }
    }

    private suspend fun `test #4-1= request should succeed with target token, when gateway token is empty`() {
        logCurrentFunctionName()
        expectPollingAndIdleMessages()
        expectedServerResponses.apply {
            add(targetTokenMessage(HttpURLConnection.HTTP_OK))
        }
        startSubTestTest(true)
    }

    @Test(enabled = true, timeOut = 200_000, priority = 6)
    fun providingBothTokensTest() {
        runBlocking {
            client = clientFromTargetId().invoke(TEST_TARGET_ID)
            enableTargetTokenInServer(false)
            enableGatewayTokenInServer(false)

            `test #5-1= request should fail, when both tokens are disabled in server`()
            `test #5-2= next request should use target token, when target token is enabled in server`()
            `test #5-3= next request should use gateway token, when target token is disabled in server`()
            `test #5-4= next request should keep using gateway token, when target token is enabled in server`()
            `test #5-5= next request should switch to target token, when gateway token is disabled in server`()
            `test #5-6= next request should keep using target token, when gateway token is enabled in server`()
        }
    }

    private suspend fun `test #5-1= request should fail, when both tokens are disabled in server`() {
        logCurrentFunctionName()
        expectPollingOnlyMessage()
        expectedServerResponses.apply {
            add(targetTokenMessage(HttpURLConnection.HTTP_UNAUTHORIZED))
            add(gatewayTokenMessage(HttpURLConnection.HTTP_UNAUTHORIZED))
        }
        startSubTestTest()
    }

    private suspend fun `test #5-2= next request should use target token, when target token is enabled in server`() {
        logCurrentFunctionName()
        enableTargetTokenInServer()
        expectPollingAndIdleMessages()
        expectedServerResponses.apply {
            add(targetTokenMessage(HttpURLConnection.HTTP_OK))
        }
        startSubTestTest()
    }

    private suspend fun `test #5-3= next request should use gateway token, when target token is disabled in server`() {
        logCurrentFunctionName()
        enableTargetTokenInServer(false)
        enableGatewayTokenInServer(true)
        expectPollingOnlyMessage()
        expectedServerResponses.apply {
            add(targetTokenMessage(HttpURLConnection.HTTP_UNAUTHORIZED))
            add(gatewayTokenMessage(HttpURLConnection.HTTP_NOT_MODIFIED))
        }
        startSubTestTest()
    }

    private suspend fun `test #5-4= next request should keep using gateway token, when target token is enabled in server`() {
        logCurrentFunctionName()
        enableTargetTokenInServer(true)
        expectPollingOnlyMessage()
        expectedServerResponses.apply {
            add(gatewayTokenMessage(HttpURLConnection.HTTP_NOT_MODIFIED))
        }
        startSubTestTest()
    }

    private suspend fun `test #5-5= next request should switch to target token, when gateway token is disabled in server`() {
        logCurrentFunctionName()
        enableGatewayTokenInServer(false)
        expectPollingOnlyMessage()
        expectedServerResponses.apply {
            add(gatewayTokenMessage(HttpURLConnection.HTTP_UNAUTHORIZED))
            add(targetTokenMessage(HttpURLConnection.HTTP_NOT_MODIFIED))
        }
        startSubTestTest()
    }

    private suspend fun `test #5-6= next request should keep using target token, when gateway token is enabled in server`() {
        logCurrentFunctionName()
        enableGatewayTokenInServer(true)
        expectPollingOnlyMessage()
        expectedServerResponses.apply {
            add(targetTokenMessage(HttpURLConnection.HTTP_NOT_MODIFIED))
        }
        startSubTestTest(true)
    }

    private suspend fun startSubTestTest(lastTest: Boolean = false) {
        client?.startAsync()
        startWatchingExpectedMessages(lastTest)
    }

    private suspend fun startWatchingExpectedMessages(lastTest: Boolean = false) {
        checkExpectedMessagesJob = getExpectedMessagesCheckingJob(lastTest)
        try {
            checkExpectedMessagesJob?.await()
        } catch (ignored: CancellationException) {
        }
    }

    private fun expectPollingAndIdleMessages() {
        expectedMessages.clear()
        expectedMessages.apply {
            add(ExpectedMessage.HaraMessage(Polling))
            add(ExpectedMessage.HaraMessage(Idle))
        }
    }

    private fun expectPollingOnlyMessage() {
        expectedMessages.clear()
        expectedMessages.apply {
            add(ExpectedMessage.HaraMessage(Polling))
        }
    }

    private suspend fun enableGatewayTokenInServer(enabled: Boolean = true) {
        managementApi.setGatewayTokenAuthorizationEnabled(basic, ServerSystemConfig(enabled))
        delay(1.seconds)
    }

    private suspend fun enableTargetTokenInServer(enabled: Boolean = true) {
        managementApi.setTargetTokenAuthorizationEnabled(basic, ServerSystemConfig(enabled))
        delay(1.seconds)
    }

    private fun targetTokenMessage(responseCode: Int = HttpURLConnection.HTTP_OK,
                                   token: String = TEST_TARGET_SECURITY_TOKEN) =
        ExpectedMessage.OkHttpMessage(
            responseCode,
            Authentication.newInstance(
                Authentication.AuthenticationType.TARGET_TOKEN_AUTHENTICATION,
                token
            ).headerValue
        )

    private fun gatewayTokenMessage(responseCode: Int = HttpURLConnection.HTTP_OK,
                                    token: String = gatewayToken) =
        ExpectedMessage.OkHttpMessage(
            responseCode,
            Authentication.newInstance(
                Authentication.AuthenticationType.GATEWAY_TOKEN_AUTHENTICATION,
                token
            ).headerValue
        )

    private suspend fun assertEquals(actual: Any?, expected: Any?) {
        throwableJob = throwableScope.async {
            Assert.assertEquals(actual, expected)
        }
        try {
            throwableJob?.await()
        } catch (ignored: CancellationException) {
        }
    }

    private suspend fun getExpectedMessagesCheckingJob(lastTest: Boolean): Deferred<Unit> {
        return checkMessagesScope.async {
            for (msg in expectedMessageChannel) {
                when (msg) {
                    is ExpectedMessage.HaraMessage -> {
                        if (expectedMessages.isNotEmpty()) {
                            assertEquals(msg, expectedMessages.removeFirst())
                        }
                    }

                    is ExpectedMessage.OkHttpMessage -> {
                        if (expectedServerResponses.isNotEmpty()) {
                            assertEquals(msg, expectedServerResponses.removeFirst())
                        }
                    }
                }
                if (expectedMessages.isEmpty() && expectedServerResponses.isEmpty()) {
                    "All expected messages received".internalLog()
                    checkExpectedMessagesJob?.cancel()
                    if (lastTest) {
                        safeStopClient()
                    }
                }
            }
        }
    }

    private fun safeStopClient() {
        try {
            client?.stop()
        } catch (ignored: Exception) {
        }
    }

    sealed class ExpectedMessage {
        data class HaraMessage(val message: MessageListener.Message) : ExpectedMessage()
        data class OkHttpMessage(val code: Int, val authHeader: String?) : ExpectedMessage()
    }
}
