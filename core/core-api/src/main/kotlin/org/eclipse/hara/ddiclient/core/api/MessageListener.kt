/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.core.api

/**
 * Listener to be up to date with the client state.
 */
interface MessageListener {

    /**
     * Callback called to notify an event or a state change
     */
    fun onMessage(message: Message)

    /**
     * Message notified by the client.
     * @property description string describing the state/event
     */
    sealed class Message(val description: String) {
        override fun toString(): String {
            return this.javaClass.simpleName
        }

        /**
         * State messages
         */
        sealed class State(description: String) : Message(description) {

            /**
             * Client is downloading artifacts from server
             */
            data class Downloading(val artifacts: List<Artifact>) : State("Client is downloading artifacts from server") {
                /**
                 * @property name
                 * @property size in bytes
                 * @property md5
                 */
                data class Artifact(val name: String, val size: Long, val md5: String)
            }

            /**
             * The update process is started.
             * Any request to cancel an update will be rejected
             */
            object Updating : State("The update process is started. Any request to cancel an update will be rejected")

            /**
             * Last update request is being cancelled.
             */
            object CancellingUpdate : State("Last update request is being cancelled")

            /**
             * Client is waiting for the authorization to start downloading
             */
            object WaitingDownloadAuthorization : State("Waiting authorization to start download")

            /**
             * Client is waiting for the authorization to start updating
             */
            object WaitingUpdateAuthorization : State("Waiting authorization to start update")

            /**
             * Client is waiting for new requests from server
             */
            object Idle : State("Client is waiting for new requests from server")
        }

        /**
         * Event messages
         */
        sealed class Event(description: String) : Message(description) {

            /**
             * Client is contacting server to retrieve new actions to execute
             */
            object Polling : Event("Client is contacting server to retrieve new action to execute")

            /**
             *  An update is available on the server
             */
            data class UpdateAvailable(val id: String) : Event("An update is available on the server")

            /**
             * A file download has started
             */
            data class StartDownloadFile(val fileName: String) : Event("A file download has started")

            /**
             * A file has been successfully downloaded
             */
            data class FileDownloaded(val fileDownloaded: String) : Event("A file has been downloaded")

            /**
             * Progress of a download file
             * @property fileName, name of the file that the client is downloading
             * @property percentage, percentage of downloaded file
             */
            data class DownloadProgress(val fileName: String, val percentage: Double = 0.0) : Event("Percent of file downloaded")

            /**
             * All files have been successfully downloaded
             */
            object AllFilesDownloaded : Event("All file needed have been downloaded")

            /**
             * The update has finished.
             * @property successApply, true if the update has been successfully applied, false otherwise
             * @property details
             */
            data class UpdateFinished(val successApply: Boolean, val details: List<String>) : Event("The update has finished")

            /**
             * An error occurred during the update.
             * @property details, contains additional info about the error
             */
            data class Error(val details: List<String>) : Event("An error has occurred")
        }
    }
}
