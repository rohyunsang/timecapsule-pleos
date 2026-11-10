package app.timecapsule.mobile

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.timecapsule.companion.*
import app.timecapsule.social.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.LocalDate

class MobileModel(app: Application) : AndroidViewModel(app) {
    val configured = listOf(BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_APP_ID, BuildConfig.FIREBASE_PROJECT_ID).all { it.isNotBlank() }
    val backend: FirebaseBackend? = if (configured) runCatching {
        FirebaseBackend(app, viewModelScope, BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_APP_ID, BuildConfig.FIREBASE_PROJECT_ID)
    }.getOrNull() else null
    val me = backend?.me ?: MutableStateFlow<Profile?>(null)
    val friends = backend?.friends ?: MutableStateFlow(emptyList())
    val capsules = backend?.remote ?: MutableStateFlow(emptyList())
    val email = backend?.email ?: MutableStateFlow<String?>(null)
    val serverProblem = backend?.problem ?: MutableStateFlow<String?>(null)
    private val _records = MutableStateFlow(CompanionSnapshot())
    val records = _records.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _playing = MutableStateFlow<String?>(null)
    val playing = _playing.asStateFlow()
    private val _photo = MutableStateFlow<ByteArray?>(null)
    val photo = _photo.asStateFlow()
    private var photoJob: Job? = null
    private var photoTarget: String? = null
    private var media: MediaPlayer? = null
    private val audioManager = app.getSystemService(android.media.AudioManager::class.java)
    private var focus: android.media.AudioFocusRequest? = null
    private val location = PhoneLocation(app)
    private val store get() = backend?.companion ?: error("연결 설정이 필요해요")
    init {
        viewModelScope.launch {
            me.collectLatest { profile ->
                stop()
                photoJob?.cancel(); photoTarget = null; _photo.value = null
                _records.value = CompanionSnapshot()
                if (profile != null) store.observe(profile.uid).catch { _records.value = CompanionSnapshot(loading = false, error = "동기화하지 못했어요. 연결 설정을 확인해 주세요.") }.collect { _records.value = it }
            }
        }
    }
    fun dismissMessage() { _message.value = null }
    fun notify(message: String) { _message.value = message }
    private fun action(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { block() }
            catch (e: TimeoutCancellationException) { _message.value = "응답이 늦어지고 있어요. 연결 상태를 확인하고 다시 시도해 주세요." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "처리하지 못했어요. 다시 시도해 주세요." }
            finally { _busy.value = false }
        }
    }
    fun login(email: String, password: String) = action { backend!!.signInEmail(email, password).getOrThrow() }
    fun register(name: String, email: String, password: String) = action {
        require(name.isNotBlank() && email.contains('@') && password.length >= 6) { "이름, 이메일과 6자 이상의 비밀번호를 확인해 주세요." }
        backend!!.signUp(name.trim()).getOrThrow()
        backend.linkEmail(email, password).getOrThrow()
        _message.value = "계정을 만들었어요. 차량에서도 같은 이메일로 로그인해 주세요."
    }
    fun linkEmail(email: String, password: String) = action { backend!!.linkEmail(email, password).getOrThrow(); _message.value = "이메일을 연결했어요." }
    fun resetPassword(email: String) = action { backend!!.resetPassword(email).getOrThrow(); _message.value = "비밀번호 재설정 메일을 보냈어요." }
    fun logout() = action { stop(); backend!!.signOut(); _records.value = CompanionSnapshot() }
    fun addFriend(code: String) = action { backend!!.addFriend(code).getOrThrow(); _message.value = "친구를 추가했어요." }
    fun removeFriend(friend: Friend) = action { backend!!.removeFriend(friend) }
    fun memory(id: String) = records.value.memories.firstOrNull { it.capsuleId == id } ?: CapsuleMemory(id)
    fun saveMemory(memory: CapsuleMemory) = action { store.saveMemory(memory); _message.value = "추억을 저장했어요." }
    fun saveAlbum(album: MemoryAlbum) = action { store.saveAlbum(album); _message.value = "앨범을 저장했어요." }
    fun deleteAlbum(id: String) = action { store.deleteAlbum(id); _message.value = "앨범을 삭제했어요. 원본 캡슐은 그대로 남아 있어요." }
    fun sendToCar(c: RemoteCapsule) = action { store.sendDestination(c.id, c.placeName ?: "캡슐 장소", c.lat, c.lng); _message.value = "차량으로 보냈어요. 주차 중 차량에서 길 안내를 확인해 주세요." }
    fun loadPhoto(id: String) {
        photoJob?.cancel(); photoTarget = id; _photo.value = null
        val uid = me.value?.uid ?: return
        photoJob = viewModelScope.launch {
            try { val bytes = store.photo(id); if (me.value?.uid == uid && photoTarget == id) _photo.value = bytes }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Missing/offline photo never blocks the capsule itself. */ }
        }
    }
    fun savePhoto(id: String, uri: android.net.Uri) = action {
        val uid = me.value?.uid ?: error("먼저 로그인해 주세요.")
        val bytes = withContext(Dispatchers.IO) {
            val source = android.graphics.ImageDecoder.createSource(getApplication<Application>().contentResolver, uri)
            val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = minOf(1.0, 1280.0 / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
            }
            try {
                java.io.ByteArrayOutputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
                    out.toByteArray().also { check(it.size <= 800_000) { "사진 용량이 커요. 다른 사진을 선택해 주세요." } }
                }
            } finally { bitmap.recycle() }
        }
        check(me.value?.uid == uid) { "계정이 바뀌었어요." }
        store.savePhoto(id, bytes); if (photoTarget == id) _photo.value = bytes; _message.value = "사진을 저장했어요. 사진은 나에게만 보여요."
    }
    fun deletePhoto(id: String) = action { store.deletePhoto(id); if (photoTarget == id) _photo.value = null; _message.value = "사진을 삭제했어요." }
    fun isCollected(c: RemoteCapsule) = c.sourceRemoteId != null || records.value.collections.any { it.capsuleId == c.id }
    fun needsLocation(c: RemoteCapsule) = !isCollected(c)
    fun open(c: RemoteCapsule) = action {
        stop()
        val uid = me.value?.uid ?: error("먼저 로그인해 주세요.")
        val collected = isCollected(c)
        val fix = if (collected) null else location.current()
        check(backend!!.canOpen(c.id)) { "공유 권한을 확인하지 못했어요. 연결과 개봉일을 확인해 주세요." }
        val current = capsules.value.firstOrNull { it.id == c.id } ?: error("이 캡슐은 더 이상 공유되지 않아요.")
        CollectionPolicy.reason(current, me.value?.uid == uid, collected, LocalDate.now().toEpochDay(), fix, SystemClock.elapsedRealtime())?.let { error(CollectionPolicy.message(it)) }
        val bytes = backend.audio(c.id) ?: error("음성을 불러오지 못했어요. 다시 시도해 주세요.")
        check(me.value?.uid == uid) { "계정이 바뀌었어요." }
        if (!collected) {
            CollectionPolicy.reason(current, true, false, LocalDate.now().toEpochDay(), fix, SystemClock.elapsedRealtime())?.let { error(CollectionPolicy.message(it)) }
            store.collect(c.id, "mobile")
        }
        val file = withContext(Dispatchers.IO) {
            File(getApplication<Application>().cacheDir, "voices/$uid").apply { mkdirs() }.let { dir ->
                File(dir, "voice-${c.id.hashCode()}.audio").apply { writeBytes(bytes) }
            }
        }
        check(me.value?.uid == uid) { "계정이 바뀌었어요." }
        play(c.id, file)
    }
    private fun play(id: String, file: File) {
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                if (change < 0) { stop(); _message.value = "다른 소리로 재생을 멈췄어요. 다시 듣기를 눌러 주세요." }
            }.build()
        check(audioManager.requestAudioFocus(request) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "지금은 소리를 재생할 수 없어요. 잠시 후 다시 시도해 주세요." }
        focus = request
        val player = MediaPlayer()
        media = player
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { if (media === it) { it.start(); _playing.value = id } }
            player.setOnCompletionListener { stop() }
            player.setOnErrorListener { _, _, _ -> stop(); _message.value = "재생하지 못했어요. 다시 불러와 주세요."; true }
            player.prepareAsync()
        } catch (e: Exception) { stop(); throw e }
    }
    fun stop() {
        media?.release(); media = null; _playing.value = null
        focus?.let { audioManager.abandonAudioFocusRequest(it) }; focus = null
    }
    override fun onCleared() { stop(); super.onCleared() }
}
