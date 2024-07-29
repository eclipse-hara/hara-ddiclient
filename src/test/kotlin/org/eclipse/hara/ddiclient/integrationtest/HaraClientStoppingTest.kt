/*
 *
 *  * Copyright © 2017-2024  Kynetics  LLC
 *  *
 *  * This program and the accompanying materials are made
 *  * available under the terms of the Eclipse Public License 2.0
 *  * which is available at https://www.eclipse.org/legal/epl-2.0/
 *  *
 *  * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.hara.ddiclient.integrationtest

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.hara.ddiclient.api.DownloadBehavior
import org.eclipse.hara.ddiclient.api.HaraClient
import org.eclipse.hara.ddiclient.api.MessageListener
import org.eclipse.hara.ddiclient.api.Updater
import org.eclipse.hara.ddiclient.integrationtest.abstractions.AbstractHaraMessageTest.ExpectedMessage
import org.eclipse.hara.ddiclient.integrationtest.abstractions.AbstractTest
import org.eclipse.hara.ddiclient.integrationtest.api.management.AssignDistributionType
import org.eclipse.hara.ddiclient.integrationtest.api.management.HawkbitAssignDistributionBody
import org.eclipse.hara.ddiclient.integrationtest.utils.TestUtils
import org.eclipse.hara.ddiclient.integrationtest.utils.internalLog
import org.eclipse.hara.ddiclient.integrationtest.utils.logCurrentFunctionName
import org.testng.annotations.AfterClass
import org.testng.annotations.Test
import kotlin.coroutines.cancellation.CancellationException

class HaraClientStoppingTest : AbstractTest() {

   companion object {
      const val TARGET_ID = "HaraClientStoppingTest"
   }

   private val testScope = CoroutineScope(Dispatchers.Default)
   private var haraScope = CoroutineScope(Dispatchers.Default)

   private val fiveSecondsDelayDownloadBehavior = object : DownloadBehavior {
      override fun onAttempt(attempt: Int, artifactId: String,
                             previousError: Throwable?): DownloadBehavior.Try {
         return DownloadBehavior.Try.After(5)
      }
   }

   private fun createMessageListener(channel: Channel<ExpectedMessage>): MessageListener {
      return object : MessageListener {
         override fun onMessage(message: MessageListener.Message) {
            runBlocking {
               "Received message: $message".internalLog()
               channel.send(ExpectedMessage.HaraMessage(message))
            }
         }
      }
   }


   @AfterClass
   override fun afterTest() {
      super.afterTest()
      setPollingTime("00:00:10")
   }

   @Test(enabled = true, priority = 41, timeOut = 45_000, invocationCount = 1)
   fun haraClientShouldStopPollingAfterBeingStoppedInDownloadingState() {
      logCurrentFunctionName()
      stoppingHaraClientWhileInDownloadingStateTestTemplate(TestUtils.OS_DISTRIBUTION_ID)
   }

   @Test(enabled = true, priority = 42, timeOut = 45_000, invocationCount = 1)
   fun haraClientShouldStopPollingAfterBeingStoppedInDownloadingStateForMultipleArtifacts() {
      logCurrentFunctionName()
      stoppingHaraClientWhileInDownloadingStateTestTemplate(
         TestUtils.OS_WITH_APPS_DISTRIBUTION_ID)
   }

   @Test(enabled = true, priority = 43, timeOut = 60_000, invocationCount = 1)
   fun haraClientShouldStopPollingAfterBeingStoppedInUpdatingState() {
      logCurrentFunctionName()
      runTest { testJob ->
         setPollingTime("00:00:05")
         val expectedMessageChannel = Channel<ExpectedMessage>(5, BufferOverflow.DROP_OLDEST)

         val updater = object : Updater {
            override fun apply(modules: Set<Updater.SwModuleWithPath>,
                               messenger: Updater.Messenger): Updater.UpdateResult {
               haraScope.launch {
                  "Applying long running fake update (8 sec) for modules: $modules".internalLog()
                  delay(8_000)
                  "Update applied".internalLog()
                  messenger.sendMessageToServer("Update applied")
               }
               return Updater.UpdateResult(true)
            }
         }

         haraClientStopTestTemplate(
            updater = updater,
            expectedMessageChannel = expectedMessageChannel,
         ) { msg, testClient ->
            if (msg is MessageListener.Message.State.Updating) {
               testClient.stop()
               "Client stopped".internalLog()
               runBlockWhileEnsuringPollingIsNotDetected(
                  expectedMessageChannel = expectedMessageChannel) {
                  "waiting for 10 seconds to ensure that updating and polling is stopped".internalLog()
                  delay(10_000) // wait for update to finish
                  testJob?.cancel()
               }
            }
         }
      }
   }

   @Test(enabled = true, priority = 44, timeOut = 45_000, invocationCount = 1)
   fun haraClientShouldPollAfterRestartedInDownloadingState() {
      logCurrentFunctionName()
      runTest { testJob ->

         setPollingTime("00:00:10")
         reCreateTestTargetOnServer(TARGET_ID)
         assignHeavyOTAUpdateToTheTarget(TestUtils.OS_DISTRIBUTION_ID)

         val expectedMessage = mutableListOf<ExpectedMessage.HaraMessage>()
         val expectedMessageChannel = Channel<ExpectedMessage>(5, BufferOverflow.DROP_OLDEST)

         var testClient = createClient(
            expectedMessageChannel, downloadBehavior = fiveSecondsDelayDownloadBehavior)

         testClient.startAsync()

         listenToMessages(expectedMessageChannel, testClient) { msg, _ ->
            if (expectedMessage.isNotEmpty()) {
               assertEquals(msg, expectedMessage.removeFirst().message)
               testJob?.cancel()
            } else if (msg is MessageListener.Message.Event.StartDownloadFile) {
               testClient.stop()
               "Client stopped".internalLog()
               delay(2_000)
               // The client should start polling after restarting the HaraClient
               testClient = createClient(expectedMessageChannel)
               expectedMessage.add(
                  ExpectedMessage.HaraMessage(MessageListener.Message.Event.Polling))
               testClient.startAsync()
            }
         }
      }
   }

   private fun stoppingHaraClientWhileInDownloadingStateTestTemplate(distributionId: Int) {
      runTest { testJob ->
         setPollingTime("00:00:05")
         val expectedMessageChannel = Channel<ExpectedMessage>(5, BufferOverflow.DROP_OLDEST)

         haraClientStopTestTemplate(
            downloadBehavior = fiveSecondsDelayDownloadBehavior,
            expectedMessageChannel = expectedMessageChannel,
            distributionId = distributionId
         ) { msg, testClient ->
            if (msg is MessageListener.Message.Event.StartDownloadFile) {
               testClient.stop()
               "Client stopped".internalLog()
               runBlockWhileEnsuringPollingIsNotDetected(
                  expectedMessageChannel = expectedMessageChannel) {
                  "waiting for 10 seconds to ensure that download and polling is stopped".internalLog()
                  delay(10_000)
                  testJob?.cancel()
               }
            }
         }
      }
   }

   private suspend fun haraClientStopTestTemplate(
      downloadBehavior: DownloadBehavior = TestUtils.downloadBehavior,
      expectedMessageChannel: Channel<ExpectedMessage> =
         Channel(5, BufferOverflow.DROP_OLDEST),
      updater: Updater = TestUtils.updater,
      distributionId: Int = TestUtils.OS_DISTRIBUTION_ID,
      onMessageReceive: suspend (MessageListener.Message, HaraClient) -> Unit
   ) {

      reCreateTestTargetOnServer(TARGET_ID)
      assignHeavyOTAUpdateToTheTarget(distributionId)

      val client = createClient(
         expectedMessageChannel, downloadBehavior = downloadBehavior, updater = updater)

      client.startAsync()

      listenToMessages(expectedMessageChannel, client, onMessageReceive)
   }

   private suspend fun listenToMessages(
      expectedMessageChannel: Channel<ExpectedMessage>,
      client: HaraClient,
      onMessageReceive: suspend (MessageListener.Message, HaraClient) -> Unit) {
      for (msg in expectedMessageChannel) {
         if (msg is ExpectedMessage.HaraMessage) {
            onMessageReceive(msg.message, client)
         }
      }
   }

   private fun runTest(testBlock: suspend (Deferred<Unit>?) -> Unit) {
      runBlocking {
         try {
            var testJob: Deferred<Unit>? = null
            testJob = testScope.async(start = CoroutineStart.LAZY) {
               launch {
                  // Catching exceptions in haraClient scope by checking if it is still active
                  while (haraScope.isActive) {
                     delay(100)
                  }
                  throw RuntimeException(
                     "Test failed: HaraClient scope is closed before the test is finished")
               }
               testBlock(testJob)
            }
            testJob.await()
         } catch (ignored: CancellationException) {
         }
      }
   }

   private suspend fun runBlockWhileEnsuringPollingIsNotDetected(
      delay: Long = 1_000,
      expectedMessageChannel: Channel<ExpectedMessage>,
      block: suspend () -> Unit) {
      testScope.launch {
         block()
      }
      val job = testScope.async {
         delay(delay)
         for (msg in expectedMessageChannel) {
            if (msg is ExpectedMessage.HaraMessage && msg.message is MessageListener.Message.Event.Polling) {
               throw IllegalStateException(
                  "Test failed: Client is polling after being stopped")
            }
         }
      }
      try {
         job.await()
      } catch (ignored: CancellationException) {
      }
   }

   private fun createClient(
      expectedMessageChannel: Channel<ExpectedMessage>,
      downloadBehavior: DownloadBehavior = TestUtils.downloadBehavior,
      updater: Updater = TestUtils.updater): HaraClient {
      val messageListener = createMessageListener(expectedMessageChannel)
      haraScope = CoroutineScope(Dispatchers.Default)
      return clientFromTargetId(
         downloadBehavior = downloadBehavior, updater = updater,
         messageListeners = listOf(messageListener),
         scope = haraScope).invoke(TARGET_ID)
   }

   private suspend fun assignHeavyOTAUpdateToTheTarget(
      distributionId: Int) {
      val distribution = HawkbitAssignDistributionBody(
         distributionId, AssignDistributionType.FORCED, 0)
      assignDistributionToTheTarget(TARGET_ID, distribution)
   }

}
