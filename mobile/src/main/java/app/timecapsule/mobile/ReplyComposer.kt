package app.timecapsule.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.timecapsule.companion.ReplyPolicy
import app.timecapsule.social.RemoteCapsule

@Composable
internal fun ReplyComposer(model: MobileModel, parent: RemoteCapsule?, dismiss: () -> Unit) {
    val draft by model.reply.collectAsState()
    val recording by model.recordingReply.collectAsState()
    val busy by model.busy.collectAsState()
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(ReplyPolicy.LOCATION) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.RECORD_AUDIO] == true && it[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            parent?.let { capsule -> model.startReply(capsule, mode) }
        } else denied = true
    }
    fun close() { if (!busy) { model.finishReply(); dismiss() } }
    AlertDialog(onDismissRequest = ::close, title = { Text(if (recording) "음성 답장을 녹음하고 있어요" else "목소리로 답장하기") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("답장은 원본 캡슐을 남긴 사람에게 보내요. 녹음한 현재 위치가 답장의 장소가 됩니다.")
            if (draft == null) {
                FilterChip(mode == ReplyPolicy.LOCATION, { mode = ReplyPolicy.LOCATION }, enabled = !busy, label = { Text("장소에서 수집한 뒤 듣기") })
                FilterChip(mode == ReplyPolicy.AFTER_THREE_DAYS, { mode = ReplyPolicy.AFTER_THREE_DAYS }, enabled = !busy, label = { Text("3일 후 어디서든 듣기") })
                Text(if (mode == ReplyPolicy.LOCATION) "받는 사람이 이 장소에 방문해 수집하면 들을 수 있어요." else "전송이 완료된 시점부터 72시간이 지나면 장소에 방문하지 않아도 들을 수 있어요.")
            } else {
                Text(if (draft?.mode == ReplyPolicy.AFTER_THREE_DAYS) "전송 완료 3일 후 · 방문 없이 듣기" else "이 장소에서 수집 후 듣기")
                Text(if (recording) "최대 1분 · 화면을 떠나면 녹음을 마치고 임시 보관해요." else "${draft?.durationSec}초의 답장을 임시 보관했어요. 전송에 실패해도 다시 보낼 수 있어요.")
            }
            if (draft == null || recording) Button(onClick = {
                if (recording) model.finishReply()
                else if (parent != null) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && PhoneLocation(context).permitted()) model.startReply(parent, mode)
                    else permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }, enabled = !busy && (recording || parent != null), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, null)
                Spacer(Modifier.width(8.dp)); Text(if (recording) "녹음 마치기" else "녹음 시작")
            }
            if (draft != null && !recording) TextButton(onClick = { deleting = true }, enabled = !busy) { Text("임시 답장 삭제") }
            if (parent == null && draft == null) Text("원본 캡슐을 다시 열어 주세요.")
        } },
        confirmButton = { if (draft != null && !recording) TextButton(onClick = { sending = true }, enabled = !busy) { Text("답장 보내기") } },
        dismissButton = { TextButton(onClick = ::close, enabled = !busy) { Text(if (draft != null) "보관하고 닫기" else "취소") } })
    if (sending) AlertDialog(onDismissRequest = { sending = false }, title = { Text("답장을 보낼까요?") }, text = { Text("녹음한 음성과 남긴 위치를 원본 작성자에게 보냅니다.") }, confirmButton = { TextButton(onClick = { sending = false; model.sendReply() }) { Text("보내기") } }, dismissButton = { TextButton(onClick = { sending = false }) { Text("취소") } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("임시 답장을 삭제할까요?") }, text = { Text("삭제한 녹음은 되돌릴 수 없어요.") }, confirmButton = { TextButton(onClick = { deleting = false; model.discardReply(); dismiss() }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("취소") } })
    if (denied) AlertDialog(onDismissRequest = { denied = false }, title = { Text("마이크와 정확한 위치가 필요해요") }, text = { Text("답장을 녹음하고 남길 장소를 확인할 때만 사용해요. 설정에서 권한을 허용한 뒤 다시 녹음해 주세요.") }, confirmButton = { TextButton(onClick = { denied = false; runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }.onFailure { model.notify("설정에서 타임캡슐의 마이크와 위치 권한을 허용해 주세요.") } }) { Text("설정으로 이동") } }, dismissButton = { TextButton(onClick = { denied = false }) { Text("취소") } })
}
