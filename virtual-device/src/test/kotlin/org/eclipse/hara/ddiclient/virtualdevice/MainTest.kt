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

package org.eclipse.hara.ddiclient.virtualdevice

import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import org.testng.Assert
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

class MainTest {

    companion object {
        const val HAWKBIT_URL = "http://localhost:8081"
        val BASIC_AUTH = Credentials.basic("test", "test")
        val GATEWAY_TOKEN = "66076ab945a127dd80b15e9011995109"

    }

    private val devicesControllerId = mutableListOf<String>()
    private lateinit var managementApi: ManagementApi

    @BeforeClass
    fun setUp() {
        mockkObject(Configuration)
        every { Configuration.url } returns HAWKBIT_URL
        every { Configuration.poolSize } returns 2
        every { Configuration.gatewayToken } returns GATEWAY_TOKEN
        every { Configuration.controllerIdGenerator } returns {
            val id = "VirtualDevice-number-$it"
            devicesControllerId.add(id)
            id
        }
        managementApi = ManagementClient.createManagementApi(HAWKBIT_URL)
        runBlocking {
            managementApi.setPollingTime(BASIC_AUTH, ServerSystemConfig("00:00:10"))
        }

    }

    @Test(enabled = true)
    fun testVirtualDeviceCreationAndPolling() {
        runBlocking {
            main()

            delay(2_000)
            val targets = managementApi.getAllTargets(BASIC_AUTH)
            val testTargets =
                targets.content.filter { devicesControllerId.contains(it.controllerId) }
            Assert.assertEquals(devicesControllerId.size, testTargets.size)
            testTargets
                .forEachIndexed { index, deviceTarget ->
                    Assert.assertEquals(devicesControllerId[index], deviceTarget.controllerId)
                    Assert.assertNotNull(deviceTarget.lastControllerRequestAt)
                }
            virtualMachineGlobalScope.cancel()
        }
    }
}