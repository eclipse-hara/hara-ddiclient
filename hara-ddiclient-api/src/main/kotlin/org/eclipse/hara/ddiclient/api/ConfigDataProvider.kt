/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.api

/**
 * A provider that configures the device data that is sent
 * to the server
 */
interface ConfigDataProvider {

    /**
     * Device data that is sent to the server
     * @return the map that contains the device data
     */
    fun configData(): Map<String, String> = emptyMap()

    /**
     * @returns true if the map returned by [configData]
     * is the same that was sent to the server, false otherwise.
     */
    fun isUpdated(): Boolean = false

    /**
     * Callback called after the device data is successfully
     * notified to the server
     */
    fun onConfigDataUpdate() {}
}
