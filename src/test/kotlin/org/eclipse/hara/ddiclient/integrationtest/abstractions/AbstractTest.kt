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

package org.eclipse.hara.ddiclient.integrationtest.abstractions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.eclipse.hara.ddiclient.api.ConfigDataProvider
import org.eclipse.hara.ddiclient.api.DeploymentPermitProvider
import org.eclipse.hara.ddiclient.api.DirectoryForArtifactsProvider
import org.eclipse.hara.ddiclient.api.DownloadBehavior
import org.eclipse.hara.ddiclient.api.HaraClient
import org.eclipse.hara.ddiclient.api.HaraClientData
import org.eclipse.hara.ddiclient.api.HaraClientDefaultImpl
import org.eclipse.hara.ddiclient.api.MessageListener
import org.eclipse.hara.ddiclient.api.Updater
import org.eclipse.hara.ddiclient.integrationtest.api.management.HawkbitAssignDistributionBody
import org.eclipse.hara.ddiclient.integrationtest.api.management.HawkbitTargetInfo
import org.eclipse.hara.ddiclient.integrationtest.api.management.ManagementApi
import org.eclipse.hara.ddiclient.integrationtest.api.management.ManagementClient
import org.eclipse.hara.ddiclient.integrationtest.api.management.ServerSystemConfig
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils
import org.eclipse.hara.ddiclient.integrationtest.utils.addOkhttpLogger
import org.testng.Assert
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import java.lang.Exception
import kotlin.coroutines.cancellation.CancellationException

abstract class AbstractTest {

    protected lateinit var managementApi: ManagementApi

    protected var client: HaraClient? = null
        set(value) {
            safeStopClient()
            field = value
        }


    @BeforeClass
    open fun beforeTest() {
        managementApi = ManagementClient.newInstance(TestUtils.hawkbitUrl)
    }

    @AfterClass
    open fun afterTest() {
    }


    protected fun setPollingTime(time: String) = runBlocking {
        managementApi.setPollingTime(TestUtils.basic, ServerSystemConfig(time))
    }

    protected suspend fun reCreateTestTargetOnServer(targetId: String) {
        runCatching {
            managementApi.deleteTarget(TestUtils.basic, targetId)
        }
        runCatching {
            managementApi.createTarget(
                TestUtils.basic, listOf(HawkbitTargetInfo(targetId)))
        }
    }

    protected suspend fun assignDistributionToTheTarget(
        targetId: String,
        distribution: HawkbitAssignDistributionBody): Int {
        val response =
            managementApi.assignDistributionToTarget(TestUtils.basic,
                targetId, distribution)
        if (response.assignedActions.isNotEmpty()) {
            return response.assignedActions.first().id
        }
        return -1
    }

    protected fun safeStopClient() {
        try {
            client?.stop()
        } catch (ignored: Exception) {
        }
    }

    protected suspend fun assertEquals(actual: Any?, expected: Any?) {
        assert {
            Assert.assertEquals(actual, expected)
        }
    }

    protected suspend fun assert(assertionBlock: suspend () -> Unit) {
        val throwableScope = CoroutineScope(Dispatchers.Default)
        val throwableJob = throwableScope.async {
            assertionBlock()
        }
        try {
            throwableJob.await()
        } catch (ignored: CancellationException) {
        }
    }

    open fun clientFromTargetId(
        directoryDataProvider: DirectoryForArtifactsProvider = TestUtils.directoryDataProvider,
        configDataProvider: ConfigDataProvider = TestUtils.configDataProvider,
        updater: Updater = TestUtils.updater,
        messageListeners: List<MessageListener> = listOf(),
        deploymentPermitProvider: DeploymentPermitProvider = object :
            DeploymentPermitProvider {},
        downloadBehavior: DownloadBehavior = TestUtils.downloadBehavior,
        okHttpClientBuilder: OkHttpClient.Builder = OkHttpClient.Builder().addOkhttpLogger(),
        targetToken: String? = "",
        gatewayToken: String? = TestUtils.gatewayToken,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default)): (String) -> HaraClient =
        { targetId ->
            val clientData = HaraClientData(
                TestUtils.tenantName,
                targetId,
                TestUtils.hawkbitUrl,
                gatewayToken, targetToken)

            val client = HaraClientDefaultImpl()

            client.init(
                clientData,
                directoryDataProvider,
                configDataProvider,
                deploymentPermitProvider,
                listOf(*messageListeners.toTypedArray()),
                listOf(updater),
                downloadBehavior,
                httpBuilder = okHttpClientBuilder,
                scope = scope
            )

            client
        }
}