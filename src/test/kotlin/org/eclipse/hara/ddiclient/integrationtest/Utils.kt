/*
* Copyright © 2017-2021  Kynetics  LLC
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.eclipse.hara.ddiclient.integrationtest

import org.eclipse.hara.ddiclient.core.PathResolver
import org.eclipse.hara.ddiclient.core.api.Updater
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.eclipse.hara.ddiclient.core.api.ConfigDataProvider
import org.eclipse.hara.ddiclient.core.api.DirectoryForArtifactsProvider
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import java.io.File
import java.util.*
import java.util.concurrent.Executors

/**
 * @author Daniele Sergio
 */

data class ActionStatus(val content: Set<ContentEntry>, val total: Int = content.size, val size: Int = content.size) {
    data class ContentEntry(val type: Type, val messages: List<String?>) {
        enum class Type {
            finished, error, warning, pending, running, canceled, retrieved, canceling, download
        }
    }
}

data class Action(val status: Status){
    enum class Status {
        finished, pending
    }
}

interface ManagementApi {
    companion object {
        const val BASE_V1_REQUEST_MAPPING = "/rest/v1"
    }

    @GET("$BASE_V1_REQUEST_MAPPING/targets/{targetId}/actions/{actionId}/status")
    suspend fun getTargetActionStatusAsync(
        @Header("Authorization") auth: String,
        @Path("targetId") targetId: String,
        @Path("actionId") actionId: Int
    ): ActionStatus

    @GET("$BASE_V1_REQUEST_MAPPING/targets/{targetId}/actions/{actionId}")
    suspend fun getActionAsync(
        @Header("Authorization") auth: String,
        @Path("targetId") targetId: String,
        @Path("actionId") actionId: Int
    ): Action

    @DELETE("$BASE_V1_REQUEST_MAPPING/targets/{targetId}/actions/{actionId}")
    suspend fun deleteTargetActionAsync(
        @Header("Authorization") auth: String,
        @Path("targetId") targetId: String,
        @Path("actionId") actionId: Int
    ): Unit
}

object ManagementClient {

    fun newInstance(url: String): ManagementApi {
        return object : ManagementApi {
            private val delegate: ManagementApi = Retrofit.Builder().baseUrl(url)
                    .client(OkHttpClient.Builder().build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .callbackExecutor(Executors.newSingleThreadExecutor())
                    .build()
                    .create(ManagementApi::class.java)

            override suspend fun getTargetActionStatusAsync(auth: String, targetId: String, actionId: Int): ActionStatus {
                return delegate.getTargetActionStatusAsync(auth, targetId, actionId)
            }

            override suspend fun getActionAsync(auth: String, targetId: String, actionId: Int): Action {
                return delegate.getActionAsync(auth, targetId, actionId)
            }

            override suspend fun deleteTargetActionAsync(auth: String, targetId: String, actionId: Int): Unit {
                TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
            }
        }
    }
}

object TestUtils {

    data class TargetDeployments(
        val targetId: String,
        val targetToken: String,
        val deploymentInfo: List<DeploymentInfo>
    ) {
        data class DeploymentInfo(
                val actionId: Int,
                val actionStatusOnStart: ActionStatus,
                val actionStatusOnFinish: ActionStatus,
                val filesDownloadedPairedWithServerFile: Set<Pair<String, String>>
        )
    }

    val tenantName = "DEFAULT"
    val tenantNameToLower = tenantName.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val basic = Credentials.basic("test", "test")
    val hawkbitUrl = "http://localhost:8081"
    val downloadRootDirPath = "./build/test/download/"
    val gatewayToken = "66076ab945a127dd80b15e9011995109"
    val getDownloadDirectoryFromActionId = { actionId: String -> File("$downloadRootDirPath/$actionId") }
    val directoryDataProvider = object : DirectoryForArtifactsProvider { override fun directoryForArtifacts(): File = File(
        downloadRootDirPath
    ) }
    val pathResolver = PathResolver(directoryDataProvider)
    val configDataProvider = object : ConfigDataProvider {}
    val updater = object : Updater {
        override fun apply(modules: Set<Updater.SwModuleWithPath>, messenger: Updater.Messenger): Updater.UpdateResult {
            println("APPLY UPDATE $modules")
            messenger.sendMessageToServer("Applying the update...")
            messenger.sendMessageToServer("Update applied")
            return Updater.UpdateResult(true)
        }
    }

    val serverFilesMappedToLocantionAndMd5 = mapOf("test1" to Pair("docker/test/artifactrepo/$tenantName/4b/5a/b54e43082887d1e7cdb10b7a21fe4a1e56b44b5a", "2490a3d39b0004e4afeb517ef0ddbe2d"),
            "test2" to Pair("docker/test/artifactrepo/$tenantName/b6/1e/a096a9d3cb96fa4cf6c63bd736a84cb7a7e4b61e", "b0b3b0dbf5330e3179c6ae3e0ac524c9"),
            "test3" to Pair("docker/test/artifactrepo/$tenantName/bf/94/cde0c01b26634f869bb876326e4fbe969792bf94", "2244fbd6bee5dcbe312e387c062ce6e6"),
            "test4" to Pair("docker/test/artifactrepo/$tenantName/dd/0a/07fa4d03ac54d0b2a52f23d8e878c96db7aadd0a", "94424c5ce3f8c57a5b26d02f37dc06fc"))

