/*
 * Copyright © 2017-2024  Kynetics  LLC
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.integrationtest.utils

import org.eclipse.hara.ddiclient.api.HaraClient
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Date

val currentTime: String
    get() = SimpleDateFormat("HH.mm.ss.SSS").format(Date())

fun String.log() {
    println("$currentTime: $this")
}
fun String.internalLog() {
    if (LOG_INTERNAL) {
        "INTERNAL: $this".log()
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun logCurrentFunctionName() {
    "Running Test: ${Thread.currentThread().stackTrace[1].methodName}".log()
}

fun HaraClient.safeStopClient() {
    try {
        stop()
    } catch (ignored: Exception) {
    }
}