package app.timecapsule.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.timecapsule.data.Capsule
import app.timecapsule.drive.DriveController
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/** Vehicle components rendered on Layoutlib, with no vehicle service or emulator. */
class DriveNoticeRenderingTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1280, screenHeight = 720,
        orientation = com.android.resources.ScreenOrientation.LANDSCAPE, density = com.android.resources.Density.MEDIUM, locale = "ko"), theme = "android:Theme.Material.Light.NoActionBar")
    private val capsule = Capsule(createdAt = 1, lat = 37.5, lng = 127.0, placeName = "서울숲 가족들과 함께 걸었던 산책길",
        audioPath = "", durationSec = 24, tags = "", rawVehicleState = "{}", carModel = null, odometerKm = null)
    @Test fun savedAndTripReview() = render(600)
    @Test fun savedAndTripReviewCompact() = render(400)
    private fun render(width: Int) {
        paparazzi.snapshot {
            TimeCapsuleTheme(night = false) {
                Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) {
                    Column(Modifier.width(width.dp)) {
                        SavedCapsuleNotice(DriveController.SaveResult(capsule, null), {}, {})
                        SavedCapsuleNotice(DriveController.SaveResult(capsule.copy(draft = true), null), {}, {})
                        TripReviewNotice(DriveController.TripReview(3, 2, 1), {}, {})
                    }
                } }
            }
        }
    }
}
