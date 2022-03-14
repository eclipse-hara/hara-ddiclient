package org.eclipse.hara.ddiclient.core.api

/**
 * Interface that customizes the client behaviour during the download phase
 */
interface DownloadBehavior {

    /**
     * Method that defines whether the client should execute a download or not.
     * @param attempt index of the next download attempt for the artifact identified by [artifactId].
     * Its value is in the [1, 2147483647] range, any attempt after 2147483647 will have index
     * 2147483647.
     * @param artifactId
     * @param previousError the error that caused the failure of the previous download attempt
     * @return [Try]
     */
    fun onAttempt(attempt:Int, artifactId:String, previousError:Throwable? = null):Try

    /**
     * Class that specifies whether the client should do another download attempt
     */
    sealed class Try{

        /**
         * The client has to stop trying to download the artifact
         */
        object Stop:Try()

        /**
         * The client has to try downloading the artifact after [seconds] seconds
         *
         * @param seconds the time in seconds that the client has to wait
         * before trying a download attempt
         */
        data class After(val seconds:Long):Try()
    }

}

