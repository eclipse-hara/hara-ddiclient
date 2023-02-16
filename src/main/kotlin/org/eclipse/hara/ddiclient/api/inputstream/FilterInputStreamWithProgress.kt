/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.api.inputstream

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

class FilterInputStreamWithProgress(
    inputStream: InputStream,
    private val totalSize: Long
) : FilterInputStream(inputStream) {

    private var alreadyRead: Int = 0

    @Throws(IOException::class)
    override fun read(): Int {
        return `in`.read().also {
            alreadyRead += it
        }
    }

    @Throws(IOException::class)
    override fun read(var1: ByteArray, var2: Int, var3: Int): Int {
        return `in`.read(var1, var2, var3).also {
            alreadyRead += it
        }
    }

    fun getProgress(): Double {
        return min(alreadyRead.toDouble() / totalSize, 1.0)
    }
}
