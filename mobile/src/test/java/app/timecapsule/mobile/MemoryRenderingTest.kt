package app.timecapsule.mobile

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFD92D3A))) {
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
}
