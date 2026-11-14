package app.timecapsule.mobile

import app.cash.paparazzi.Paparazzi
import app.timecapsule.companion.ReplyPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Android AtomicFile/JSON run on Layoutlib; no recorder, device or emulator is started. */
class ReplyDraftStorageTest {
    @get:Rule val paparazzi = Paparazzi()
    @get:Rule val temporary = TemporaryFolder()
    private fun draft(uid: String = "alice") = ReplyDraft("parent", uid, "bob", 1000, 37.5, 127.0,
        ReplyPolicy.AFTER_THREE_DAYS, temporary.newFile("$uid.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }, 10)
    @Test fun completedDraftSurvivesNewStorageInstanceAndRetainsRecipientAndMode() {
        val draft = draft()
        ReplyDraftStorage(temporary.root).save(draft)
        assertEquals(draft, ReplyDraftStorage(temporary.root).restore("alice"))
        assertNull(ReplyDraftStorage(temporary.root).restore("bob"))
    }
    @Test fun discardDoesNotRemoveAnotherAccountsDraft() {
        val alice = draft(); val bob = draft("bob")
        val store = ReplyDraftStorage(temporary.root)
        store.save(alice); store.save(bob); store.discard(alice)
        assertNull(store.restore("alice")); assertFalse(alice.file.exists())
        assertEquals(bob, store.restore("bob")); assertTrue(bob.file.exists())
    }
    @Test fun missingAudioIsNotShownAsSendableDraft() {
        val draft = draft(); val store = ReplyDraftStorage(temporary.root)
        store.save(draft); draft.file.delete()
        assertNull(store.restore("alice"))
    }
}