    val md5OfFileNamed: (String) -> String = { key -> serverFilesMappedToLocantionAndMd5.getValue(key).second }
    val locationOfFileNamed: (String) -> String = { key -> serverFilesMappedToLocantionAndMd5.getValue(key).first }

    val test1Artifact = Updater.SwModule.Artifact("test1", Updater.Hashes("",
        md5OfFileNamed("test1")
    ), 0)
    val test2Artifact = Updater.SwModule.Artifact("test2", Updater.Hashes("",
        md5OfFileNamed("test2")
    ), 0)
    val test3Artifact = Updater.SwModule.Artifact("test3", Updater.Hashes("",
        md5OfFileNamed("test3")
    ), 0)
    val test4Artifact = Updater.SwModule.Artifact("test4", Updater.Hashes("",
        md5OfFileNamed("test4")
    ), 0)

    fun messagesOnSuccessfullyDownloadOsWithAppDistribution(target: String) = arrayOf(
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf("successfully downloaded all files")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf(
                            "successfully downloaded file with md5 ${
                                md5OfFileNamed(
                                        "test1"
                                )
                            }"
                    )
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf(
                            "successfully downloaded file with md5 ${
                                md5OfFileNamed(
                                        "test2"
                                )
                            }"
                    )
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf(
                            "successfully downloaded file with md5 ${
                                md5OfFileNamed(
                                        "test3"
                                )
                            }"
                    )
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf(
                            "successfully downloaded file with md5 ${
                                md5OfFileNamed(
                                        "test4"
                                )
                            }"
                    )
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.download,
                    listOf("Update Server: Target downloads /$tenantNameToLower/controller/v1/$target/softwaremodules/2/artifacts/test_2")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.download,
                    listOf("Update Server: Target downloads /$tenantNameToLower/controller/v1/$target/softwaremodules/2/artifacts/test_3")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.download,
                    listOf("Update Server: Target downloads /$tenantNameToLower/controller/v1/$target/softwaremodules/1/artifacts/test_1")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.download,
                    listOf("Update Server: Target downloads /$tenantNameToLower/controller/v1/$target/softwaremodules/3/artifacts/test_4")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf("Start downloading 4 files")
            )
    )

    fun messagesOnSuccefullyDownloadOsDistribution(target: String) = arrayOf(
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf("successfully downloaded all files")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf(
                            "successfully downloaded file with md5 ${
                                md5OfFileNamed(
                                        "test4"
                                )
                            }"
                    )
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.download,
                    listOf("Update Server: Target downloads /$tenantNameToLower/controller/v1/$target/softwaremodules/3/artifacts/test_4")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf("Start downloading 1 files")
            )
    )

    fun messagesOnSuccefullyDownloadAppDistribution(target: String) = arrayOf(
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf("successfully downloaded all files")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf(
                            "successfully downloaded file with md5 ${
                                md5OfFileNamed(
                                        "test1"
                                )
                            }"
                    )
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.download,
                    listOf("Update Server: Target downloads /$tenantNameToLower/controller/v1/$target/softwaremodules/1/artifacts/test_1")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf("Start downloading 1 files")
            )
    )

    val endMessagesOnSuccessUpdate = arrayOf(
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.finished,
                    listOf("Details:")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf("Update applied")
            ),
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.running,
                    listOf("Applying the update...")
            )
    )

    val firstActionEntry = ActionStatus.ContentEntry(
            ActionStatus.ContentEntry.Type.running,
            listOf(null)
    )

    val startMessagesOnUpdateFond = arrayOf(
            ActionStatus.ContentEntry(
                    ActionStatus.ContentEntry.Type.retrieved,
                    listOf("Update Server: Target retrieved update action and should start now the download.")
            ),
        firstActionEntry
    )

    fun filesDownloadedInOsWithAppsPairedToServerFile(action: Int) = setOf(
        pathResolver.fromArtifact(action.toString()).invoke(
            test1Artifact
        ) to locationOfFileNamed("test1"),
            pathResolver.fromArtifact(action.toString()).invoke(
                test2Artifact
            ) to locationOfFileNamed("test2"),
            pathResolver.fromArtifact(action.toString()).invoke(
                test3Artifact
            ) to locationOfFileNamed("test3"),
            pathResolver.fromArtifact(action.toString()).invoke(
                test4Artifact
            ) to locationOfFileNamed("test4")
    )

    val defaultActionStatusOnStart =
        ActionStatus(setOf(firstActionEntry))
}
