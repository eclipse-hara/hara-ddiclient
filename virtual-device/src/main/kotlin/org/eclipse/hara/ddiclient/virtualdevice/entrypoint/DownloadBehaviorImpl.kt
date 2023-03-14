/*
 * Copyright © 2017-2023  Kynetics  LLC
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.virtualdevice.entrypoint

import org.eclipse.hara.ddiclient.api.DownloadBehavior
import kotlin.math.pow

class DownloadBehaviorImpl(
    private val maxDelay:Long = 3600,
    private val minDelay:Long = 10,
    private val maxAttempts:Int = 36
): DownloadBehavior {

    override fun onAttempt(attempt: Int, artifactId:String, previousError: Throwable?): DownloadBehavior.Try {
        return when{
            attempt == 1 -> DownloadBehavior.Try.After(0)

            attempt > maxAttempts -> DownloadBehavior.Try.Stop

            else -> 2.0.pow(attempt.toDouble()).toLong().coerceIn(minDelay, maxDelay)
                .run { DownloadBehavior.Try.After(this) }
        }

    }

}