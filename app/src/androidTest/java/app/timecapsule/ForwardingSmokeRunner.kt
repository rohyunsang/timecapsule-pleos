package app.timecapsule

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.room.Room
import app.timecapsule.capsule.CapsuleRepository
import app.timecapsule.capsule.OpenPolicy
import app.timecapsule.data.AppDatabase
import app.timecapsule.geo.GeoPoint
import app.timecapsule.map.CollectedMapData
import app.timecapsule.social.FakeBackend
import app.timecapsule.social.SocialSync
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate

/** Device smoke test with isolated preferences, files and Room; never sends to Firebase. */
class ForwardingSmokeRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            runBlocking { withTimeout(20_000) { verifyDelivery() } }
            result.putString("stream", "PASS: collected source -> selected friend -> synced audio -> location-free playback policy; retry, provenance and sender album verified\n")
            finish(-1, result)
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString()}\n")
            finish(0, result)
        }
    }

    private suspend fun verifyDelivery() {
        val prefix = "forward_test_${System.nanoTime()}_"
        val prefs = mutableSetOf<String>()
        val folder = File(targetContext.cacheDir, prefix).apply { mkdirs() }
        val isolated = object : ContextWrapper(targetContext) {
            override fun getFilesDir() = folder
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences(prefix + name, mode).also { prefs += prefix + name }
        }
        val db = Room.inMemoryDatabaseBuilder(targetContext, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val backend = FakeBackend(isolated)
            val sender = backend.signUp("전달 테스트").getOrThrow()
            val author = backend.addFriend("MNSU27").getOrThrow()
            val recipient = backend.addFriend("JYNG38").getOrThrow()
            val repo = CapsuleRepository(db)
            val sync = SocialSync(isolated, scope, backend, repo, Settings(isolated))
            sync.start()
            backend.friendLeavesCapsule(author, GeoPoint(37.0, 127.0), "테스트 장소")
            val original = repo.capsules.first { it.isNotEmpty() }.single()
            check(sync.forward(original.id, recipient.uid) { true }.isFailure)
            repo.collect(original.id, 1000)
            sync.forward(original.id, recipient.uid) { true }.getOrThrow()
            sync.forward(original.id, recipient.uid) { true }.getOrThrow()
            val delivered = backend.remote.value.single { it.sourceRemoteId != null }
            check(delivered.visibleTo == listOf(recipient.uid))
            check(delivered.originalOwnerUid == author.uid)
            delay(100)
            check(repo.all().size == 1) // no outgoing copy masquerading as my road capsule
            scope.coroutineContext.cancelChildren()
            scope.coroutineContext[Job]!!.children.toList().forEach { it.join() }
            repo.all().forEach { repo.delete(it) }
            backend.switchAccount(app.timecapsule.social.Profile(recipient.uid, recipient.name, recipient.code))
            val receiverSync = SocialSync(isolated, scope, backend, repo, Settings(isolated))
            receiverSync.start()
            val received = repo.capsules.first { it.any { c -> c.isForwarded } }.single()
            check(received.ownerUid == sender.uid && received.originalOwnerName == author.name)
            check(received.collectedAt == null && !CollectedMapData.eligible(received))
            check(backend.canOpen(received.remoteId!!))
            check(OpenPolicy.reason(received, null, LocalDate.now(), null, 0, 1000) == null)
            check(File(received.audioPath).readBytes().contentEquals(backend.audio(delivered.id)!!))
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]?.join()
            db.close()
            folder.deleteRecursively()
            prefs.forEach { targetContext.deleteSharedPreferences(it) }
        }
    }
}
