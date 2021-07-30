/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.core.inputstream

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

class FilterInputStreamWithProgress(
    inputStream: InputStream,
    private val totalSize: Long
) : FilterInputStream(inputStream) {

    private var alreadyRead: AtomicInteger = AtomicInteger(0)

    @Throws(IOException::class)
    override fun read(): Int {
        try {
            val count = this.`in`.read()
            alreadyRead.addAndGet(count)
            return count
        } catch (e: IOException) {
            throw e
        }
    }

    @Throws(IOException::class)
    override fun read(var1: ByteArray, var2: Int, var3: Int): Int {
        try {
            val count = this.`in`.read(var1, var2, var3)
            alreadyRead.addAndGet(count)
            return count
        } catch (e: IOException) {
            throw e
        }
    }

    fun getProgress(): Double {
        return alreadyRead.get().toDouble() / totalSize
    }
}
