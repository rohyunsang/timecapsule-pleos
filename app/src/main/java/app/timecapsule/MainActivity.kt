package app.timecapsule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.timecapsule.drive.DriveEvent
import app.timecapsule.drive.DriveState
import app.timecapsule.drive.DriveStateMachine
import app.timecapsule.tag.MoodTagEngine
import app.timecapsule.tag.Narration
import app.timecapsule.vehicle.FakeVehicleSource
import app.timecapsule.vehicle.VehicleSnapshot
import java.time.LocalDate

/**
 * Stage-1 dev screen: toggles a fake car state and shows the tags, narration and drive state
 * the real flow will produce. Replaced by screens 01-06 later.
 */
class MainActivity : ComponentActivity() {
    private val car = FakeVehicleSource(VehicleSnapshot(occupiedSeats = 1, outsideTempC = 18f, placeName = "7번 국도"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { DevScreen(car) }
            }
        }
    }
}

@Composable
private fun DevScreen(car: FakeVehicleSource) {
    val snap by car.snapshot.collectAsState()
    var state by remember { mutableStateOf<DriveState>(DriveState.Idle) }
    val tags = MoodTagEngine.tagsFor(snap)
    val narration = Narration.build(LocalDate.now().minusYears(18), LocalDate.now(), tags)

    Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("타임캡슐 프로토타입", fontSize = 32.sp)
        Text("주행 상태: $state", fontSize = 20.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(snap.wiperOn == true, { car.set { it.copy(wiperOn = it.wiperOn != true) } }, { Text("와이퍼") })
            FilterChip(snap.headlightOn == true, { car.set { it.copy(headlightOn = it.headlightOn != true) } }, { Text("전조등") })
            FilterChip(snap.anyWindowOpen == true, { car.set { it.copy(anyWindowOpen = it.anyWindowOpen != true) } }, { Text("창문") })
            FilterChip(snap.onHighway == true, { car.set { it.copy(onHighway = it.onHighway != true) } }, { Text("고속도로") })
            FilterChip(false, { car.set { it.copy(occupiedSeats = ((it.occupiedSeats ?: 1) % 4) + 1) } }, { Text("탑승 ${snap.occupiedSeats}명") })
        }

        Text("태그: " + tags.joinToString(" · ") { it.label }, fontSize = 22.sp)
        Text("내레이션: $narration", fontSize = 22.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton("묻어줘") { state = DriveStateMachine.reduce(state, DriveEvent.BuryRequested).state }
            BigButton("그만") { state = DriveStateMachine.reduce(state, DriveEvent.RecordingEnded).state }
            BigButton("1.2km 접근") { state = DriveStateMachine.reduce(state, DriveEvent.CapsuleWithinRange(1)).state }
            BigButton("들려줘") { state = DriveStateMachine.reduce(state, DriveEvent.PlayRequested).state }
            BigButton("방향지시등") { state = DriveStateMachine.reduce(state, DriveEvent.SafetySignalOn).state }
        }
    }
}

/** Spec 07: buttons used while driving are at least 72 dp tall. */
@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    Button(onClick, Modifier.heightIn(min = 72.dp)) { Text(label, fontSize = 20.sp) }
}
