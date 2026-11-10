/*
 * Copyright © 2026  Eurotech S.p.A.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hara.ddiclient.api.actors

import org.eclipse.hara.ddiclient.api.actors.FileDownloader.Companion.FileToDownload
import org.eclipse.hara.ddiclient.api.actors.FileDownloader.Companion.md5
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class FileDownloaderTest {

    private lateinit var folder: File

    @BeforeMethod
    fun createFolder() {
        folder = Files.createTempDirectory("hara-file-downloader").toFile()
    }

    @AfterMethod
    fun deleteFolder() {
        folder.deleteRecursively()
    }

    @Test
    fun md5IsLowercaseHex() {
        val file = File(folder, "hello").apply { writeText("hello") }

        assertEquals(file.md5(), "5d41402abc4b2a76b9719d911017c592")
    }

    @Test
    fun md5OfAnEmptyFile() {
        val file = File(folder, "empty").apply { writeBytes(ByteArray(0)) }

        assertEquals(file.md5(), "d41d8cd98f00b204e9800998ecf8427e")
    }

    @Test
    fun md5OfAFileLargerThanTheReadBuffer() {
        val content = ByteArray(10_000) { (it % 256).toByte() }
        val file = File(folder, "large").apply { writeBytes(content) }
        val expected = MessageDigest.getInstance("MD5").digest(content).joinToString("") { "%02x".format(it) }

        assertEquals(file.md5(), expected)
    }

    @Test
    fun filesAreNamedAfterTheMd5() {
        val toDownload = FileToDownload("image.bin", MD5, "http://server/image.bin", folder, 5)

        assertEquals(toDownload.tempFile, File(folder, "$MD5.downloading"))
        assertEquals(toDownload.destination, File(folder, MD5))
        assertEquals(toDownload.destinationMd5File, File(folder, "$MD5.md5"))
    }

    @Test
    fun onFileSavedMovesTheTempFileToTheDestination() {
        val toDownload = FileToDownload("image.bin", MD5, "http://server/image.bin", folder, 5)
        toDownload.tempFile.writeText("hello")

        assertTrue(toDownload.onFileSaved())
        assertFalse(toDownload.tempFile.exists())
        assertEquals(toDownload.destination.readText(), "hello")
    }

    companion object {
        private const val MD5 = "5d41402abc4b2a76b9719d911017c592"
    }
}
