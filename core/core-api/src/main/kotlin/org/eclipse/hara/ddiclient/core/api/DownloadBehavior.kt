package org.eclipse.hara.ddiclient.core.api

/**
 * Interface that customizes the client behaviour during the download phase
 */
interface DownloadBehavior {

    /**
     * Method that defines if the client should execute a download or not.
     * @param attempt next download attempt for the artifact identified by [artifactId]
     * @param artifactId
     * @param previousError the error that has made fail the previous download attempt
     *
     * @return [Try]
     */
    fun onAttempt(attempt:Int, artifactId:String, previousError:Throwable? = null):Try

    /**
     * Class that specified if the client should do another download attempt
     */
    sealed class Try{

        /**
         * The client have to stop to try downloading the artifact
         */
        object Stop:Try()

        /**
         * The client have to try download a file after [seconds] seconds
         *
         * @param seconds the time in seconds that the client has to wait
         * before tries download the artifact
         */
        data class After(val seconds:Long):Try()
    }

}

