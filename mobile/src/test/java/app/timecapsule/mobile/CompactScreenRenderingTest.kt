package app.timecapsule.mobile

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import app.timecapsule.companion.CompanionSnapshot
import app.timecapsule.companion.ReplyPolicy
import app.timecapsule.social.Friend
import org.junit.Rule
import org.junit.Test

/** Compact 320dp viewport with enlarged text; no Android emulator. */
class CompactScreenRenderingTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 640, screenHeight = 1280, density = com.android.resources.Density.XHIGH, fontScale = 1.5f, locale = "ko"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )
    private fun render(content: @Composable () -> Unit) = paparazzi.snapshot {
        TimeCapsuleMobileTheme { Surface(color = MaterialTheme.colorScheme.background, content = content) }
    }

    @Test fun replyOptions() = render {
        ReplyComposerContent(null, false, false, false, true, ReplyPolicy.AFTER_THREE_DAYS, {}, {}, {}, {}, {}, {})
    }
    @Test fun forwardToFriend() = render {
        CapsuleSharingContent(listOf(Friend("friend", "함께 여행하는 오랜 친구 지민", "ABC123")), setOf("friend"), true, false, {}, {}, {}, {})
    }
    @Test fun emptyLibrary() = render {
        MemoryLibraryContent(CompanionSnapshot(loading = false), emptyList(), "me", 1000) {}
    }
}
