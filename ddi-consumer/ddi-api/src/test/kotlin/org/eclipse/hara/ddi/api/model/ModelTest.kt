/*
 * Copyright © 2026  Eurotech S.p.A.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddi.api.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import java.time.Instant

class ModelTest {

    private val gson = Gson()

    @Test
    fun controllerBaseWithADeployment() {
        val response = gson.fromJson(
            """
            {"config":{"polling":{"sleep":"00:05:00"}},
             "_links":{"deploymentBase":{"href":"$BASE/deploymentBase/42?c=-2129030598"},
                       "configData":{"href":"$BASE/configData"}}}
            """, ControllerBaseResponse::class.java)

        assertEquals(response.config.polling.sleep, "00:05:00")
        assertTrue(response.requireDeployment())
        assertTrue(response.requireConfigData())
        assertFalse(response.requireCancel())
        assertEquals(response.deploymentActionId(), "42")
    }

    @Test
    fun controllerBaseWithACancel() {
        val response = gson.fromJson(
            """
            {"config":{"polling":{"sleep":"00:00:30"}},
             "_links":{"cancelAction":{"href":"$BASE/cancelAction/7"}}}
            """, ControllerBaseResponse::class.java)

        assertTrue(response.requireCancel())
        assertFalse(response.requireDeployment())
        assertEquals(response.cancelActionId(), "7")
    }

    @Test
    fun controllerBaseWithNothingToDo() {
        val response = gson.fromJson(
            """{"config":{"polling":{"sleep":"00:00:30"}},"_links":{}}""", ControllerBaseResponse::class.java)

        assertFalse(response.requireDeployment())
        assertFalse(response.requireCancel())
        assertFalse(response.requireConfigData())
    }

    @Test
    fun deploymentBase() {
        val response = gson.fromJson(
            """
            {"id":"42",
             "deployment":{"download":"forced","update":"attempt",
               "chunks":[{"part":"os","version":"1.0","name":"linux",
                 "metadata":[{"key":"k","value":"v"}],
                 "artifacts":[{"filename":"image.bin",
                   "hashes":{"sha1":"s1","md5":"m1","sha256":"s256"},
                   "size":1024,
                   "_links":{"download-http":{"href":"$BASE/softwaremodules/3/artifacts/image.bin"},
                             "md5sum-http":{"href":"$BASE/softwaremodules/3/artifacts/image.bin.MD5SUM"}}}]}]}}
            """, DeploymentBaseResponse::class.java)

        assertEquals(response.id, "42")
        assertEquals(response.deployment.download, DeploymentBaseResponse.Deployment.ProvisioningType.forced)
        assertEquals(response.deployment.update, DeploymentBaseResponse.Deployment.ProvisioningType.attempt)
        assertNull(response.actionHistory)

        val chunk = response.deployment.chunks.single()
        assertEquals(chunk.part, "os")
        assertEquals(chunk.metadata, setOf(DeploymentBaseResponse.Deployment.Chunks.Metadata("k", "v")))

        val artifact = chunk.artifacts.single()
        assertEquals(artifact.size, 1024L)
        assertEquals(artifact.hashes.md5, "m1")
        assertEquals(artifact._links.download_http?.href, "$BASE/softwaremodules/3/artifacts/image.bin")
        assertNull(artifact._links.download)
    }

    @Test
    fun deploymentFeedbackJson() {
        val feedback = DeploymentFeedbackRequest.newInstance(
            "42",
            DeploymentFeedbackRequest.Status.Execution.proceeding,
            DeploymentFeedbackRequest.Status.Result.Progress(3, 1),
            DeploymentFeedbackRequest.Status.Result.Finished.none,
            "downloading")

        val json = JsonParser.parseString(gson.toJson(feedback)).asJsonObject
        val status = json.getAsJsonObject("status")
        val result = status.getAsJsonObject("result")

        assertEquals(json.get("id").asString, "42")
        assertEquals(status.get("execution").asString, "proceeding")
        assertEquals(status.getAsJsonArray("details").single().asString, "downloading")
        assertEquals(result.get("finished").asString, "none")
        assertEquals(result.getAsJsonObject("progress").get("of").asInt, 3)
        assertEquals(result.getAsJsonObject("progress").get("cnt").asInt, 1)
    }

    @Test
    fun feedbackTimeIsTheCurrentInstantWithMilliseconds() {
        val before = Instant.now().toEpochMilli()
        val time = CancelFeedbackRequest.newInstance(
            "7",
            CancelFeedbackRequest.Status.Execution.closed,
            CancelFeedbackRequest.Status.Result.Finished.success).time
        val after = Instant.now().toEpochMilli()

        assertTrue(time.matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")), time)
        assertTrue(Instant.parse(time).toEpochMilli() in before..after, time)
    }

    companion object {
        private const val BASE = "https://hawkbit.example.com/DEFAULT/controller/v1/device-1"
    }
}
