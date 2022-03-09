package org.eclipse.hara.ddiclient.virtualdevice.entrypoint

import org.eclipse.hara.ddiclient.core.api.DownloadBehavior
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