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
     * Callback called to notify an event or a change in status
     */
    fun onMessage(message: Message)

    /**
     * Messages notify by the client.
     * @param description, description of the state/event
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
                 * @param name of artifact
                 * @param size of artifact in bytes
                 * @param md5 of artifact
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
             * Client is waiting the authorization to start download
             */
            object WaitingDownloadAuthorization : State("Waiting authorization to start download")

            /**
             * Client is waiting the authorization to start update
             */
            object WaitingUpdateAuthorization : State("Waiting authorization to start update")

            /**
             * Client is waiting for new requests from server"
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
             *  An update is available on cloud
             */
            data class UpdateAvailable(val id: String) : Event("An update is available on cloud")

            /**
             * A file downloading is started
             */
            data class StartDownloadFile(val fileName: String) : Event("A file downloading is started")

            /**
             * A file is successfully downloaded
             */
            data class FileDownloaded(val fileDownloaded: String) : Event("A file is downloaded")

            /**
             * Progress of a download file
             * @param fileName, name of the file the client is downloading
             * @param percentage, percentage of downloaded file
             */
            data class DownloadProgress(val fileName: String, val percentage: Double = 0.0) : Event("Percent of file downloaded")

            /**
             * All files are successfully downloaded
             */
            object AllFilesDownloaded : Event("All file needed are downloaded")

            /**
             * The update is finished.
             * @param successApply, true if the update is successfully applied, false otherwise
             * @param details,
             */
            data class UpdateFinished(val successApply: Boolean, val details: List<String>) : Event("The update is finished")

            /**
             * Error occurred during the update.
             * @param details, contains additional info about the error
             */
            data class Error(val details: List<String>) : Event("An error is occurred")
        }
    }
}
