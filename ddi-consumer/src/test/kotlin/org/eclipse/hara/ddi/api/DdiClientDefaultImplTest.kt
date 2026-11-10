/*
 * Copyright © 2026  Eurotech S.p.A.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddi.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.eclipse.hara.ddi.api.model.ConfigurationDataRequest
import org.eclipse.hara.ddi.api.model.DeploymentFeedbackRequest
import org.eclipse.hara.ddiclient.api.HaraClientData
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.Assert.expectThrows
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

/**
 * The DDI client against an in-process HTTP server: request paths, headers and
 * authentication, without a hawkBit server.
 */
class DdiClientDefaultImplTest {

    private lateinit var server: MockWebServer

    @BeforeMethod
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterMethod(alwaysRun = true)
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun controllerBaseRequest() = runBlocking {
        server.enqueue(json(CONTROLLER_BASE))

        val response = client(targetToken = "tt").getControllerActions()

        val request = server.next()
        assertEquals(request.method, "GET")
        assertEquals(request.path, "/$TENANT/controller/v1/$CONTROLLER_ID")
        assertEquals(request.getHeader("Accept"), "application/hal+json")
        assertEquals(response.config.polling.sleep, "00:05:00")
        assertEquals(response.deploymentActionId(), "42")
    }

    @Test
    fun targetTokenAuthentication() = runBlocking {
        server.enqueue(json(CONTROLLER_BASE))

        client(targetToken = "tt").getControllerActions()

        assertEquals(server.next().getHeader("Authorization"), "TargetToken tt")
    }

    @Test
    fun gatewayTokenAuthentication() = runBlocking {
        server.enqueue(json(CONTROLLER_BASE))

        client(gatewayToken = "gt").getControllerActions()

        assertEquals(server.next().getHeader("Authorization"), "GatewayToken gt")
    }

    // Client certificate authentication (mTLS): no Authorization header at all
    @Test
    fun noTokenSendsNoAuthorizationHeader() = runBlocking {
        server.enqueue(json(CONTROLLER_BASE))

        client().getControllerActions()

        assertNull(server.next().getHeader("Authorization"))
    }

    @Test
    fun rejectedTargetTokenFallsBackToGatewayTokenAndKeepsIt() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(json(CONTROLLER_BASE))
        server.enqueue(json(CONTROLLER_BASE))
        val client = client(targetToken = "tt", gatewayToken = "gt")

        client.getControllerActions()
        client.getControllerActions()

        assertEquals(server.next().getHeader("Authorization"), "TargetToken tt")
        assertEquals(server.next().getHeader("Authorization"), "GatewayToken gt")
        assertEquals(server.next().getHeader("Authorization"), "GatewayToken gt")
    }

    @Test
    fun serverErrorThrowsHttpException() {
        server.enqueue(MockResponse().setResponseCode(500))
        val client = client(targetToken = "tt")

        val error = expectThrows(HttpException::class.java) {
            runBlocking { client.getControllerActions() }
        }

        assertEquals(error.code(), 500)
    }

    @Test
    fun unchangedControllerBase() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(304))
        var changed = false
        var unchanged = false

        client(targetToken = "tt").onControllerActionsChange(
            "etag-1",
            onChange = { _, _ -> changed = true },
            onNothingChange = { unchanged = true })

        assertEquals(server.next().getHeader("If-None-Match"), "etag-1")
        assertFalse(changed)
        assertTrue(unchanged)
    }

    @Test
    fun changedControllerBaseHandsOverTheNewEtag() = runBlocking {
        server.enqueue(json(CONTROLLER_BASE).addHeader("ETag", "etag-2"))
        var newEtag: String? = null

        client(targetToken = "tt").onControllerActionsChange(
            "etag-1",
            onChange = { _, etag -> newEtag = etag })

        assertEquals(newEtag, "etag-2")
    }

    @Test
    fun deploymentBaseRequest() = runBlocking {
        server.enqueue(json(DEPLOYMENT_BASE))

        val response = client(targetToken = "tt").getDeploymentActionDetails("42", 0)

        assertEquals(server.next().path, "/$TENANT/controller/v1/$CONTROLLER_ID/deploymentBase/42?actionHistory=0")
        assertEquals(response.deployment.chunks.single().artifacts.single().filename, "image.bin")
    }

    @Test
    fun deploymentFeedbackRequest() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        val feedback = DeploymentFeedbackRequest.newInstance(
            "42",
            DeploymentFeedbackRequest.Status.Execution.closed,
            DeploymentFeedbackRequest.Status.Result.Progress(1, 1),
            DeploymentFeedbackRequest.Status.Result.Finished.success,
            "installed")

        client(targetToken = "tt").postDeploymentActionFeedback("42", feedback)

        val request = server.next()
        assertEquals(request.method, "POST")
        assertEquals(request.path, "/$TENANT/controller/v1/$CONTROLLER_ID/deploymentBase/42/feedback")
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"execution\":\"closed\""), body)
        assertTrue(body.contains("\"finished\":\"success\""), body)
    }

    @Test
    fun configDataAcceptedCallsBack() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        var called = false

        client(targetToken = "tt").putConfigData(configData()) { called = true }

        val request = server.next()
        assertEquals(request.method, "PUT")
        assertEquals(request.path, "/$TENANT/controller/v1/$CONTROLLER_ID/configData")
        assertTrue(called)
    }

    @Test
    fun configDataRejectedDoesNotCallBack() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        var called = false

        client(targetToken = "tt").putConfigData(configData()) { called = true }

        assertFalse(called)
    }

    @Test
    fun artifactDownloadStreamsTheBodyAndAuthenticates() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("artifact content"))
        val url = server.url("/$TENANT/controller/v1/$CONTROLLER_ID/softwaremodules/3/artifacts/image.bin").toString()

        val content = client(targetToken = "tt").downloadArtifact(url).use { it.readBytes().decodeToString() }

        assertEquals(content, "artifact content")
        assertEquals(server.next().getHeader("Authorization"), "TargetToken tt")
    }

    private fun client(gatewayToken: String? = null, targetToken: String? = null) = DdiClientDefaultImpl.of(
        HaraClientData(TENANT, CONTROLLER_ID, server.url("/").toString(), gatewayToken, targetToken),
        OkHttpClient.Builder())

    private fun configData() = ConfigurationDataRequest.of(mapOf("k" to "v"), ConfigurationDataRequest.Mode.merge)

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/hal+json")
        .setBody(body)

    private fun MockWebServer.next(): RecordedRequest = takeRequest(5, TimeUnit.SECONDS)!!

    companion object {
        private const val TENANT = "DEFAULT"
        private const val CONTROLLER_ID = "device-1"

        private const val CONTROLLER_BASE = """
            {"config":{"polling":{"sleep":"00:05:00"}},
             "_links":{"deploymentBase":{"href":"http://localhost/DEFAULT/controller/v1/device-1/deploymentBase/42?c=-1"}}}
        """

        private const val DEPLOYMENT_BASE = """
            {"id":"42",
             "deployment":{"download":"forced","update":"forced",
               "chunks":[{"part":"os","version":"1.0","name":"linux",
                 "artifacts":[{"filename":"image.bin","hashes":{"sha1":"s1","md5":"m1"},"size":5,
                   "_links":{"download-http":{"href":"http://localhost/x"}}}]}]}}
        """
    }
}
