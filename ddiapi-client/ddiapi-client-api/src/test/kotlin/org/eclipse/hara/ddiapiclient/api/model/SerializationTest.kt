/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiapiclient.api.model

import com.google.gson.Gson
import org.testng.Assert.assertEquals
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

class SerializationTest {

    val gson = Gson()

    @DataProvider(name = "Serialization")
    fun objectsToSerialize(): Array<Any> {
        val cfgDataReq = ConfigurationDataRequest.of(emptyMap(), ConfigurationDataRequest.Mode.merge)
        return arrayOf(cfgDataReq.copy(data = mapOf("ciao" to "miao")))
    }

    @Test(dataProvider = "Serialization")
    fun serialization(expected: Any) {
        val json = gson.toJson(expected)
        println(json)
        val actual = gson.fromJson(json, expected.javaClass)
        assertEquals(actual, expected)
    }
}
