package app.timecapsule.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.timecapsule.companion.CapsuleMemory
import app.timecapsule.companion.ReplyPolicy
import app.timecapsule.social.RemoteCapsule
import java.time.*
import java.time.format.DateTimeFormatter

internal val LocalCapsuleTime = compositionLocalOf { System.currentTimeMillis() }

@Composable
internal fun MemoryCapsuleCard(c: RemoteCapsule, memory: CapsuleMemory, collected: Boolean, now: Long, open: () -> Unit) {
    val waiting = ReplyPolicy.waiting(c.replyMode, c.replyAvailableAt, now)
    val dateLocked = c.openAfter?.let { it > Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toEpochDay() } == true
    val playable = !waiting && !dateLocked && (collected || c.sourceRemoteId != null || ReplyPolicy.remoteOpen(c.replyMode, c.replyAvailableAt, now))
    val label = when {
        dateLocked -> "${LocalDate.ofEpochDay(c.openAfter!!)} 개봉"
        waiting -> c.replyAvailableAt?.let { "${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))}부터 듣기" } ?: "전송 시각 확인 중"
        playable -> "어디서든 듣기"
        else -> "장소에서 수집하기"
    }
    Card(onClick = open, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(color = if (playable) Color(0xFFFFECEE) else Color(0xFFF1F1F3), shape = RoundedCornerShape(14.dp)) {
                Icon(if (playable) Icons.Rounded.PlayArrow else Icons.Rounded.Lock, null, Modifier.padding(12.dp).size(28.dp), tint = if (playable) MaterialTheme.colorScheme.primary else Color(0xFF696970))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(memory.title.ifBlank { c.placeName ?: "이름 없는 장소" }, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${c.originalOwnerName ?: c.ownerName} · ${c.durationSec}초", style = MaterialTheme.typography.bodySmall, color = Color(0xFF696970))
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (playable) MaterialTheme.colorScheme.primary else Color(0xFF696970))
            }
            if (memory.favorite) Icon(Icons.Rounded.Favorite, "즐겨찾기", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Icon(Icons.Rounded.ChevronRight, "상세 보기", Modifier.size(20.dp), tint = Color(0xFF696970))
        }
    }
}
