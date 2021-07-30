/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.virtualdevice

import org.joda.time.Duration
import java.util.UUID

object Configuration {

    val logLevel = env("HARA_LOG_LEVEL", "TRACE")

    /**
     * Number of virtual device generated
     */
    val poolSize = env("HARA_CLIENT_POOL_SIZE", "1").toInt()

    val tenant = env("HAWKBIT_TENANT", "DEFAULT")
    val controllerIdGenerator = { env("HAWKBIT_CONTROLLER_ID", UUID.randomUUID().toString()) }
    val url = env("HAWKBIT_URL", "https://hawkbit.eclipseprojects.io")
    val gatewayToken = env("HAWKBIT_GATEWAY_TOKEN", "")

    /**
     * Each virtual device will be started with a random delay in [0, HARA_VIRTUAL_DEVICE_STARTING_DELAY]
     */
    val virtualDeviceStartingDelay = Duration.standardSeconds(
        env("HARA_VIRTUAL_DEVICE_STARTING_DELAY", "1").toLong()
    ).millis

    val storagePath = env("HARA_STORAGE_PATH", "/client")

    /**
     * A list of target attributes for each device.
     *
     * The string must have the form of: key1,value1|key2,value2|....|keyn,valuen
     *
     * The value supports the following template substitutions:
     * {0} is replaced with the virtual device id
     * {1} is replaced with the tenant
     * {2} is replaced with the controller id
     * {3} is replaced with the gatewayToken
     *
     * Example:
     *  the string "virtual_device_id,{0}|virtual_device_controller_id,{1}|client:kotlin" represents
     *  three target attributes:
     *  1- virtual_device_id = 7
     *  2- virtual_device_controller_id = a18b68b4-4c9b-4c8f-91af-a26d3a2d1008
     *  3- client = kotlin
     *
     */
    val targetAttributes = env("HARA_TARGET_ATTRIBUTES","client,kotlin virtual device")

    /**
     *
     * Template substitutions:
     * {0} is replaced with the virtual device id
     * {1} is replaced with the tenant
     * {2} is replaced with the controller id
     * {3} is replaced with the gatewayToken
     * {4} is replaced with the message
     *
     */
    val logMessageTemplate = env("HARA_LOG_MESSAGE", "{4}")

    /**
     *
     * Template substitutions:
     * {0} is replaced with the software module's name
     * {1} is replaced with the virtual device's id
     * {2} is replaced with the tenant
     * {3} is replaced with the controller id
     * {4} is replaced with the gatewayToken
     *
     */

    val srvMsgTemplateBeforeUpdate = env("HARA_SRV_MSF_BEFORE_UPDATE", "Applying the sw {0} for target {1}")

    /**
     *
     * Template substitutions:
     * {0} is replaced with the software module's name
     * {1} is replaced with the virtual device's id
     * {2} is replaced with the tenant
     * {3} is replaced with the controller id
     * {4} is replaced with the gatewayToken
     *
     */
    val srvMsgTemplateAfterUpdate = env("HARA_SRV_MSF_AFTER_UPDATE","Applied the sw {0} for target {1}")

    val grantDownload = env("HARA_GRANT_DOWNLOAD", "true").toBoolean()
    val grantUpdate = env("HARA_GRANT_UPDATE", "true").toBoolean()

    private fun env(envVariable:String, defaultValue:String):String{
        return System.getenv(envVariable) ?: defaultValue
    }

}