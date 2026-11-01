package app.timecapsule.social

import kotlinx.coroutines.flow.StateFlow

/**
 * Leaving a capsule for other people (기획서: 편지 캡슐 받는 사람, 가족 계정/공유).
 *
 * A capsule is left for:
 *  - SELF: stays in this car only, never uploaded.
 *  - FRIENDS: every friend. When a friend passes the spot, their car plays it.
 *  - SOME: chosen friends (a letter capsule to one person).
 *
 * Friends connect with a 6-character friend code, typed in or opened from a QR code. Both
 * sides become friends at once: knowing the code is the consent, as with a phone number.
 */
enum class Audience { SELF, FRIENDS, SOME }

data class Profile(val uid: String, val name: String, val code: String)

data class Friend(val uid: String, val name: String, val code: String)

/** A capsule as stored on the server. Audio is fetched separately ([SocialBackend.audio]). */
data class RemoteCapsule(
    val id: String = "",
    val ownerUid: String,
    val ownerName: String,
    val createdAt: Long,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val placeName: String?,
    val durationSec: Int,
    val tags: String,
    val rawVehicleState: String,
    /** Letter capsule: first openable day (LocalDate.toEpochDay), null for a normal capsule. */
    val openAfter: Long?,
    val audience: Audience,
    val recipients: List<String>,
    /** Uids allowed to receive it: all friends (FRIENDS) or the recipients (SOME). */
    val visibleTo: List<String>,
)

interface SocialBackend {
    /** Shown in the dev panel: "Firebase" or "가짜 서버". */
    val label: String

    /** Signed-in profile on this car, null before the user picks a name. */
    val me: StateFlow<Profile?>
    val friends: StateFlow<List<Friend>>

    /** Server capsules that are mine or that friends left for me. */
    val remote: StateFlow<List<RemoteCapsule>>

    /** Last error worth showing (offline, bad config), null when fine. */
    val problem: StateFlow<String?>

    /**
     * True once [friends] and [remote] both came from the server for the current account.
     * Until then a missing capsule may just not be loaded yet, so nothing is removed.
     */
    val synced: StateFlow<Boolean>

    /** Email linked to this account, so it can be loaded again on another car or reinstall. */
    val email: StateFlow<String?>
    suspend fun linkEmail(email: String, password: String): Result<Unit>
    /** Loads an existing account (name, code, friends, my capsules) onto this car. */
    suspend fun signInEmail(email: String, password: String): Result<Profile>
    /** Sends a link to set a new password. */
    suspend fun resetPassword(email: String): Result<Unit>

    suspend fun signUp(name: String): Result<Profile>
    suspend fun addFriend(code: String): Result<Friend>
    suspend fun removeFriend(friend: Friend)

    /** Uploads a capsule and its audio; returns the server id. */
    suspend fun upload(capsule: RemoteCapsule, audio: ByteArray): Result<String>
    suspend fun setVisibleTo(id: String, uids: List<String>)
    suspend fun delete(id: String)
    suspend fun audio(id: String): ByteArray?

    /** Dev panel: switch this car to another account used here before, or a new person (null). */
    suspend fun switchAccount(profile: Profile?)
    /** Handing the car over: forget who was signed in. */
    suspend fun signOut()
    /** Dev panel: accounts used on this car before. */
    val knownAccounts: StateFlow<List<Profile>>
}

object FriendCode {
    /** No 0/O, 1/I: codes are read aloud and typed in a car. */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6

    fun random(rnd: java.util.Random = java.security.SecureRandom()): String =
        (1..LENGTH).map { ALPHABET[rnd.nextInt(ALPHABET.length)] }.joinToString("")

    /** Uppercases and drops spaces and dashes; null when it cannot be a code. */
    fun normalize(input: String): String? {
        val c = input.uppercase().filter { it.isLetterOrDigit() }
        return c.takeIf { it.length == LENGTH && it.all { ch -> ch in ALPHABET } }
    }

    /** What the QR code encodes. Opening it adds the friend (deep link into the app). */
    fun link(code: String) = "timecapsule://friend?code=$code"

    fun fromLink(uri: String): String? =
        Regex("""[?&]code=([A-Za-z0-9]+)""").find(uri)?.groupValues?.get(1)?.let(::normalize)
}
