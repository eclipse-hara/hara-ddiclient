/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.core

import java.io.File
import java.lang.StringBuilder
import java.security.DigestInputStream
import java.security.MessageDigest

fun File.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    val sb = StringBuilder()

    inputStream().use { fis ->
        val digestInputStream = DigestInputStream(fis, md)
        val buffer = ByteArray(4096)
        while (digestInputStream.read(buffer) > -1) {}
        digestInputStream.close()
        digestInputStream.messageDigest
            .digest()
            .forEach {
                sb.append(String.format("%02X", it))
            }
    }
    return sb.toString().lowercase()
}


