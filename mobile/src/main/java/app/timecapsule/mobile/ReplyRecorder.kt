package app.timecapsule.mobile

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import app.timecapsule.companion.CollectionPolicy
import app.timecapsule.companion.ReplyPolicy
import app.timecapsule.social.*
import java.io.File
import org.json.JSONObject

data class ReplyDraft(val parentId: String, val authorUid: String, val recipientUid: String, val createdAt: Long,
                      val lat: Double, val lng: Double, val mode: String, val file: File, val durationSec: Int = 0)

/** Foreground-only recording. Finished drafts survive leaving the screen and process recreation. */
class ReplyRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var since = 0L
    private var current: ReplyDraft? = null
    private val directory = File(context.filesDir, "reply-drafts").apply { mkdirs() }
    private fun metadata(uid: String) = File(directory, "${java.util.UUID.nameUUIDFromBytes(uid.toByteArray())}.json")
    @Suppress("DEPRECATION")
    fun start(parent: RemoteCapsule, author: Profile, fix: CollectionPolicy.Fix, mode: String, onLimit: () -> Unit): ReplyDraft {
        check(recorder == null)
        require(mode in setOf(ReplyPolicy.LOCATION, ReplyPolicy.AFTER_THREE_DAYS))
        val at = System.currentTimeMillis()
        val draft = ReplyDraft(parent.id, author.uid, parent.ownerUid, at, fix.lat, fix.lng, mode, File(directory, "${java.util.UUID.nameUUIDFromBytes(author.uid.toByteArray())}-$at.m4a"))
        val media = MediaRecorder()
        try {
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioEncodingBitRate(64_000); media.setAudioSamplingRate(44_100)
            media.setOutputFile(draft.file.absolutePath); media.setMaxDuration(60_000)
            media.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit() }
            media.prepare(); media.start()
            since = SystemClock.elapsedRealtime(); recorder = media; current = draft
            return draft
        } catch (e: Exception) { media.release(); draft.file.delete(); throw e }
    }
    fun stop(): ReplyDraft? {
        val media = recorder ?: return current
        recorder = null
        val draft = current
        val duration = ((SystemClock.elapsedRealtime() - since) / 1000).toInt().coerceAtMost(60)
        val success = runCatching { media.stop() }.isSuccess
        media.release()
        if (!success || duration < 3 || draft == null) { draft?.file?.delete(); current = null; return null }
        val finished = draft.copy(durationSec = duration)
        val json = JSONObject().put("parentId", finished.parentId).put("authorUid", finished.authorUid)
            .put("recipientUid", finished.recipientUid).put("createdAt", finished.createdAt).put("lat", finished.lat)
            .put("lng", finished.lng).put("mode", finished.mode).put("file", finished.file.name).put("durationSec", duration)
        current = finished
        android.util.AtomicFile(metadata(finished.authorUid)).let { storage ->
            val output = storage.startWrite()
            try { output.write(json.toString().toByteArray()); storage.finishWrite(output) }
            catch (e: Exception) { storage.failWrite(output); throw e }
        }
        return finished
    }
    fun restore(uid: String): ReplyDraft? = runCatching {
        val json = JSONObject(metadata(uid).readText())
        check(json.getString("authorUid") == uid)
        val name = json.getString("file"); check('/' !in name && '\\' !in name)
        val file = File(directory, name); check(file.isFile)
        ReplyDraft(json.getString("parentId"), uid, json.getString("recipientUid"), json.getLong("createdAt"),
            json.getDouble("lat"), json.getDouble("lng"), json.getString("mode"), file, json.getInt("durationSec"))
    }.getOrNull()
    fun discard(draft: ReplyDraft) { draft.file.delete(); metadata(draft.authorUid).delete(); if (current == draft) current = null }
}
