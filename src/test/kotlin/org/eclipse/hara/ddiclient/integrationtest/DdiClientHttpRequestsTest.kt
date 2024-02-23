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

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.eclipse.hara.ddi.security.Authentication
import org.eclipse.hara.ddiclient.api.HaraClient
import org.eclipse.hara.ddiclient.api.MessageListener
import org.eclipse.hara.ddiclient.api.MessageListener.Message.Event.Polling
import org.eclipse.hara.ddiclient.api.MessageListener.Message.State.Idle
import org.eclipse.hara.ddiclient.integrationtest.abstractions.AbstractHaraMessageTest
import org.eclipse.hara.ddiclient.integrationtest.api.management.ServerSystemConfig
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.basic
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils.gatewayToken
import org.eclipse.hara.ddiclient.integrationtest.utils.addOkhttpLogger
import org.eclipse.hara.ddiclient.integrationtest.utils.logCurrentFunctionName
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import java.net.HttpURLConnection
import kotlin.time.Duration.Companion.seconds

class DdiClientHttpRequestsTest : AbstractHaraMessageTest() {

    override val targetId: String = "DoubleToken"
    private var expectedServerResponses = mutableListOf<ExpectedMessage>()

    override val expectedMessagesList: MutableList<MutableList<ExpectedMessage>> by lazy {
        super.expectedMessagesList.apply {
            add(expectedServerResponses)
        }
    }

    override val expectedMessagesAssertionListener =
        listOf(checkOkHttpExpectedMessages())

    private fun checkOkHttpExpectedMessages(): suspend (ExpectedMessage) -> Unit = { msg ->
        if (expectedServerResponses.isNotEmpty()) {
            assertEquals(msg, expectedServerResponses.removeFirst())
        }
    }

    companion object {
        const val TEST_TARGET_SECURITY_TOKEN = "r2m3ixxc86a2v4q81wntpyhr78zy08we"
    }

    override fun filterHaraMessages(message: MessageListener.Message): Boolean {
        return message is Polling || message is Idle
    }

    @BeforeClass
    override fun beforeTest() {
        super.beforeTest()
        setPollingTime("00:00:05")
    }

    @AfterClass
    override fun afterTest() {
        setPollingTime("00:00:30")
        runBlocking {
            enableTargetTokenInServer(true)
            enableGatewayTokenInServer(true)
        }
    }

    private fun createClient(
        targetToken: String? = TEST_TARGET_SECURITY_TOKEN,
        gatewayToken: String? = TestUtils.gatewayToken): HaraClient {

        val okHttpClient = OkHttpClient.Builder().apply {
            addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)

                val actualServerResponse = OkHttpMessage(
                    response.code, request.header("Authorization"))

                sendExpectedMessage(actualServerResponse)
                response
            })
            addOkhttpLogger()
        }

        return clientFromTargetId(
            okHttpClientBuilder = okHttpClient,
            targetToken = targetToken,
            gatewayToken = gatewayToken).invoke(targetId)
    }

    @Suppress("UNUSED_VARIABLE")
    @Test(enabled = true, priority = 1)
    fun useEmptyTokensTest() = runBlocking {
        try {
            val client = createClient(targetToken = "", gatewayToken = "")
        } catch (e: Throwable) {
            assertEquals(e.message, "gatewayToken and targetToken cannot both be empty")
        }
    }

    @Test(enabled = true, priority = 2)
    fun useNullTokensForAnonymousAuthorizationTest() {
        runBlocking {
            client = createClient(targetToken = null, gatewayToken = null)
            enableTargetTokenInServer(false)
            enableGatewayTokenInServer(false)

            expectPollingOnlyMessage()
            expectedServerResponses.apply {
                OkHttpMessage(
                    HttpURLConnection.HTTP_UNAUTHORIZED, null
                ).also {
                    add(it)
                }
            }

            startSubTestTest(true)
        }
    }

    @Test(enabled = true, timeOut = 60_000, priority = 3)
    fun useOnlyGatewayTokenTest() {
        runBlocking {
            client = createClient(targetToken = "")
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
        client = createClient(targetToken = "", gatewayToken = invalidGatewayToken)
        expectPollingOnlyMessage()
        expectedServerResponses.apply {
            add(gatewayTokenMessage(HttpURLConnection.HTTP_UNAUTHORIZED, invalidGatewayToken))
        }
        startSubTestTest(true)
    }

    @Test(enabled = true, timeOut = 60_000, priority = 4)
    fun useOnlyTargetTokenTest() {
        runBlocking {
            client = createClient(gatewayToken = "")
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
        client = createClient(targetToken = invalidTargetToken, gatewayToken = "")
        expectPollingOnlyMessage()
        expectedServerResponses.apply {
            add(targetTokenMessage(HttpURLConnection.HTTP_UNAUTHORIZED, invalidTargetToken))
        }
        startSubTestTest(true)
    }


    @Test(enabled = true, timeOut = 60_000, priority = 5)
    fun usingEmptyTargetTokenRequestShouldOnlyUseGatewayTokenTest() {
        runBlocking {
            client = createClient(targetToken = "")
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


    @Test(enabled = true, timeOut = 60_000, priority = 6)
    fun usingEmptyGatewayTokenRequestShouldOnlyUseTargetTokenTest() {
        runBlocking {
            enableTargetTokenInServer(true)
            enableGatewayTokenInServer(false)
            client = createClient(gatewayToken = "")

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

    @Test(enabled = true, timeOut = 200_000, priority = 7)
    fun providingBothTokensTest() {
        runBlocking {
            client = createClient()
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

    private fun expectPollingAndIdleMessages() {
        expectMessages(Polling, Idle)
    }

    private fun expectPollingOnlyMessage() {
        expectMessages(Polling)
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
        OkHttpMessage(
            responseCode,
            Authentication.newInstance(
                Authentication.AuthenticationType.TARGET_TOKEN_AUTHENTICATION,
                token
            ).headerValue
        )

    private fun gatewayTokenMessage(responseCode: Int = HttpURLConnection.HTTP_OK,
                                    token: String = gatewayToken) =
        OkHttpMessage(
            responseCode,
            Authentication.newInstance(
                Authentication.AuthenticationType.GATEWAY_TOKEN_AUTHENTICATION,
                token
            ).headerValue
        )

    data class OkHttpMessage(val code: Int, val authHeader: String?) :
        ExpectedMessage()

}
