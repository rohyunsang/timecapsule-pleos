package app.timecapsule.social

import app.timecapsule.data.Capsule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

/** Exercise the real send coordinator with controlled server responses, without real messages. */
class ForwardDeliveryTest {
    private val sender = Profile("sender", "전달자", "SEND27")
    private val recipient = Friend("recipient", "친구", "RECV27")
    private val source = Capsule(id = 1, createdAt = 123, lat = 37.0, lng = 127.0, placeName = "길",
        audioPath = "", durationSec = 3, tags = "", rawVehicleState = "{}", carModel = null, odometerKm = null,
        ownerUid = "author", ownerName = "원작자", remoteId = "original", collectedAt = 456)
    private var current: Capsule? = source
    private val me = MutableStateFlow<Profile?>(sender)
    private val friends = MutableStateFlow(listOf(recipient))
    private var permitted = true
    private var allowed = true
    private var unlocked = true
    private var audio: ByteArray? = byteArrayOf(1, 2, 3)
    private var afterAudio: () -> Unit = {}
    private var uploads = 0
    private var uploadFails = false
    private val backend = Proxy.newProxyInstance(SocialBackend::class.java.classLoader, arrayOf(SocialBackend::class.java)) { _, method, args ->
        when (method.name) {
            "getMe" -> me
            "getFriends" -> friends
            "canOpen" -> permitted
            "audio" -> { afterAudio(); audio }
            else -> if (method.name.startsWith("upload")) {
                uploads++
                val c = args[0] as RemoteCapsule
                assertEquals(listOf("recipient"), c.visibleTo)
                assertArrayEquals(audio, args[1] as ByteArray)
                val result = if (uploadFails) Result.failure<String>(IllegalStateException("offline")) else Result.success(c.id)
                // Kotlin suspend Result methods return the inline class's underlying value.
                result.javaClass.getDeclaredMethod("unbox-impl").invoke(result)
            } else error("Unexpected server call: ${method.name}")
        }
    } as SocialBackend
    private suspend fun send() = Forwarding.send(backend, 1, recipient.uid, { allowed }, { current }, { unlocked })

    @Test fun successfulDeliveryUploadsAudioOnlyToChosenFriend() = runTest {
        assertTrue(send().isSuccess)
        assertEquals(1, uploads)
        assertEquals(source, current)
    }
    @Test fun uncollectedAndLockedSourcesNeverUpload() = runTest {
        current = source.copy(collectedAt = null)
        assertTrue(send().isFailure)
        current = source; unlocked = false
        assertTrue(send().isFailure)
        assertEquals(0, uploads)
    }
    @Test fun revokedSourceAndMissingAudioNeverUpload() = runTest {
        permitted = false
        assertTrue(send().isFailure)
        permitted = true; audio = null
        assertTrue(send().isFailure)
        assertEquals(0, uploads)
    }
    @Test fun removedFriendOrAccountSwitchDuringDownloadNeverUploads() = runTest {
        afterAudio = { friends.value = emptyList() }
        assertTrue(send().isFailure)
        friends.value = listOf(recipient); afterAudio = { me.value = sender.copy(uid = "other") }
        assertTrue(send().isFailure)
        assertEquals(0, uploads)
    }
    @Test fun deletedSourceOrMovingCarDuringDownloadNeverUploads() = runTest {
        afterAudio = { current = null }
        assertTrue(send().isFailure)
        current = source; afterAudio = { allowed = false }
        assertTrue(send().isFailure)
        assertEquals(0, uploads)
    }
    @Test fun serverFailureNeverReportsSuccess() = runTest {
        uploadFails = true
        assertTrue(send().isFailure)
        assertEquals(1, uploads)
    }
}
