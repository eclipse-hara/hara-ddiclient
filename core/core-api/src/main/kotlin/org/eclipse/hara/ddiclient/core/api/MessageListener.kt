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

interface MessageListener {

    fun onMessage(message: Message)

    sealed class Message(val description: String) {
        override fun toString(): String {
            return this.javaClass.simpleName
        }

        sealed class State(description: String) : Message(description) {
            data class Downloading(val artifacts: List<Artifact>) : State("Client is downloading artifacts from server") {
                data class Artifact(val name: String, val size: Long, val md5: String)
            }
            object Updating : State("The update process is started. Any request to cancel an update will be rejected")
            object CancellingUpdate : State("Last update request is being cancelled")
            object WaitingDownloadAuthorization : State("Waiting authorization to start download")
            object WaitingUpdateAuthorization : State("Waiting authorization to start update")
            object Idle : State("Client is waiting for new requests from server")
        }

        sealed class Event(description: String) : Message(description) {
            object Polling : Event("Client is contacting server to retrieve new action to execute")
            data class UpdateAvailable(val id: String) : Event("An update is available on cloud")
            data class StartDownloadFile(val fileName: String) : Event("A file downloading is started")
            data class FileDownloaded(val fileDownloaded: String) : Event("A file is downloaded")
            data class DownloadProgress(val fileName: String, val percentage: Double = 0.0) : Event("Percent of file downloaded")
            object AllFilesDownloaded : Event("All file needed are downloaded")
            data class UpdateFinished(val successApply: Boolean, val details: List<String>) : Event("The update is finished")
            data class Error(val details: List<String>) : Event("An error is occurred")
        }
    }
}
