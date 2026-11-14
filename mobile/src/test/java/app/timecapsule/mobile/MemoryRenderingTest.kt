package app.timecapsule.mobile

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.timecapsule.companion.*
import app.timecapsule.social.*
import org.junit.Rule
import org.junit.Test

/** Layoutlib renders the production component on the JVM, without a device or emulator. */
class MemoryRenderingTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(locale = "ko"), theme = "android:Theme.Material.Light.NoActionBar")
    private val capsule = RemoteCapsule("preview", "friend", "민수", 1, 37.5, 127.0, 300, "한강에서 함께 본 노을", 24, "", "{}", null, Audience.SOME, emptyList(), emptyList())
    @Test fun collectionAndReplyStates() {
        paparazzi.snapshot {
            TimeCapsuleMobileTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F6)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("차곡차곡 쌓인 목소리", style = MaterialTheme.typography.headlineSmall)
                    MemoryCapsuleCard(capsule, CapsuleMemory("preview", favorite = true), true, 1000) {}
                    MemoryCapsuleCard(capsule.copy(placeName = "다음 여행에서 열어 볼 캡슐"), CapsuleMemory("preview"), false, 1000) {}
                    MemoryCapsuleCard(capsule.copy(replyToId = "parent", replyMode = ReplyPolicy.AFTER_THREE_DAYS, replyAvailableAt = 259201000), CapsuleMemory("preview", title = "민수가 남긴 음성 답장"), false, 1000) {}
                    MemoryCapsuleCard(capsule.copy(replyToId = "parent", replyMode = ReplyPolicy.AFTER_THREE_DAYS, replyAvailableAt = 1000), CapsuleMemory("preview", title = "3일이 지나 열린 답장"), false, 1000) {}
                }
            }
        }
    }
    @Test fun mapMarkerStates() {
        paparazzi.snapshot {
            TimeCapsuleMobileTheme {
                Column(Modifier.fillMaxSize().background(Color.White).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("지도의 캡슐 상태", style = MaterialTheme.typography.titleLarge)
                    CapsuleAvailability.State.entries.forEach { state ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(capsuleMarker(state, 2f).asImageBitmap(), null, Modifier.size(44.dp))
                            Text(CapsuleAvailability.label(state))
                        }
                    }
                }
            }
        }
    }
    @Test fun loginScreen() {
        paparazzi.snapshot {
            TimeCapsuleMobileTheme {
                Surface(color = MaterialTheme.colorScheme.background) { LoginContent(false, { _, _ -> }, { _, _, _ -> }, {}) }
            }
        }
    }
    @Test fun emptyLibrary() {
        paparazzi.snapshot {
            TimeCapsuleMobileTheme {
                Surface(color = MaterialTheme.colorScheme.background) { MemoryLibraryContent(CompanionSnapshot(loading = false), emptyList(), "me", 1000) {} }
            }
        }
    }
    @Test fun libraryWithCollectedAndLockedCapsules() {
        paparazzi.snapshot {
            TimeCapsuleMobileTheme {
                Surface(color = Color(0xFFF5F5F6)) {
                    MemoryLibraryContent(CompanionSnapshot(collections = listOf(CollectionReceipt("preview", 1, "mobile")), loading = false),
                        listOf(capsule, capsule.copy(id = "locked", placeName = "다음 여행에서 만날 목소리")), "me", 1000) {}
                }
            }
        }
    }
    @Test fun collectedDetail() = renderDetail(capsule, true)
    @Test fun waitingReplyDetail() = renderDetail(capsule.copy(replyToId = "parent", replyMode = ReplyPolicy.AFTER_THREE_DAYS, replyAvailableAt = 259201000), false)
    @Test fun detailWithLargeText() = renderDetail(capsule.copy(placeName = "오랜 친구들과 함께 걸었던 서울숲의 저녁 산책길"), true, 1.5f)
    private fun renderDetail(c: RemoteCapsule, collected: Boolean, fontScale: Float = 1f) {
        paparazzi.snapshot {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale)) {
                TimeCapsuleMobileTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        CapsuleDetailContent(c, CapsuleMemory(c.id), "me", collected, null, null, false, false, 1000,
                            CapsuleDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}))
                    }
                }
            }
        }
    }
    @Test fun sharedAlbumWithUnavailableCapsule() {
        paparazzi.snapshot {
            TimeCapsuleMobileTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AlbumsContent(listOf(MemoryAlbum("album", "친구들과 함께한 가을 여행", listOf("preview", "private"), listOf("friend", "me"), "friend")),
                        listOf(capsule), "me", listOf(Friend("friend", "민수", "ABC123")), false, {}, { _, _ -> }, { _, _ -> })
                }
            }
        }
    }
    @Test fun emptyAlbums() {
        paparazzi.snapshot { TimeCapsuleMobileTheme { Surface(color = MaterialTheme.colorScheme.background) { AlbumsContent(emptyList(), emptyList(), "me", emptyList(), false, {}, { _, _ -> }, { _, _ -> }) } } }
    }
    @Test fun linkedAccountAndFriends() {
        paparazzi.snapshot {
            TimeCapsuleMobileTheme {
                Surface(color = MaterialTheme.colorScheme.background) { AccountContent(Profile("me", "지민", "XYZ123"), listOf(Friend("friend", "민수", "ABC123")), "example@example.com", false, { _, _ -> }, {}, { _, _ -> }, {}) }
            }
        }
    }
    @Test fun forwardingWithoutFriends() = renderSharing(emptyList(), emptySet(), true)
    @Test fun sharingWithSelectedFriends() = renderSharing(listOf(Friend("friend", "민수", "ABC123"), Friend("second", "함께 여행하는 오랜 친구 지민", "DEF456")), setOf("friend"), false)
    private fun renderSharing(friends: List<Friend>, selected: Set<String>, forwarding: Boolean) {
        paparazzi.snapshot {
            TimeCapsuleMobileTheme {
                CapsuleSharingContent(friends, selected, forwarding, false, {}, {}, {}, {})
            }
        }
    }

    @Test fun replyOpeningOptions() = renderReply(null)
    @Test fun savedReplyBeforeSending() = renderReply(ReplyDraft("parent", "me", "friend", 1, 37.5, 127.0, ReplyPolicy.AFTER_THREE_DAYS, java.io.File("preview-only.m4a"), 24))
    private fun renderReply(draft: ReplyDraft?) {
        paparazzi.snapshot {
            TimeCapsuleMobileTheme {
                ReplyComposerContent(draft, false, false, false, true, ReplyPolicy.AFTER_THREE_DAYS, {}, {}, {}, {}, {}, {})
            }
        }
    }

    @Test fun replyToForwardingFriend() {
        paparazzi.snapshot {
            TimeCapsuleMobileTheme {
                ReplyComposerContent(null, false, false, false, true, ReplyPolicy.LOCATION, {}, {}, {}, {}, {}, {},
                    recipientLabel = "지민님", forwardedReply = true)
            }
        }
    }

    @Test fun albumEditorWithUnavailableCapsule() {
        paparazzi.snapshot { TimeCapsuleMobileTheme { Surface(color = MaterialTheme.colorScheme.background) {
            androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                albumEditorItems("친구들과 함께한 여행", setOf("preview", "missing"), setOf("friend"), listOf(capsule),
                    listOf(Friend("friend", "민수", "ABC123")), false, {}, {}, {}, {})
            }
        } } }
    }
    @Test fun memoryEditorWithPhoto() {
        val bitmap = android.graphics.Bitmap.createBitmap(400, 220, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply {
            drawColor(android.graphics.Color.rgb(213, 229, 236))
            drawRect(0f, 140f, 400f, 220f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(114, 156, 133) })
            drawCircle(320f, 55f, 25f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(245, 205, 118) })
        }
        paparazzi.snapshot { TimeCapsuleMobileTheme { Surface(color = MaterialTheme.colorScheme.background) {
            androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                memoryEditorItems(CapsuleMemory("preview", favorite = true), bitmap.asImageBitmap(), "함께 걷던 오후", "친구들과 다시 만나기로 한 장소", false,
                    CapsuleDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}), {}, {})
            }
        } } }
    }

}
